package io.github.sexileman.eae2pattern.wildcard;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class MachineWildcardEvents {
    private MachineWildcardEvents() {}
    @SubscribeEvent public static void reload(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {MachineWildcard.clearFingerprints();WildcardProviderRefresh.refresh();}
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        MachineWildcard.clearFingerprints(); WildcardProviderRefresh.clear();
    }
    @SubscribeEvent public static void tags(net.minecraftforge.event.TagsUpdatedEvent event) { MaterialForms.clear(); }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        var stack = event.getEntity().getItemInHand(event.getHand());
        if (!MachineWildcard.isOwnItem(stack) && !MachineWildcard.isBound(stack)) return;
        // The upstream material editor does not understand bound recipes. Do not silently let it
        // modify the generic components while the machine whitelist remains unchanged.
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        if (event.getLevel().isClientSide)
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.github.sexileman.eae2pattern.client.WildcardEditorScreen.openStored(stack.copy(), event.getHand()));
    }
    @SubscribeEvent public static void missing(net.minecraftforge.registries.MissingMappingsEvent event) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("wildcard_pattern")) return;
        for (var mapping : event.getMappings(net.minecraftforge.registries.ForgeRegistries.Keys.ITEMS, "wildcard_pattern"))
            if (mapping.getKey().getPath().equals("wildcard_pattern")) {
                EAE2Pattern.LOGGER.info("Migrating missing item {} to eae2_pattern:machine_wildcard_pattern; existing NBT is preserved", mapping.getKey());
                mapping.remap(WildcardItems.MACHINE_PATTERN.get());
            }
    }
}
