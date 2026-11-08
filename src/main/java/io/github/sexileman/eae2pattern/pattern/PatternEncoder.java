package io.github.sexileman.eae2pattern.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link PatternSpec} into an encoded AE2 pattern.
 *
 * <p>This is layer L1 of the design in {@code 3.基于样板编码终端.md}: we call AE2's own
 * {@link PatternDetailsHelper} instead of re-implementing pattern encoding. That keeps us binary compatible
 * with AE2's pattern format, including any future NBT the encoder decides to write, and it means we never
 * have to touch AE2 internals to stay working across 15.2.x patch releases.</p>
 *
 * <p>Pure function: no storage access, no blank pattern consumption. The caller is responsible for the
 * transaction, which is what lets the preview GUI show exactly what a run would produce before anything is
 * spent.</p>
 */
public final class PatternEncoder {

    private PatternEncoder() {
    }

    public static ItemStack encode(net.minecraft.server.level.ServerPlayer player, PatternSpec spec)
            throws EncodingException {
        ItemStack encoded = encode(player.serverLevel(), spec);
        PatternMetadata.apply(player, spec, encoded);
        return encoded;
    }

    public static final class EncodingException extends Exception {
        public EncodingException(String message) {
            super(message);
        }
    }

    /**
     * @param level the server level, used to resolve {@code recipeId} back to a live recipe instance
     * @param spec  the flattened description produced by the client
     * @return the encoded pattern, never empty
     * @throws EncodingException when the recipe is missing, of the wrong type, or AE2 refuses to encode it
     */
    public static ItemStack encode(Level level, PatternSpec spec) throws EncodingException {
        if(io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.isResetRecipe(spec.recipeId().toString()))
            throw new EncodingException("Wildcard reset is a manual crafting operation, not a batch pattern");
        List<StackSpec> inputs = IngredientFilter.filterInputs(spec.inputs());
        if (inputs.isEmpty() && spec.kind() != PatternKind.CRAFTING) {
            throw new EncodingException("pattern has no ingredients after filtering: " + spec.recipeId());
        }
        if (spec.outputs().isEmpty()) {
            throw new EncodingException("pattern has no output: " + spec.recipeId());
        }

        boolean substitutes = io.github.sexileman.eae2pattern.config.EAE2PatternConfig.ALLOW_SUBSTITUTES.get();
        boolean fluidSubstitutes = io.github.sexileman.eae2pattern.config.EAE2PatternConfig.ALLOW_FLUID_SUBSTITUTES.get();

        ItemStack encoded = switch (spec.kind()) {
            case CRAFTING -> encodeCrafting(level, spec, inputs, substitutes, fluidSubstitutes);
            case PROCESSING -> encodeProcessing(spec, inputs);
            case STONECUTTING -> encodeStonecutting(level, spec, inputs, substitutes);
            case SMITHING -> encodeSmithing(level, spec, inputs, substitutes);
        };

        if (encoded == null || encoded.isEmpty()) {
            throw new EncodingException("AE2 returned an empty pattern for " + spec.kind() + " " + spec.recipeId());
        }
        if (!PatternDetailsHelper.isEncodedPattern(encoded)) {
            throw new EncodingException("AE2 returned a stack that is not an encoded pattern: " + spec.recipeId());
        }
        return encoded;
    }

