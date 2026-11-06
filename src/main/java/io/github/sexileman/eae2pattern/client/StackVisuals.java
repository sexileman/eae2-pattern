package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.IdentityHashMap;
import java.util.Map;

/** Small screen-local cache; scrolling never retains another stack for every recipe in the category. */
public final class StackVisuals {
    private final Map<StackSpec, ItemStack> items = new IdentityHashMap<>();
    public ItemStack item(StackSpec spec) {
        if (items.size() >= 512) items.clear();
        return items.computeIfAbsent(spec, StackSpec::toItemStack);
    }
    public FluidStack fluid(StackSpec spec) {
        var fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
        return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, 1, spec.tag());
    }
    public Component name(StackSpec spec) {
        if (spec.isItem()) return item(spec).isEmpty() ? Component.literal(spec.id().toString()) : item(spec).getHoverName();
        var fluid = fluid(spec);
        return fluid.isEmpty() ? Component.literal(spec.id().toString()) : fluid.getDisplayName();
    }
    public void draw(GuiGraphics g, StackSpec spec, int x, int y) {
        if (spec.isItem()) g.renderItem(item(spec), x, y);
        else {
            var fluid = fluid(spec);
            if (fluid.isEmpty()) return;
            var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
            var texture = extension.getStillTexture(fluid);
            if (texture == null) return;
            var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
            int tint = extension.getTintColor(fluid);
            g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, ((tint >>> 24) & 255) / 255f);
            try { g.blit(x, y, 0, 16, 16, sprite); }
            finally { g.setColor(1, 1, 1, 1); }
        }
        if (spec.amount() > 1) {
            String amount = spec.amount() >= 1_000_000 ? spec.amount() / 1_000_000 + "m" : spec.amount() >= 1000 ? spec.amount() / 1000 + "k" : "" + spec.amount();
            var font = Minecraft.getInstance().font;
            g.drawString(font, amount, x + 17 - font.width(amount), y + 9, 0xFFFFFFFF, true);
        }
    }
}
