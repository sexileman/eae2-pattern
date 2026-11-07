package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Independent v2 format: up to 2048 real recipes, compressed with bounded decoding. */
public final class MachineWildcardData {
    public static final String KEY = "eae2_machine_wildcard";
    public static final int MAX_RECIPES = 2048, MAX_BYTES = 512_000, MAX_RAW_BYTES = 4_000_000;
    public static final class CapacityException extends IllegalArgumentException {
        public CapacityException(String message) { super(message); }
    }
    private MachineWildcardData() {}
    public static boolean matches(ResourceLocation bound, ResourceLocation actual, byte[] stored, byte[] current) {
        return bound != null && bound.equals(actual) && stored != null && stored.length == 32
                && current != null && Arrays.equals(stored, current);
    }
    public static int bytes(CompoundTag tag) {
        try {
            var out = new ByteArrayOutputStream(); NbtIo.write(tag, new DataOutputStream(out)); return out.size();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid wildcard NBT", e); }
    }
    public static int count(CompoundTag data) {
        return data.getInt("version") >= 2 ? data.getInt("count") : data.getList("recipes", 10).size();
    }
    public static CompoundTag create(ResourceLocation type, List<CompoundTag> entries) {
        ListTag rows = new ListTag(); entries.forEach(row -> rows.add(row.copy()));
        validateRows(rows);
        CompoundTag payload = new CompoundTag(); payload.put("recipes", rows);
        if (bytes(payload) > MAX_RAW_BYTES) throw new CapacityException("Wildcard decoded payload exceeds 4 MB");
        try {
            var out = new ByteArrayOutputStream(); NbtIo.writeCompressed(payload, out);
            if (out.size() > MAX_BYTES) throw new CapacityException("Wildcard compressed payload exceeds 512 KB");
            CompoundTag data = new CompoundTag(); data.putInt("version", 2); data.putString("machine", type.toString());
            data.putInt("count", entries.size()); data.putByteArray("payload", out.toByteArray());
            // A generated item must also pass the reader's allocation accounting; otherwise split
            // before spending blanks rather than creating an item which cannot later be expanded.
            try { readRecipes(data); }
            catch (RuntimeException allocation) { throw new CapacityException("Wildcard exceeds decoded NBT allocation budget"); }
            return data;
        } catch (IOException e) { throw new IllegalArgumentException("Cannot compress wildcard", e); }
    }
    public static ResourceLocation validate(CompoundTag data) {
        ResourceLocation machine = ResourceLocation.tryParse(data.getString("machine"));
        if (machine == null) throw new IllegalArgumentException("Invalid machine type");
        if(data.getInt("version")==3){dynamic(machine,data.getCompound("rules"),count(data));return machine;}
        readRecipes(data); return machine;
    }
    public static CompoundTag dynamic(ResourceLocation type, CompoundTag rules, int initialCount) {
        if(type==null||initialCount<0||initialCount>MAX_RECIPES)throw new IllegalArgumentException("Invalid dynamic wildcard");
        CompoundTag clean=WildcardEditorState.clean(rules);clean.remove("held_fingerprint");clean.putBoolean("dynamic",true);new DynamicRule(clean);
        CompoundTag data=new CompoundTag();data.putInt("version",3);data.putString("machine",type.toString());data.putInt("count",initialCount);data.put("rules",clean);
        if(bytes(data)>128_000)throw new CapacityException("Dynamic wildcard rules exceed 128 KB");return data;
    }
    public static ListTag readRecipes(CompoundTag data) {
        int version = data.getInt("version");
        ListTag rows;
        if (version == 1) {
            if (bytes(data) > MAX_RAW_BYTES) throw new IllegalArgumentException("Oversized legacy wildcard");
            rows = data.getList("recipes", 10);
        } else if (version == 2) {
            byte[] compressed = data.getByteArray("payload");
            if (compressed.length == 0 || compressed.length > MAX_BYTES || count(data) < 1 || count(data) > MAX_RECIPES)
                throw new IllegalArgumentException("Invalid wildcard size or count");
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                // Limit decompressed bytes before parsing, and separately bound NBT allocation accounting.
                byte[] raw = gzip.readNBytes(MAX_RAW_BYTES + 1);
                if (raw.length > MAX_RAW_BYTES) throw new IllegalArgumentException("Wildcard decompression exceeds 4 MB");
                var payload = NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)), new NbtAccounter(16_000_000L));
                rows = payload.getList("recipes", 10);
                if (rows.size() != count(data)) throw new IllegalArgumentException("Wildcard count mismatch");
            } catch (IOException e) { throw new IllegalArgumentException("Invalid compressed wildcard", e); }
        } else throw new IllegalArgumentException("Unsupported wildcard version");
        validateRows(rows); return rows;
    }
    private static void validateRows(ListTag rows) {
        if (rows.isEmpty() || rows.size() > MAX_RECIPES) throw new IllegalArgumentException("Wildcard requires 1-2048 recipes");
        var seen = new HashSet<ResourceLocation>();
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("recipe"));
            if (id == null || !seen.add(id) || row.getByteArray("fingerprint").length != 32 || !row.contains("pattern", 10))
                throw new IllegalArgumentException("Invalid or duplicate source recipe");
        }
    }
}
