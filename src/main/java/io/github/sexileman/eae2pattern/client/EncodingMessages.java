package io.github.sexileman.eae2pattern.client;
import net.minecraft.network.chat.Component;
import java.util.Locale;
/** Brief player-facing explanation; original diagnostics remain in logs. */
public final class EncodingMessages {
    private EncodingMessages(){}
    public static String key(String raw){
        String text=raw==null?"":raw.toLowerCase(Locale.ROOT);
        if(text.contains("inspection capacity"))return "inspection_limited";
        if(text.contains("unsupported ingredient"))return "unsupported_ingredient";
        if(text.contains("no ingredients after filtering")||text.contains("no encodable ingredients left after filtering"))return "filtered_input";
        if(text.contains("no output")||text.contains("no valid input or output"))return "no_output";
        if(text.contains("network")&&(text.contains("unavailable")||text.contains("changed")))return "network";
        if(text.contains("held wildcard changed"))return "held_changed";
        if(text.contains("another pattern job"))return "busy";
        if(text.contains("2048")||text.contains("multiple wildcard items")||text.contains("too large")||text.contains("exceeds"))return "capacity";
        if(text.contains("fixed collection mode"))return "fixed_mode";
        if(text.contains("no server recipes match"))return "no_match";
        if(text.contains("not registered")||text.contains("recipe unavailable"))return "missing_recipe";
        return "failed";
    }
    public static Component explain(String raw){return Component.translatable("eae2_pattern.error."+key(raw));}
}
