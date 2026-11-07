package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

/** Bounded, versioned container. Every child is tied to a real source recipe, never a material guess. */
public final class MachineWildcardData {
    public static final String KEY = "eae2_machine_wildcard";
    public static final int MAX_RECIPES = 32, MAX_BYTES = 24_000;
    private MachineWildcardData() {}
    public static boolean matches(ResourceLocation bound, ResourceLocation actual, byte[] stored, byte[] current) {
        return bound != null && bound.equals(actual) && stored != null && stored.length == 32
                && current != null && java.util.Arrays.equals(stored, current);
    }
    public static int bytes(CompoundTag tag) {
        try {
            var out = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(out));
            return out.size();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid wildcard NBT", e); }
    }
    public static CompoundTag create(ResourceLocation type, List<CompoundTag> entries) {
        CompoundTag data = new CompoundTag();
        data.putInt("version", 1); data.putString("machine", type.toString());
        ListTag rows = new ListTag(); entries.forEach(row -> rows.add(row.copy())); data.put("recipes", rows);
        validate(data);
        return data;
    }
    public static ResourceLocation validate(CompoundTag data) {
        if (data.getInt("version") != 1 || bytes(data) > MAX_BYTES)
            throw new IllegalArgumentException("Unsupported or oversized machine wildcard");
        ResourceLocation machine = ResourceLocation.tryParse(data.getString("machine"));
        ListTag rows = data.getList("recipes", 10);
        if (machine == null || rows.isEmpty() || rows.size() > MAX_RECIPES)
            throw new IllegalArgumentException("Invalid machine wildcard entries");
        var seen = new HashSet<ResourceLocation>();
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("recipe"));
            if (id == null || !seen.add(id) || row.getByteArray("fingerprint").length != 32 || !row.contains("pattern", 10))
                throw new IllegalArgumentException("Invalid or duplicate source recipe");
        }
        return machine;
    }
}
