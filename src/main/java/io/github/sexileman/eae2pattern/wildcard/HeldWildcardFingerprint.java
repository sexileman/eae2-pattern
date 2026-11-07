package io.github.sexileman.eae2pattern.wildcard;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.security.*;
import java.util.TreeSet;

/** Canonical snapshot token for a held-item write; compound insertion order is irrelevant. */
public final class HeldWildcardFingerprint {
    private HeldWildcardFingerprint() {}
    public static boolean positionMatches(boolean mainHand, int originalSlot, int currentSlot, boolean ownItem, int count) {
        return ownItem && count == 1 && (!mainHand || originalSlot == currentSlot);
    }
    public static byte[] of(ItemStack stack) { return ofTag(stack.save(new CompoundTag())); }
    public static byte[] visible(ItemStack stack) {
        CompoundTag publicData = new CompoundTag();
        publicData.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        publicData.putInt("Count", stack.getCount());
        var shared = stack.getShareTag(); if (shared != null) publicData.put("tag", shared.copy());
        return ofTag(publicData);
    }
    /** Keep unrelated tags and capability data when re-encoding an existing carrier. */
    public static CompoundTag mergeEncodingTags(CompoundTag original, CompoundTag encoded) {
        CompoundTag result = original.copy();
        for (String key : new String[]{MachineWildcardData.KEY, "editor", "encodePlayer"}) {
            if (!encoded.contains(key)) throw new IllegalArgumentException("Missing encoded wildcard data: " + key);
            result.put(key, encoded.get(key).copy());
        }
        var gtl = result.getCompound("gtlcore").copy(); gtl.merge(encoded.getCompound("gtlcore")); result.put("gtlcore", gtl);
        var display = result.getCompound("display").copy(); display.merge(encoded.getCompound("display")); result.put("display", display);
        return result;
    }
    public static byte[] ofTag(CompoundTag tag) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) { write(tag, out); }
            return digest.digest();
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Cannot fingerprint held wildcard", e); }
    }
    private static void text(String value, DataOutputStream out) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static void write(Tag tag, DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = new TreeSet<>(compound.getAllKeys()); out.writeInt(keys.size());
            for (String key : keys) { text(key, out); write(compound.get(key), out); }
        } else if (tag instanceof ListTag list) {
            out.writeByte(list.getElementType()); out.writeInt(list.size()); for (Tag child : list) write(child, out);
        } else if (tag instanceof ByteArrayTag bytes) {
            out.writeInt(bytes.getAsByteArray().length); out.write(bytes.getAsByteArray());
        } else if (tag instanceof IntArrayTag ints) {
            out.writeInt(ints.getAsIntArray().length); for (int value : ints.getAsIntArray()) out.writeInt(value);
        } else if (tag instanceof LongArrayTag longs) {
            out.writeInt(longs.getAsLongArray().length); for (long value : longs.getAsLongArray()) out.writeLong(value);
        } else text(tag.getAsString(), out);
    }
}
