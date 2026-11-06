package io.github.sexileman.eae2pattern.pattern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One "please encode this" unit. The client derives it from a JEI recipe, the server turns it into an
 * encoded pattern.
 *
 * <p>{@code recipeId} is carried so the server can look up the real {@code Recipe} object: AE2's stonecutting
 * and smithing encoders take the recipe instance itself, and the crafting encoder takes a
 * {@code CraftingRecipe}. The ingredient lists are carried as well because JEI is the only component that
 * knows how an arbitrary mod machine interprets its recipe, and the dedicated server may not have JEI at all.</p>
 *
 * <p>For {@link PatternKind#CRAFTING} the server only trusts {@code recipeId}: it rebuilds the 3x3 grid from
 * the recipe, because a crafting pattern whose grid does not match the recipe is a silent, unrecoverable
 * crafting loop.</p>
 */
public record PatternSpec(
        PatternKind kind,
        ResourceLocation recipeId,
        List<StackSpec> inputs,
        List<StackSpec> outputs
) {

    public static final int MAX_INPUTS = 81;
    public static final int MAX_OUTPUTS = 27;

    public PatternSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(recipeId, "recipeId");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public static PatternSpec processing(ResourceLocation recipeId, List<StackSpec> inputs, List<StackSpec> outputs) {
        return new PatternSpec(PatternKind.PROCESSING, recipeId, inputs, outputs);
    }

    public static PatternSpec crafting(ResourceLocation recipeId, List<StackSpec> inputs, List<StackSpec> outputs) {
        return new PatternSpec(PatternKind.CRAFTING, recipeId, inputs, outputs);
    }

    public static PatternSpec stonecutting(ResourceLocation recipeId, List<StackSpec> inputs, List<StackSpec> outputs) {
        return new PatternSpec(PatternKind.STONECUTTING, recipeId, inputs, outputs);
    }

    public static PatternSpec smithing(ResourceLocation recipeId, List<StackSpec> inputs, List<StackSpec> outputs) {
        return new PatternSpec(PatternKind.SMITHING, recipeId, inputs, outputs);
    }

    public boolean fits() {
        return inputs.size() <= MAX_INPUTS && outputs.size() <= MAX_OUTPUTS && !outputs.isEmpty();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeResourceLocation(recipeId);
        buf.writeVarInt(inputs.size());
        for (StackSpec spec : inputs) {
            spec.write(buf);
        }
        buf.writeVarInt(outputs.size());
        for (StackSpec spec : outputs) {
            spec.write(buf);
        }
    }

    public static PatternSpec read(FriendlyByteBuf buf) {
        PatternKind kind = PatternKind.byOrdinal(buf.readByte());
        ResourceLocation recipeId = buf.readResourceLocation();
        int inputCount = buf.readVarInt();
        if (inputCount < 0 || inputCount > MAX_INPUTS) throw new IllegalArgumentException("Invalid pattern input count");
        List<StackSpec> inputs = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            inputs.add(StackSpec.read(buf));
        }
        int outputCount = buf.readVarInt();
        if (outputCount < 1 || outputCount > MAX_OUTPUTS) throw new IllegalArgumentException("Invalid pattern output count");
        List<StackSpec> outputs = new ArrayList<>(outputCount);
        for (int i = 0; i < outputCount; i++) {
            outputs.add(StackSpec.read(buf));
        }
        return new PatternSpec(kind, recipeId, inputs, outputs);
    }
}
