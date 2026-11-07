package io.github.sexileman.eae2pattern.wildcard;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class MachineWildcardEvents {
    private MachineWildcardEvents() {}
    @SubscribeEvent public static void reload(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) MachineWildcard.clearFingerprints();
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        MachineWildcard.clearFingerprints();
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (MachineWildcard.isOwnItem(event.getItemStack()) && !MachineWildcard.isBound(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("eae2_pattern.editor.blank_help").withStyle(ChatFormatting.GOLD)); return;
        }
        if (!MachineWildcard.isBound(event.getItemStack())) return;
        var data = event.getItemStack().getTag().getCompound(MachineWildcardData.KEY);
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.machine", data.getString("machine")).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.recipes", MachineWildcardData.count(data)).withStyle(ChatFormatting.GRAY));
        String encoder = event.getItemStack().getTag().getString("encodePlayer");
        if (!encoder.isEmpty()) event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.encoder", encoder).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.bound_help").withStyle(ChatFormatting.GOLD));
    }
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
            if (mapping.getKey().getPath().equals("wildcard_pattern")) mapping.remap(WildcardItems.MACHINE_PATTERN.get());
    }
}
