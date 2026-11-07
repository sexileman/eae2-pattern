package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.*;

/** Presentation only; counts are stored observations, never a client-side scan of source recipes. */
public final class WildcardTooltipText {
    private WildcardTooltipText() {}
    public static boolean isGeneratedName(Component name) {
        return name.getContents() instanceof TranslatableContents content && content.getKey().equals("eae2_pattern.wildcard.name");
    }
    public static List<Component> lines(Component type,int count,boolean dynamic,String encoder) {
        var lines=new ArrayList<Component>();lines.add(Component.empty());
        lines.add(Component.translatable("eae2_pattern.wildcard.encoding_type",type.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("eae2_pattern.wildcard.matched_patterns",Component.literal(Integer.toString(count)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(dynamic?"eae2_pattern.wildcard.dynamic_note":"eae2_pattern.wildcard.fixed_note").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());
        if(!encoder.isEmpty())lines.add(Component.translatable("eae2_pattern.wildcard.encoder",encoder).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("eae2_pattern.wildcard.edit_hint").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());return lines;
    }
}
