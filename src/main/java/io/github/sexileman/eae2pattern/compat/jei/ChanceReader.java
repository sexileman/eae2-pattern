package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers the "this only happens 90% of the time" information that JEI does not model itself.
 *
 * <p>JEI has no concept of a probabilistic ingredient: a slot is either there or not. Mods that do have one
 * (GregTech is the big one) express it as an extra line in the slot tooltip, so that is what we read. Two shapes
 * are accepted, a labelled percentage such as {@code Chance: 90%} and a bare percentage line such as
 * {@code 90%}. Working on the tooltip rather than on GregTech's own classes keeps the mod independent of it, and
 * it also covers every other mod that prints the same thing.</p>
 *
 * <p>When nothing is found the slot is treated as certain, which is the safe default: a pattern that consumes
 * something the machine always consumes is correct, while one that silently drops it is not.</p>
 */
public final class ChanceReader {

    private static final Pattern LABELLED = Pattern.compile(
            "(?:chance|probability|chance of|概率|几率|几率消耗)\\s*[:：]?\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*%",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BARE = Pattern.compile("^\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*%\\s*$");

    private ChanceReader() {
    }

    /** @return the chance in the range (0, 1], 1 meaning "always". */
    public static float chanceOf(IRecipeSlotView slot) {
        if (!(slot instanceof IRecipeSlotDrawable drawable)) {
            return 1.0F;
        }
        List<Component> tooltip;
        try {
            tooltip = drawable.getTooltip();
        } catch (Throwable t) {
            EAE2Pattern.LOGGER.debug("Could not read a slot tooltip while collecting recipes", t);
            return 1.0F;
        }
        if (tooltip == null || tooltip.isEmpty()) {
            return 1.0F;
        }
        for (Component line : tooltip) {
            Float chance = parse(line.getString());
            if (chance != null) {
                return chance;
            }
        }
        return 1.0F;
    }

    static Float parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        Matcher labelled = LABELLED.matcher(line);
        if (labelled.find()) {
            return toChance(labelled.group(1));
        }
        Matcher bare = BARE.matcher(line.toLowerCase(Locale.ROOT));
        if (bare.matches()) {
            return toChance(bare.group(1));
        }
        return null;
    }

    private static Float toChance(String percent) {
        try {
            float value = Float.parseFloat(percent) / 100.0F;
            if (value <= 0.0F) {
                return 0.0F;
            }
            return Math.min(1.0F, value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
