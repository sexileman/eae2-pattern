package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.*;

/** Presentation only; counts are stored observations, never a client-side scan of source recipes. */
public final class WildcardTooltipText {
    private static java.util.function.BiFunction<String,String,Component> typeNames = (machine, category) -> Component.literal(machine);
    public static void setTypeNames(java.util.function.BiFunction<String,String,Component> resolver) { typeNames = resolver; }
    public static Component name(net.minecraft.world.item.ItemStack stack) {
        if (!MachineWildcard.isBound(stack)) return Component.translatable("item.eae2_pattern.machine_wildcard_pattern.blank");
        var root = stack.getTag(); var data = root.getCompound(MachineWildcardData.KEY);
        String category = data.getInt("version") == 3 ? data.getCompound("rules").getString("category") : root.getCompound("editor").getString("category");
        return boundName(typeNames.apply(data.getString("machine"), category));
    }
    public static Component boundName(Component type) {
        return Component.translatable("eae2_pattern.wildcard.bound_name", type.copy()).withStyle(style -> style.withColor(0xE640E6).withItalic(false));
    }
    private WildcardTooltipText() {}
    public static boolean isGeneratedName(Component name) {
        return name.getContents() instanceof TranslatableContents content && content.getKey().equals("eae2_pattern.wildcard.name");
    }
    public static List<Component> lines(Component type,int count,boolean dynamic,String encoder) {
        var lines=new ArrayList<Component>();
        if(!encoder.isEmpty())lines.add(Component.translatable("eae2_pattern.wildcard.encoder",encoder).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("eae2_pattern.wildcard.encoding_type",type.copy().withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("eae2_pattern.wildcard.matched_patterns",
                        Component.literal(Integer.toString(count)).withStyle(ChatFormatting.WHITE),
                        Component.translatable(dynamic
                                        ? "eae2_pattern.wildcard.mode.dynamic"
                                        : "eae2_pattern.wildcard.mode.fixed")
                                .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
