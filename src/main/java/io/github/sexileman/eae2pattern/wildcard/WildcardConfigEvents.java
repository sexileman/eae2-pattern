package io.github.sexileman.eae2pattern.wildcard;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
@Mod.EventBusSubscriber(modid=EAE2Pattern.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WildcardConfigEvents {
    private WildcardConfigEvents(){}
    @SubscribeEvent public static void reload(ModConfigEvent.Reloading event){
        if(event.getConfig().getSpec()==io.github.sexileman.eae2pattern.config.EAE2PatternConfig.SPEC){
            MachineWildcard.clearFingerprints();WildcardProviderRefresh.refresh();
        }
    }
}
