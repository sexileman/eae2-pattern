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
        String amount = StackAmountLabel.format(spec.amount(), !spec.isItem());
        if (!amount.isEmpty()) {
            var font = Minecraft.getInstance().font;
            // Item models render above the GUI plane. Match vanilla decorations' foreground depth,
            // and keep long labels within their own slot rather than spilling onto adjacent icons.
            float scale = Math.min(1f, 16f / Math.max(1, font.width(amount)));
            g.pose().pushPose();
            try {
                g.pose().translate(x + 17, y + 17, 200);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, amount, -font.width(amount), -font.lineHeight, 0xFFFFFFFF, true);
            } finally { g.pose().popPose(); }
        }
    }

    /** JEI-style rule annotation: small text at the top, quantities stay at the bottom. */
    public void drawRule(GuiGraphics g, IngredientPresentation presentation, int x, int y) {
        String label = switch (presentation.kind()) {
            case NOT_CONSUMED -> Component.translatable("eae2_pattern.workbench.slot_overlay_not_consumed").getString();
            case CHANCE_INPUT, CHANCE_OUTPUT -> presentation.chance();
            case MIXED -> "?";
            default -> "";
        };
        if (label.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        float scale = Math.min(.5f, 16f / Math.max(1, font.width(label)));
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 210);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, label, 0, 0, presentation.color(), true);
        } finally { g.pose().popPose(); }
    }
}
