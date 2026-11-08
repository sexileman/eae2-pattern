package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Bounded UI metadata only; source recipes and machine types are independently validated by the server. */
public final class WildcardEditorState {
    private WildcardEditorState() {}
    public static CompoundTag clean(CompoundTag input) {
        CompoundTag out = new CompoundTag();
        if (input == null) return out;
        for (String key : new String[]{"in_include", "in_exclude", "out_include", "out_exclude", "category", "in_form", "out_form", "material"}) {
            String value = input.getString(key);
            if (value.length() > 256) throw new IllegalArgumentException("Wildcard rule exceeds 256 characters");
            if (key.equals("category") && !value.isEmpty() && ResourceLocation.tryParse(value) == null)
                throw new IllegalArgumentException("Invalid wildcard editor category");
            out.putString(key, value);
        }
        for (String key : new String[]{"in_all", "out_all", "search_tools", "drop_tools", "dynamic"}) out.putBoolean(key, input.getBoolean(key));
        for (String key : new String[]{"in_scope", "out_scope", "output_policy"}) {
            int value = input.getInt(key);
            if (value < 0 || value > 2) throw new IllegalArgumentException("Invalid wildcard editor option");
            out.putInt(key, value);
        }
        for(String key:new String[]{"in_form","out_form"})if(!MaterialForms.FORMS.contains(out.getString(key)))throw new IllegalArgumentException("Invalid material form");
        String material=out.getString("material");if(!material.isEmpty()&&!material.matches("[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid material family");
        var exceptions=input.getList("exclude_ids",8);if(exceptions.size()>2048)throw new IllegalArgumentException("Too many wildcard exceptions");
        var ids=new net.minecraft.nbt.ListTag();var seen=new java.util.HashSet<String>();for(int i=0;i<exceptions.size();i++){String id=exceptions.getString(i);if(id.length()>256||ResourceLocation.tryParse(id)==null)throw new IllegalArgumentException("Invalid excluded recipe");if(seen.add(id))ids.add(net.minecraft.nbt.StringTag.valueOf(id));}out.put("exclude_ids",ids);
        int multiplier = input.contains("multiplier") ? input.getInt("multiplier") : 1;
        if (multiplier < 1 || multiplier > 65536) throw new IllegalArgumentException("Invalid wildcard multiplier");
        out.putInt("multiplier", multiplier);
        if (input.contains("held_fingerprint")) {
            if (!input.contains("held_fingerprint", 7) || input.getByteArray("held_fingerprint").length != 32)
                throw new IllegalArgumentException("Invalid held wildcard fingerprint");
            out.putByteArray("held_fingerprint", input.getByteArray("held_fingerprint").clone());
        }
        CircuitCondition.read(input).write(out);
        return out;
    }
}
