package io.github.sexileman.eae2pattern.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client wiring: one key binding that opens the preview for the machine under the cursor.
 *
 * <p>Both an in-game tick path and a screen path are needed: the first covers hovering an item in the world-facing
 * JEI overlay, the second covers hovering it inside a container screen, which is the common case.</p>
 */
public final class ClientSetup {

    public static final KeyMapping OPEN_PREVIEW = new KeyMapping(
            "key.eae2_pattern.open_preview",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.eae2_pattern");

    private ClientSetup() {
    }

    @Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_PREVIEW);
        }
    }

    @Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (OPEN_PREVIEW.consumeClick()) {
                EAE2PatternClient.openPreviewForHovered();
            }
        }

        @SubscribeEvent
        public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
            if (OPEN_PREVIEW.matches(event.getKeyCode(), event.getScanCode())) {
                EAE2PatternClient.openPreviewForHovered();
            }
        }
    }
}
