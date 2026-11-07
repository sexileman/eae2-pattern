package io.github.sexileman.eae2pattern.wildcard;

import com.google.gson.JsonElement;
import java.util.TreeSet;

/** Sort object keys without reordering ingredient/output arrays, for stable fingerprints after restart. */
public final class CanonicalRecipeJson {
    private CanonicalRecipeJson() {}
    public static String encode(JsonElement value) {
        if (value.isJsonObject()) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) {
                if (!first) out.append(','); first = false;
                out.append(new com.google.gson.JsonPrimitive(key)).append(':').append(encode(value.getAsJsonObject().get(key)));
            }
            return out.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder out = new StringBuilder("["); boolean first = true;
            for (JsonElement entry : value.getAsJsonArray()) {
                if (!first) out.append(','); first = false; out.append(encode(entry));
            }
            return out.append(']').toString();
        }
        return value.toString();
    }
}
