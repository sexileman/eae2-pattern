package io.github.sexileman.eae2pattern.pattern;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A single ingredient or product of a recipe, flattened to something both sides can agree on:
 * a registry id, an optional NBT payload, a kind and an amount.
 *
 * <p>We deliberately do not ship AE2 {@code AEKey} instances: the client side of the mod must not require
 * AE2 keys to be resolvable for every recipe ingredient (many ingredients are not AE-addressable), and a
 * plain registry id survives a reload in which the client and server disagree about AE2 key identity.</p>
 *
 * <p>The NBT payload is what makes GregTech-style packs work. A GT recipe does not say "a programmed
 * circuit", it says "a programmed circuit configured as circuit 24", and that configuration lives in NBT.
 * Dropping it would produce a pattern that happily accepts circuit 0 instead, which is a wrong pattern the
 * player only notices after the craft already happened.</p>
 */
public record StackSpec(StackType type, ResourceLocation id, @Nullable CompoundTag tag, long amount) {

    public StackSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        // Own the tag: the caller's stack may be mutated (AE2 does exactly that) after we took a reference.
        if (tag != null) {
            tag = tag.isEmpty() ? null : tag.copy();
        }
    }

    public StackSpec(StackType type, ResourceLocation id, long amount) {
        this(type, id, null, amount);
    }

    public static StackSpec of(StackType type, ResourceLocation id, long amount) {
        return new StackSpec(type, id, null, amount);
    }

    public static StackSpec item(Item item, long amount) {
        return new StackSpec(StackType.ITEM, ForgeRegistries.ITEMS.getKey(item), null, amount);
    }

    public static StackSpec item(ItemStack stack) {
        return new StackSpec(StackType.ITEM, ForgeRegistries.ITEMS.getKey(stack.getItem()),
                stack.getTag(), stack.getCount());
    }

    public static StackSpec fluid(ResourceLocation fluidId, long amount) {
        return new StackSpec(StackType.FLUID, fluidId, null, amount);
    }

    public static StackSpec fluid(net.minecraftforge.fluids.FluidStack stack) {
        ResourceLocation key = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        return new StackSpec(StackType.FLUID, key, stack.getTag(), stack.getAmount());
    }

    public boolean isItem() {
        return type == StackType.ITEM;
    }

    public boolean hasTag() {
        return tag != null && !tag.isEmpty();
    }

    public ItemStack toItemStack() {
        if (!isItem()) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, (int) Math.min(Integer.MAX_VALUE, amount));
        if (hasTag()) {
            stack.setTag(tag.copy());
        }
        return stack;
    }

    /**
     * A stable, human readable identifier for the whole stack including its NBT. Used for de-duplicating
     * requests and for search, where "same item, different NBT" must be two different things.
     */
    public String describe() {
        return hasTag() ? id.toString() + tag : id.toString();
    }

    public StackSpec withAmount(long newAmount) {
        return new StackSpec(type, id, tag, newAmount);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeResourceLocation(id);
        buf.writeNbt(tag);
        buf.writeVarLong(amount);
    }

    public static StackSpec read(FriendlyByteBuf buf) {
        StackType type = StackType.byOrdinal(buf.readByte());
        ResourceLocation id = buf.readResourceLocation();
        CompoundTag tag = buf.readNbt();
        long amount = buf.readVarLong();
        return new StackSpec(type, id, tag, amount);
    }

    @Override
    public String toString() {
        return amount + "x " + type + ":" + id + (hasTag() ? " " + tag : "");
    }
}
