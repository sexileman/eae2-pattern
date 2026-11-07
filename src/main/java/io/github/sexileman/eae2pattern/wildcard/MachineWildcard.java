package io.github.sexileman.eae2pattern.wildcard;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

/** Optional adapter for both upstream branches and GTLCore's native expansion entry point. */
public final class MachineWildcard {
    public static final ResourceLocation ITEM = new ResourceLocation("eae2_pattern", "machine_wildcard_pattern");
    private record Fingerprint(java.lang.ref.WeakReference<Recipe<?>> owner, byte[] hash) {}
    private static final Map<Recipe<?>, Fingerprint> FINGERPRINTS = new WeakHashMap<>();
    private MachineWildcard() {}
    public static synchronized void clearFingerprints() { FINGERPRINTS.clear(); }
    public static boolean available() { return ForgeRegistries.ITEMS.containsKey(ITEM); }
    public static boolean isOwnItem(ItemStack stack) { return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ITEM); }
    public static boolean isBound(ItemStack stack) { return stack.getTag() != null && stack.getTag().contains(MachineWildcardData.KEY, 10); }
    public static ResourceLocation type(Recipe<?> recipe) { return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized byte[] fingerprint(Recipe<?> recipe) {
        Fingerprint cached = FINGERPRINTS.get(recipe);
        if (cached != null && cached.owner().get() == recipe) return cached.hash();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            byte[] payload;
            if (recipe.getSerializer().getClass().getName().equals("com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer")) {
                // GT uses identity-keyed capability maps. Their network iteration order can change
                // between JVMs, so hashing raw network bytes would invalidate unchanged recipes.
                com.mojang.serialization.Codec codec = (com.mojang.serialization.Codec) recipe.getSerializer().getClass().getField("CODEC").get(null);
                var json = (com.google.gson.JsonElement) codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, recipe)
                        .getOrThrow(false, message -> EAE2Pattern.LOGGER.debug("GT recipe fingerprint: {}", message));
                payload = CanonicalRecipeJson.encode(json).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                ((net.minecraft.world.item.crafting.RecipeSerializer) recipe.getSerializer()).toNetwork(buf, recipe);
                payload = new byte[buf.readableBytes()]; buf.readBytes(payload);
            }
            if (payload.length > 1_000_000) throw new IllegalArgumentException("Source recipe is too large");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(payload);
            if (FINGERPRINTS.size() >= 8192) FINGERPRINTS.clear();
            FINGERPRINTS.put(recipe, new Fingerprint(new java.lang.ref.WeakReference<>(recipe), hash)); return hash;
        } catch (java.security.NoSuchAlgorithmException | ReflectiveOperationException e) { throw new IllegalStateException("Cannot fingerprint source recipe", e); }
        finally { buf.release(); }
    }
    public static ItemStack item(ResourceLocation type, List<CompoundTag> entries, String player) {
        if (!available()) throw new IllegalStateException("Independent machine wildcard item is not registered");
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(ITEM));
        stack.getOrCreateTag().put(MachineWildcardData.KEY, MachineWildcardData.create(type, entries));
        var ids = new net.minecraft.nbt.ListTag(); ids.add(net.minecraft.nbt.StringTag.valueOf(type.toString()));
        CompoundTag gtl = new CompoundTag(); gtl.put("patternQuickUploadRecipeTypes", ids);
        stack.getOrCreateTag().put("gtlcore", gtl);
        stack.getOrCreateTag().putString("encodePlayer", player);
        stack.setHoverName(Component.translatable("eae2_pattern.wildcard.name", type.toString(), entries.size()));
        return stack;
    }
    public static Stream<IPatternDetails> expand(ItemStack stack, Level level) {
        // Missing, changed, or moved recipes fail closed. Reloading never broadens the machine whitelist.
        if (level == null || level.isClientSide) return Stream.empty();
        try {
            CompoundTag data = stack.getTag().getCompound(MachineWildcardData.KEY);
            ResourceLocation machine = ResourceLocation.tryParse(data.getString("machine"));
            if (machine == null) return Stream.empty();
            var rows = MachineWildcardData.readRecipes(data);
            var result = new ArrayList<IPatternDetails>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i);
                var recipe = level.getRecipeManager().byKey(new ResourceLocation(row.getString("recipe"))).orElse(null);
                if (recipe == null || !MachineWildcardData.matches(machine, type(recipe), row.getByteArray("fingerprint"), fingerprint(recipe))) continue;
                ItemStack encoded = ItemStack.of(row.getCompound("pattern"));
                // Only concrete AE2 processing children are accepted; no recursive wildcard expansion.
                if (!BuiltInRegistries.ITEM.getKey(encoded.getItem()).equals(new ResourceLocation("ae2", "processing_pattern"))) continue;
                IPatternDetails details = PatternDetailsHelper.decodePattern(encoded, level);
                if (details != null) result.add(details);
            }
            return result.stream();
        } catch (RuntimeException e) {
            EAE2Pattern.LOGGER.debug("Rejected machine wildcard", e); return Stream.empty();
        }
    }
}
