package io.github.sexileman.eae2pattern.compat.gtl;

import appeng.api.crafting.IPatternDetails;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

/** Ships in the universal EAE2 jar; GTL classes are never linked here. Neither optional provider is linked directly in bytecode. */
public final class GtlWildcardBridge {
    private static Method ownIs, ownExpand, legacyExpand;
    private static Item legacyItem;
    private static boolean reported;
    private GtlWildcardBridge() {}
    public static boolean available() {
        return ModList.get().isLoaded("eae2_pattern") || ModList.get().isLoaded("wildcard_pattern");
    }
    private static boolean own(ItemStack stack) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("eae2_pattern")) return false;
        if (ownIs == null) {
            Class<?> api = Class.forName("io.github.sexileman.eae2pattern.api.MachineWildcardApi");
            ownIs = api.getMethod("isPattern", ItemStack.class);
            ownExpand = api.getMethod("expand", ItemStack.class, Level.class);
        }
        return (Boolean) ownIs.invoke(null, stack);
    }
    private static boolean legacy(ItemStack stack) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("wildcard_pattern")) return false;
        if (legacyItem == null) {
            Object entry = Class.forName("org.leodreamer.wildcard_pattern.WildcardItems").getField("WILDCARD_PATTERN").get(null);
            legacyItem = ((ItemLike) entry).asItem();
            legacyExpand = Class.forName("org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic")
                    .getMethod("decodePatterns", ItemStack.class, Level.class);
        }
        return stack.is(legacyItem);
    }
    public static boolean accepts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try { if (own(stack)) return true; }
        catch (ReflectiveOperationException | LinkageError e) { report(e); }
        try { return legacy(stack); }
        catch (ReflectiveOperationException | LinkageError e) { report(e); return false; }
    }
    @SuppressWarnings("unchecked")
    public static List<IPatternDetails> expand(ItemStack stack, Level level) {
        if (stack == null || level == null || stack.isEmpty()) return List.of();
        try { if (own(stack)) return List.copyOf((List<IPatternDetails>) ownExpand.invoke(null, stack, level)); }
        catch (ReflectiveOperationException | LinkageError e) { report(e); return List.of(); }
        try {
            if (legacy(stack)) try (Stream<IPatternDetails> stream = (Stream<IPatternDetails>) legacyExpand.invoke(null, stack, level)) {
                return stream.filter(java.util.Objects::nonNull).toList();
            }
        } catch (ReflectiveOperationException | LinkageError e) { report(e); }
        return List.of();
    }
    private static void report(Throwable error) {
        if (!reported) { reported = true; com.mojang.logging.LogUtils.getLogger().error("Optional wildcard provider failed; recipe expansion rejected", error); }
    }
}
