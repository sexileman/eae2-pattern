package io.github.sexileman.eae2pattern.cycle;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import java.lang.reflect.*;

/** Prefer EAP's scaling contract when enabled, otherwise use an independent phase descriptor. */
public final class GrowthBatchBridge {
    public record Batch(IPatternDetails pattern,long times){}
    private GrowthBatchBridge(){}
    private static final class Eap {
        static final Method FACTORY;
        static{Method method=null;try{method=Class.forName("com.extendedae_plus.util.smartDoubling.PatternScaler",false,GrowthBatchBridge.class.getClassLoader()).getMethod("createScaled",AEProcessingPattern.class,long.class);}catch(ReflectiveOperationException|LinkageError absent){}FACTORY=method;}
    }
    public static Batch prepare(IPatternDetails original,long requested){
        if(requested<1||requested>TwoRecipeGrowth.MAX_OPERATIONS)throw new IllegalArgumentException("Invalid phase batch size");
        if(requested==1||!(original instanceof AEProcessingPattern))return new Batch(original,1);
        try{
            if(Eap.FACTORY==null||!Boolean.TRUE.equals(original.getClass().getMethod("eap$allowScaling").invoke(original)))return new Batch(new GrowthBatchPattern(original,requested),requested);
            int limit=((Number)original.getClass().getMethod("eap$getMultiplierLimit").invoke(original)).intValue();
            if(limit<=0){var configClass=Class.forName("com.extendedae_plus.config.ModConfig",false,GrowthBatchBridge.class.getClassLoader());var config=configClass.getField("INSTANCE").get(null);limit=configClass.getField("smartScalingMaxMultiplier").getInt(config);}
            long times=limit>0?Math.min(requested,limit):requested;if(times<=1)return new Batch(original,1);
            Object scaled=Eap.FACTORY.invoke(null,original,times);
            if(scaled instanceof IPatternDetails details)return new Batch(details,times);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){/* Optional API absent or changed: use our native provider adapter. */}
        return new Batch(new GrowthBatchPattern(original,requested),requested);
    }
}
