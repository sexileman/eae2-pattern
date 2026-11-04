package io.github.sexileman.eae2pattern.pattern;

import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Requirement #7: "filter out the items in the recipe that are not consumed".
 *
 * <p>Three distinct things can make an ingredient "not consumed", and they need different handling:</p>
 * <ol>
 *     <li><b>Catalysts.</b> JEI lists the machine itself in the catalyst role of a recipe. Those are not
 *     ingredients at all and must never enter the pattern. The JEI adapter drops them before we get here.</li>
 *     <li><b>Container items.</b> A bucket of water is consumed, then the empty bucket is returned. Net cost is
 *     zero, so a pattern that demands the bucket will stall the crafting job. Detected via the crafting
 *     remainder.</li>
 *     <li><b>Configured blacklist.</b> Pack authors and players know their pack better than any heuristic does.</li>
 * </ol>
 */
public final class IngredientFilter {

    private IngredientFilter() {
    }

    /** Applies every filter to a pattern input list, preserving order. */
    public static List<StackSpec> filterInputs(List<StackSpec> inputs) {
        List<StackSpec> result = new ArrayList<>(inputs.size());
        for (StackSpec spec : inputs) {
            if (shouldKeep(spec)) {
                result.add(spec);
            }
        }
        return result;
    }

    public static boolean shouldKeep(StackSpec spec) {
        if (matchesIgnoredList(spec)) {
            return false;
        }
        if (spec.isItem() && EAE2PatternConfig.STRIP_CONTAINER_ITEMS.get()) {
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty() && hasCraftingRemainder(stack.getItem())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCraftingRemainder(Item item) {
        // getCraftingRemainingItem() is deprecated on Item in 1.20.1 in favour of hasCraftingRemainingItem(),
        // but the boolean form is the one that stays cheap for items without a remainder.
        return item.hasCraftingRemainingItem();
    }

    private static boolean matchesIgnoredList(StackSpec spec) {
        List<? extends String> ignored = EAE2PatternConfig.IGNORED_STACKS.get();
        if (ignored.isEmpty()) {
            return false;
        }
        String candidate = spec.id().toString().toLowerCase(Locale.ROOT);
        for (String entry : ignored) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (matchesPattern(entry.trim().toLowerCase(Locale.ROOT), candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String pattern, String candidate) {
        int star = pattern.indexOf('*');
        if (star < 0) {
            return pattern.equals(candidate);
        }
        String prefix = pattern.substring(0, star);
        String suffix = pattern.substring(star + 1);
        return candidate.length() >= prefix.length() + suffix.length()
                && candidate.startsWith(prefix)
                && candidate.endsWith(suffix);
    }

    /** Convenience for the GUI: is this exact id blacklisted? */
    public static boolean isBlacklisted(ResourceLocation id) {
        return matchesIgnoredList(new StackSpec(StackType.ITEM, id, 1));
    }

    /** Used by the client preview to resolve an item id that may have been removed by a reload. */
    public static boolean itemExists(ResourceLocation id) {
        return ForgeRegistries.ITEMS.containsKey(id);
    }
}
