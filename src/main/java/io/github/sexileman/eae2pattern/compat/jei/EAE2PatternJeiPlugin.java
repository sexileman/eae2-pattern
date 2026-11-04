package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * The only JEI entry point of the mod.
 *
 * <p>We register no categories and no recipes: the whole point is to *read* what other mods already taught JEI.
 * All this plugin does is capture the runtime handle.</p>
 */
@JeiPlugin
public final class EAE2PatternJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = EAE2Pattern.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
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
