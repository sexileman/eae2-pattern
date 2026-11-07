package io.github.sexileman.eae2pattern.wildcard;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class WildcardItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EAE2Pattern.MOD_ID);
    public static final RegistryObject<Item> MACHINE_PATTERN = ITEMS.register("machine_wildcard_pattern", () -> new Item(new Item.Properties().stacksTo(64)) {
        @Override public int getMaxStackSize(net.minecraft.world.item.ItemStack stack) { return WildcardCarrierRules.maxStackSize(stack.hasTag()); }
        @Override public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
            return WildcardTooltipText.name(stack);
        }
    });
    public static boolean writable(net.minecraft.world.item.ItemStack stack) {
        return MachineWildcard.isOwnItem(stack) && WildcardCarrierRules.validCount(stack.getCount(), stack.hasTag());
    }
    private WildcardItems() {}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(MACHINE_PATTERN.get());
    }
}
