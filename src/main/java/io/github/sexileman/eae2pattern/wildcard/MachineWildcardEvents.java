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
        if (!MachineWildcard.isBound(event.getItemStack())) return;
        var data = event.getItemStack().getTag().getCompound(MachineWildcardData.KEY);
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.machine", data.getString("machine")).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.recipes", data.getList("recipes", 10).size()).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("eae2_pattern.wildcard.bound_help").withStyle(ChatFormatting.GOLD));
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        if (!MachineWildcard.isBound(event.getEntity().getItemInHand(event.getHand()))) return;
        // The upstream material editor does not understand bound recipes. Do not silently let it
        // modify the generic components while the machine whitelist remains unchanged.
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        if (!event.getLevel().isClientSide)
            event.getEntity().sendSystemMessage(Component.translatable("eae2_pattern.wildcard.edit_help"));
    }
}
