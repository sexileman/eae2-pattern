package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.network.chat.Component;

/** Display identity is independent of the persisted filter value. */
public record PickerOption(String value, Component label, Component detail, StackSpec icon) {
    public PickerOption(String value, Component label) { this(value,label,Component.empty(),null); }
    public PickerOption {
        label=label.copy(); detail=detail.copy();
        if(icon!=null)icon=icon.withAmount(1);
    }
    public boolean matches(String query) {
        return OptionalPinyinSearch.contains(label.getString()+" "+value+" "+detail.getString()+" "+(icon==null?"":icon.id()),query);
    }
}