    /**
     * Builds the grid from the recipe itself rather than from the client's flat ingredient list.
     *
     * <p>JEI's input slots carry no position, so a shaped recipe such as "ABA" / " C " cannot be rebuilt by filling
     * slot 0, 1, 2 in order. The recipe knows its own shape, so we ask it. The client's lists are still used for the
     * preview, but they never decide what gets encoded.</p>
     */
    private static ItemStack encodeCrafting(Level level, PatternSpec spec, List<StackSpec> inputs,
                                            boolean substitutes, boolean fluidSubstitutes)
            throws EncodingException {
        Recipe<?> recipe = lookupRecipe(level, spec.recipeId());
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            throw new EncodingException("recipe " + spec.recipeId() + " is not a crafting recipe but was requested as one");
        }
        ItemStack[] grid = gridFromRecipe(craftingRecipe, inputs, spec.recipeId());
        ItemStack output = resultOf(recipe, level);
        if (output.isEmpty()) {
            output = requireItemOutput(spec);
        }
        return PatternDetailsHelper.encodeCraftingPattern(craftingRecipe, grid, output, substitutes, fluidSubstitutes);
    }

    private static ItemStack[] gridFromRecipe(CraftingRecipe recipe, List<StackSpec> fallback, ResourceLocation recipeId)
            throws EncodingException {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            // Modded crafting recipes are allowed to hide their ingredients. Fall back to the client's list, which at
            // least matched what JEI drew for the recipe.
            return gridFromSpecs(fallback, recipeId);
        }
        if (ingredients.size() > 9) {
            throw new EncodingException("crafting recipe has " + ingredients.size() + " slots, a grid holds 9: " + recipeId);
        }
        ItemStack[] grid = new ItemStack[9];
        int index = 0;
        int recipeWidth = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (Ingredient ingredient : ingredients) {
            int slot = recipe instanceof ShapedRecipe ? CraftingGrid.slot(index, recipeWidth) : index;
            index++;
            if (ingredient.isEmpty()) {
                // An empty slot in a shaped pattern still occupies its position, so the index advances.
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) {
                continue;
            }
            ItemStack stack = options[0].copy();
            stack.setCount(1);
            if (slot < 9) {
                grid[slot] = stack;
            }
        }
        return grid;
    }

    private static ItemStack[] gridFromSpecs(List<StackSpec> inputs, ResourceLocation recipeId) throws EncodingException {
        if (inputs.size() > 9) {
            throw new EncodingException("crafting pattern has " + inputs.size() + " ingredients, but a grid holds 9: "
                    + recipeId);
        }
        ItemStack[] grid = new ItemStack[9];
        int slot = 0;
        for (StackSpec spec : inputs) {
            if (slot >= 9) {
                break;
            }
            if (!spec.isItem()) {
                // A fluid cannot live in a crafting grid. AE2 models that through allow_fluid_substitutes instead.
                continue;
            }
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                stack.setCount(1);
                grid[slot++] = stack;
            }
        }
        return grid;
    }


    private static ItemStack encodeProcessing(PatternSpec spec, List<StackSpec> inputs) throws EncodingException {
        GenericStack[] in = toGenericStacks(inputs, true);
        GenericStack[] out = toGenericStacks(spec.outputs(), false);
        if (out.length == 0) {
            throw new EncodingException("processing pattern has no output: " + spec.recipeId());
        }
        return PatternDetailsHelper.encodeProcessingPattern(in, out);
    }

    private static ItemStack encodeStonecutting(Level level, PatternSpec spec, List<StackSpec> inputs,
                                                boolean substitutes) throws EncodingException {
        Recipe<?> recipe = lookupRecipe(level, spec.recipeId());
        if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) {
            throw new EncodingException("recipe " + spec.recipeId() + " is not a stonecutting recipe");
        }
        AEItemKey input = stonecutterRecipe.getIngredients().isEmpty()
                ? requireItemKey(inputs.get(0), "stonecutting input")
                : firstItemKey(stonecutterRecipe.getIngredients().get(0), "stonecutting input");
        AEItemKey output = keyOfResult(recipe, level, spec, "stonecutting output");
        return PatternDetailsHelper.encodeStonecuttingPattern(stonecutterRecipe, input, output, substitutes);
    }

    private static ItemStack encodeSmithing(Level level, PatternSpec spec, List<StackSpec> inputs,
                                            boolean substitutes) throws EncodingException {
        Recipe<?> recipe = lookupRecipe(level, spec.recipeId());
        if (!(recipe instanceof SmithingRecipe smithingRecipe)) {
            throw new EncodingException("recipe " + spec.recipeId() + " is not a smithing recipe");
        }
        if (inputs.size() < 3) {
            throw new EncodingException("smithing pattern needs template, base and addition, got " + inputs.size()
                    + " inputs for " + spec.recipeId());
        }
        // Vanilla orders smithing ingredients as template, base, addition; JEI slot order is not guaranteed to match.
        List<Ingredient> ingredients = smithingRecipe.getIngredients();
        AEItemKey template;
        AEItemKey base;
        AEItemKey addition;
        if (ingredients.size() >= 3) {
            template = firstItemKey(ingredients.get(0), "smithing template");
            base = firstItemKey(ingredients.get(1), "smithing base");
            addition = firstItemKey(ingredients.get(2), "smithing addition");
        } else {
            template = requireItemKey(inputs.get(0), "smithing template");
            base = requireItemKey(inputs.get(1), "smithing base");
            addition = requireItemKey(inputs.get(2), "smithing addition");
        }
        AEItemKey output = keyOfResult(recipe, level, spec, "smithing output");
        return PatternDetailsHelper.encodeSmithingTablePattern(smithingRecipe, template, base, addition, output, substitutes);
    }


    private static Recipe<?> lookupRecipe(Level level, ResourceLocation recipeId) throws EncodingException {
        if (level == null || level.getRecipeManager() == null) {
            throw new EncodingException("no recipe manager available on this side");
        }
        return level.getRecipeManager().byKey(recipeId)
                .orElseThrow(() -> new EncodingException("recipe " + recipeId + " is not present on the server"));
    }

    private static ItemStack requireItemOutput(PatternSpec spec) throws EncodingException {
        StackSpec output = spec.outputs().get(0);
        if (!output.isItem()) {
            throw new EncodingException("crafting output must be an item, got " + output.type() + " for " + spec.recipeId());
        }
        ItemStack stack = output.toItemStack();
        if (stack.isEmpty()) {
            throw new EncodingException("unknown output item " + output.id() + " for " + spec.recipeId());
        }
        return stack;
    }

    private static AEItemKey requireItemKey(StackSpec spec, String role) throws EncodingException {
        if (!spec.isItem()) {
            throw new EncodingException(role + " must be an item, got " + spec.type());
        }
        // Build the key from the full stack, not from the bare item: a GregTech circuit is an item plus NBT,
        // and AEItemKey.of(Item) would produce a key that matches every circuit configuration.
        ItemStack stack = spec.toItemStack();
        if (stack.isEmpty()) {
            throw new EncodingException(role + " references unknown item " + spec.id());
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new EncodingException(role + " cannot be represented as an AE2 item key: " + spec.id());
        }
        return key;
    }

    /** The recipe's own result, which is authoritative; the client's output list is only a fallback. */
    private static ItemStack resultOf(Recipe<?> recipe, Level level) {
        try {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            return result == null ? ItemStack.EMPTY : result;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static AEItemKey keyOfResult(Recipe<?> recipe, Level level, PatternSpec spec, String role)
            throws EncodingException {
        ItemStack result = resultOf(recipe, level);
        if (!result.isEmpty()) {
            AEItemKey key = AEItemKey.of(result);
            if (key != null) {
                return key;
            }
        }
        return requireItemKey(spec.outputs().get(0), role);
    }

    private static AEItemKey firstItemKey(Ingredient ingredient, String role) throws EncodingException {
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) {
            throw new EncodingException(role + " ingredient has no concrete item");
        }
        AEItemKey key = AEItemKey.of(options[0]);
        if (key == null) {
            throw new EncodingException(role + " cannot be represented as an AE2 item key: " + options[0]);
        }
        return key;
    }

    private static GenericStack[] toGenericStacks(List<StackSpec> specs, boolean requireNonEmpty) throws EncodingException {
        List<GenericStack> stacks;
        try { stacks = CompleteIngredients.map(specs, PatternEncoder::toGenericStack,s->s.type()+":"+s.id()); }
        catch(IllegalArgumentException invalid) { throw new EncodingException(invalid.getMessage()); }
        if (requireNonEmpty && stacks.isEmpty()) {
            throw new EncodingException("no encodable ingredients left after filtering");
        }
        return stacks.toArray(new GenericStack[0]);
    }

    private static GenericStack toGenericStack(StackSpec spec) {
        AEKey key = toKey(spec);
        return key == null ? null : new GenericStack(key, spec.amount());
    }

    private static AEKey toKey(StackSpec spec) {
        if (spec.isItem()) {
            ItemStack stack = spec.toItemStack();
            return stack.isEmpty() ? null : AEItemKey.of(stack);
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
        return fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY ? null : AEFluidKey.of(new net.minecraftforge.fluids.FluidStack(fluid, 1, spec.tag()));
    }
}
