package io.github.sexileman.eae2pattern.pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * A single ingredient or product of a recipe, flattened to something both sides can agree on:
 * a registry id, a kind and an amount.
 *
 * <p>We deliberately do not ship AE2 {@code AEKey} instances: the client side of the mod must not require
 * AE2 keys to be resolvable for every recipe ingredient (many ingredients are not AE-addressable), and a
 * plain registry id survives a reload in which the client and server disagree about AE2 key identity.</p>
 */
public record StackSpec(StackType type, ResourceLocation id, long amount) {

    public StackSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
    }

    public static StackSpec of(StackType type, ResourceLocation id, long amount) {
        return new StackSpec(type, id, amount);
    }

    public static StackSpec item(Item item, long amount) {
        return new StackSpec(StackType.ITEM, ForgeRegistries.ITEMS.getKey(item), amount);
    }

    public static StackSpec item(ItemStack stack) {
        return new StackSpec(StackType.ITEM, ForgeRegistries.ITEMS.getKey(stack.getItem()), stack.getCount());
    }

    public static StackSpec fluid(ResourceLocation fluidId, long amount) {
        return new StackSpec(StackType.FLUID, fluidId, amount);
    }

    public static StackSpec fluid(net.minecraftforge.fluids.FluidStack stack) {
        ResourceLocation key = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        return new StackSpec(StackType.FLUID, key, stack.getAmount());
    }

    public boolean isItem() {
        return type == StackType.ITEM;
    }

    public ItemStack toItemStack() {
        if (!isItem()) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, (int) Math.min(Integer.MAX_VALUE, amount));
    }

    public StackSpec withAmount(long newAmount) {
        return new StackSpec(type, id, newAmount);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeResourceLocation(id);
        buf.writeVarLong(amount);
    }

    public static StackSpec read(FriendlyByteBuf buf) {
        StackType type = StackType.byOrdinal(buf.readByte());
        ResourceLocation id = buf.readResourceLocation();
        long amount = buf.readVarLong();
        return new StackSpec(type, id, amount);
    }

    @Override
    public String toString() {
        return amount + "x " + type + ":" + id;
    }
}
