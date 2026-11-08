package io.github.sexileman.eae2pattern.pattern;

import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explicit pack exclusions only. Actual non-consumed GT inputs are handled before encoding.
 * Crafting remainders do not prove that a processing input is free or interchangeable with a fluid.
 * Native AE2 crafting encoding owns bucket/fluid substitutions and container returns.
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
        return true;
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
