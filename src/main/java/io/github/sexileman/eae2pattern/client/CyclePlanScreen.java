package io.github.sexileman.eae2pattern.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.crafting.CraftConfirmMenu;

import io.github.sexileman.eae2pattern.net.S2CCycleDiagramPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native AE2 child screen sharing the confirmation menu, like AE2CT's crafting tree. */
public final class CyclePlanScreen extends AESubScreen<CraftConfirmMenu, CraftConfirmScreen> {
    private final CyclePlanOverlay content;

    public CyclePlanScreen(CraftConfirmScreen parent) {
        super(parent, "/screens/eae2_pattern_cycle.json");
        content = new CyclePlanOverlay(this);
        var icon = getMenu().getHost().getMainMenuIcon();
        widgets.add(
                "back",
                new TabButton(
                        icon, Component.translatable("gui.back"), button -> returnToParent()));
    }

    void accept(S2CCycleDiagramPacket packet) {
        content.acceptPacket(packet);
    }

    public java.util.Optional<AeToolScreen.IngredientArea> jeiIngredientAt(double x, double y) {
        return content.jeiIngredientAt(x, y);
    }

    @Override
    public void drawFG(GuiGraphics g, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(g, offsetX, offsetY, mouseX, mouseY);
        g.pose().pushPose();
        try {
            g.pose().translate(-offsetX, -offsetY, 0);
            content.render(g, mouseX, mouseY);
        } finally {
            g.pose().popPose();
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        content.tick();
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return content.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return content.mouseScrolled(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return content.mouseDragged(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        content.mouseReleased();
        return super.mouseReleased(x, y, button);
    }
}
