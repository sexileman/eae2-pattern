package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * The only JEI entry point of the mod.
 *
 * <p>Machine recipes come from existing categories. Our own blank carrier uses a vanilla crafting
 * recipe, and the information page explains how to configure it without an ME network.</p>
 */
@JeiPlugin
public final class EAE2PatternJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = EAE2Pattern.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }
    @Override
    public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
        registration.addIngredientInfo(io.github.sexileman.eae2pattern.wildcard.WildcardItems.MACHINE_PATTERN.get().getDefaultInstance(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                net.minecraft.network.chat.Component.translatable("eae2_pattern.editor.blank_recipe"),
                net.minecraft.network.chat.Component.translatable("eae2_pattern.editor.blank_help"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRuntimeHolder.set(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRuntimeHolder.set(null);
    }
}
