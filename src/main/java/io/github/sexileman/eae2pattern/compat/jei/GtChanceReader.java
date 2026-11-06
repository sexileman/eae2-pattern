package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.AccessibleObject;

/**
 * Reads GregTech CEu's per-content chances, which JEI does not model at all.
 *
 * <p>GT marks every input slot as a plain JEI input: unlike most mods it never uses the catalyst role, and it does
 * not attach the chance text to the slot tooltip either - the "90%" or "Not Consumed" is drawn by its own widget
 * layer. The only reliable place it exists is the recipe object itself, so this class reflects on it. Everything
 * here is optional: if GregTech is absent, or its internals moved, the adapter reports "no data" and the rest of
 * the mod keeps treating every ingredient as certain.</p>
 *
 * <p>GT encodes "not consumed" as {@code chance == 0} and "always" as {@code chance == maxChance} (10000), with
 * values in between meaning a probabilistic consumption or production.</p>
 */
public final class GtChanceReader {

    private static final String WRAPPER = "com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper";
    private static final String RECIPE = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final String ITEM_CAP = "com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability";
    private static final String FLUID_CAP = "com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability";

    private static boolean probed;
    private static boolean available;
    private static final ClassValue<Map<String, AccessibleObject>> MEMBERS = new ClassValue<>() {
        @Override protected Map<String, AccessibleObject> computeValue(Class<?> type) {
            Map<String, AccessibleObject> accessors = new HashMap<>();
            for (String name : List.of("recipe", "chance", "maxChance")) {
                try { accessors.put(name, type.getField(name)); }
                catch (NoSuchFieldException e) {
                    try { accessors.put(name, type.getMethod(name)); }
                    catch (NoSuchMethodException ignored) { }
                }
            }
            return accessors;
        }
    };

    private GtChanceReader() {
    }

    /** A cursor over the chances of one side of a recipe, advancing as JEI slots are walked. */
    public static final class Cursor {
        private final float[] items;
        private final float[] fluids;
        private int itemIndex;
        private int fluidIndex;

        private Cursor(float[] items, float[] fluids) {
            this.items = items;
            this.fluids = fluids;
        }

        public float next(boolean fluid) {
            if (fluid) {
                return this.fluidIndex < this.fluids.length ? this.fluids[this.fluidIndex++] : 1.0F;
            }
            return this.itemIndex < this.items.length ? this.items[this.itemIndex++] : 1.0F;
        }
    }

    /**
     * @param itemSlots  how many item slots JEI drew for this side
     * @param fluidSlots how many fluid slots JEI drew for this side
     * @return null when GregTech is absent, its classes moved, or - important - when the recipe's contents and
     *         JEI's slots disagree in number. A misaligned mapping would mark a guaranteed input as "0%, never
     *         consumed" and drop it from the pattern, which is far worse than not knowing the chances at all.
     */
    @Nullable
    public static Cursor cursor(Object jeiRecipe, boolean inputs, int itemSlots, int fluidSlots) {
        if (!isAvailable() || jeiRecipe == null) {
            return null;
        }
        try {
            Object recipe = recipeOf(jeiRecipe);
            if (recipe == null) {
                return null;
            }
            float[] itemChances = chances(recipe, inputs, ITEM_CAP);
            float[] fluidChances = chances(recipe, inputs, FLUID_CAP);
            if (itemChances.length != itemSlots || fluidChances.length != fluidSlots) {
                EAE2Pattern.LOGGER.debug(
                        "GregTech chances not used for {}: {} item and {} fluid contents against {} and {} JEI slots",
                        recipe.getClass().getSimpleName(), itemChances.length, fluidChances.length, itemSlots, fluidSlots);
                return null;
            }
            return new Cursor(itemChances, fluidChances);
        } catch (ReflectiveOperationException | RuntimeException e) {
            EAE2Pattern.LOGGER.debug("Could not read GregTech chances for {}", jeiRecipe.getClass().getName(), e);
            return null;
        }
    }

    private static boolean isAvailable() {
        if (!probed) {
            probed = true;
            try {
                Class.forName(RECIPE);
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }
        return available;
    }

    private static Object recipeOf(Object jeiRecipe) throws ReflectiveOperationException {
        if (jeiRecipe.getClass().getName().equals(RECIPE)) return jeiRecipe;
        if (!jeiRecipe.getClass().getName().equals(WRAPPER)) {
            return null;
        }
        return member(jeiRecipe, "recipe");
    }

    private static float[] chances(Object recipe, boolean inputs, String capabilityClass)
            throws ReflectiveOperationException {
        Class<?> cap = Class.forName(capabilityClass);
        Object capability = cap.getField("CAP").get(null);
        String methodName = inputs ? "getInputContents" : "getOutputContents";
        Method method = recipe.getClass().getMethod(methodName, Class.forName(
                "com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability"));
        Object result = method.invoke(recipe, capability);
        if (!(result instanceof List<?> contents)) {
            return new float[0];
        }
        float[] chances = new float[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            chances[i] = chanceOf(contents.get(i));
        }
        return chances;
    }

    private static float chanceOf(Object content) throws ReflectiveOperationException {
        if (content == null) {
            return 1.0F;
        }
        int chance = ((Number) member(content, "chance")).intValue();
        int maxChance = ((Number) member(content, "maxChance")).intValue();
        if (maxChance <= 0) {
            return 1.0F;
        }
        if (chance <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) chance / (float) maxChance);
    }

    /** Both older public-field Content and newer record Content are used by 1.20.1 packs. */
    private static Object member(Object target, String name) throws ReflectiveOperationException {
        AccessibleObject accessor = MEMBERS.get(target.getClass()).get(name);
        if (accessor instanceof Field field) return field.get(target);
        if (accessor instanceof Method method) return method.invoke(target);
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }
}
