package io.github.sexileman.eae2pattern.wildcard;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class WildcardItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EAE2Pattern.MOD_ID);
    public static final RegistryObject<Item> MACHINE_PATTERN = ITEMS.register("machine_wildcard_pattern", () -> new Item(new Item.Properties().stacksTo(1)));
    private WildcardItems() {}
}
