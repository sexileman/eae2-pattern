package io.github.sexileman.eae2pattern.client;

import appeng.client.gui.Icon;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Uses the same native icon-button geometry as AE2's other left-toolbar controls. */
public final class CycleToolbarButton extends IconButton {
    public CycleToolbarButton(CraftConfirmScreen screen) {
        super(button -> CyclePlanOverlay.open(screen));
    }

    @Override
    protected Icon getIcon() {
        return Icon.SUBSTITUTION_ENABLED;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable("eae2_pattern.diagram.title"));
    }
}
