package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** JEI hotkeys navigate outside our own buttons, but must retain the same return context. */
@Mod.EventBusSubscriber(modid=EAE2Pattern.MOD_ID,value=Dist.CLIENT)
public final class ToolScreenEvents {
    private ToolScreenEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event){
        if(event.getCurrentScreen() instanceof PatternWorkbenchScreen tool)tool.prepareTransition(event.getNewScreen());
    }
}
