package io.github.sexileman.eae2pattern;

import com.mojang.logging.LogUtils;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point. Deliberately thin: the mod is mostly a client-side recipe reader plus a server-side
 * transaction, and neither of those wants a big god object.
 */
@Mod(EAE2Pattern.MOD_ID)
public final class EAE2Pattern {

    public static final String MOD_ID = "eae2_pattern";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EAE2Pattern() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EAE2PatternConfig.SPEC, "eae2_pattern-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                io.github.sexileman.eae2pattern.config.WorkbenchPreferences.SPEC, "eae2_pattern-client.toml");
        modBus.addListener(EAE2Pattern::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                LOGGER.info("EAE2 Pattern {} loaded", container.getModInfo().getVersion()));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
