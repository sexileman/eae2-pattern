package io.github.sexileman.eae2pattern.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.crafting.CraftConfirmMenu;

import io.github.sexileman.eae2pattern.client.CyclePlanOverlay;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CycleConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    protected CycleConfirmScreenMixin(
            CraftConfirmMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void eae2$cycleButton(
            CraftConfirmMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style,
            CallbackInfo ci) {
        var button =
                new TabButton(
                        Icon.SUBSTITUTION_ENABLED,
                        Component.translatable("eae2_pattern.diagram.title"),
                        b -> CyclePlanOverlay.open((CraftConfirmScreen) (Object) this));
        button.setStyle(TabButton.Style.BOX);
        addToLeftToolbar(button);
    }
}
