package io.github.sexileman.eae2pattern.pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Native metadata used by ExtendedAE Plus and GTLCore; neither mod is required. */
public final class PatternMetadata {
    private PatternMetadata() {}

    public static void apply(ServerPlayer player, PatternSpec spec, ItemStack encoded) {
        boolean plus = ModList.get().isLoaded("extendedae_plus");
        boolean gtl = ModList.get().isLoaded("gtlcore");
        if (!plus && !gtl) return;
        // Resolve the source recipe before scaling/filtering. Never infer a machine from its outputs:
        // the same output may belong to several machine types, and filtered patterns may not match.
        ResourceLocation type = gtl ? player.serverLevel().getRecipeManager().byKey(spec.recipeId())
                .map(recipe -> BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).orElse(null) : null;
        write(encoded.getOrCreateTag(), plus ? player.getGameProfile().getName() : null, type);
    }

    /** Writes only the supported keys, preserving AE2 payloads and unrelated addon data. */
    public static void write(CompoundTag root, String encoder, ResourceLocation type) {
        if (encoder != null && !encoder.isBlank()) root.putString("encodePlayer", encoder);
        if (type != null) {
            CompoundTag gtl = root.getCompound("gtlcore");
            ListTag ids = new ListTag();
            ids.add(StringTag.valueOf(type.toString()));
            gtl.put("patternQuickUploadRecipeTypes", ids);
            root.put("gtlcore", gtl);
        }
    }
}
