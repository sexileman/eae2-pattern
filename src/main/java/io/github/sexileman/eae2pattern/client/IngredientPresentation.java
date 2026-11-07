package io.github.sexileman.eae2pattern.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import java.util.ArrayList;
import java.util.List;

/** Slot semantics shared by source details, encoded details and tooltips. Never displays NC as 0% consumed. */
public record IngredientPresentation(Kind kind, String chance) {
    public enum Kind { REGULAR, NOT_CONSUMED, CHANCE_INPUT, CHANCE_OUTPUT, MIXED }
    public static final IngredientPresentation REGULAR = new IngredientPresentation(Kind.REGULAR, "");
    public static IngredientPresentation of(Ingredient ingredient, int side) {
        if (side == 0 && ingredient.neverConsumed()) return new IngredientPresentation(Kind.NOT_CONSUMED, "");
        if (side == 0 && ingredient.probabilistic()) return new IngredientPresentation(Kind.CHANCE_INPUT, ingredient.chanceText());
        if (side == 1 && !ingredient.guaranteedOutput()) return new IngredientPresentation(Kind.CHANCE_OUTPUT, ingredient.chanceText());
        return REGULAR;
    }
    public static IngredientPresentation encoded(RecipePlan.Entry entry, StackSpec spec, int side, boolean dropTools) {
        List<Ingredient> candidates = new ArrayList<>(side == 0 ? entry.inputs() : entry.outputs());
        if (side == 0 && !dropTools) candidates.addAll(entry.catalysts());
        IngredientPresentation found = null;
        for (Ingredient ingredient : candidates) {
            if (!RecipeDiagnostics.sameMaterial(spec, ingredient.spec())) continue;
            if (side == 0 && dropTools && ingredient.neverConsumed()) continue;
            IngredientPresentation next = of(ingredient, side);
            if (found != null && !found.equals(next)) return new IngredientPresentation(Kind.MIXED, "");
            found = next;
        }
        return found == null ? REGULAR : found;
    }
    public String badge() {
        return switch (kind) { case NOT_CONSUMED -> "∞"; case CHANCE_INPUT, CHANCE_OUTPUT -> "%"; case MIXED -> "?"; default -> ""; };
    }
    public int color() { return kind == Kind.NOT_CONSUMED ? 0xFFBE2424 : WorkbenchTheme.WARNING; }
    public Component description() {
        String key = switch (kind) {
            case NOT_CONSUMED -> "slot_not_consumed";
            case CHANCE_INPUT -> "slot_chance_input";
            case CHANCE_OUTPUT -> "slot_chance_output";
            case MIXED -> "slot_mixed";
            default -> "slot_regular";
        };
        return Component.translatable("eae2_pattern.workbench." + key, chance)
                .withStyle(kind == Kind.NOT_CONSUMED ? ChatFormatting.RED : kind == Kind.REGULAR ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }
}
