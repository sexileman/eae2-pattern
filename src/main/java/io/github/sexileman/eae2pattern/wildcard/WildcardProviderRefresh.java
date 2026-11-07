package io.github.sexileman.eae2pattern.wildcard;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=io.github.sexileman.eae2pattern.EAE2Pattern.MOD_ID)
public final class WildcardProviderRefresh {
    private static final Map<PatternProviderLogic,Boolean> PROVIDERS=new WeakHashMap<>();
    private static final ArrayDeque<java.lang.ref.WeakReference<PatternProviderLogic>> PENDING=new ArrayDeque<>();
    private WildcardProviderRefresh(){}
    public static void observe(PatternProviderLogic provider){PROVIDERS.put(provider,Boolean.TRUE);}
    public static void refresh(){PENDING.clear();for(var provider:PROVIDERS.keySet())PENDING.add(new java.lang.ref.WeakReference<>(provider));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||PENDING.isEmpty())return;
        var provider=PENDING.removeFirst().get();if(provider!=null)try{provider.updatePatterns();}catch(RuntimeException error){io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn("Wildcard provider refresh skipped: {}",error.getMessage());}
    }
    public static void clear(){PROVIDERS.clear();PENDING.clear();}
}
