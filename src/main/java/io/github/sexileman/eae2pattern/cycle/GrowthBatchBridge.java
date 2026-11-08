package io.github.sexileman.eae2pattern.cycle;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import java.lang.reflect.*;

/** Optional EAP batches use EAP's own wrapper/unwrap contract, never oversized vanilla inputs. */
public final class GrowthBatchBridge {
    public record Batch(IPatternDetails pattern,long times){}
    private GrowthBatchBridge(){}
    private static final class Eap {
        static final Method FACTORY;
        static{Method method=null;try{method=Class.forName("com.extendedae_plus.util.smartDoubling.PatternScaler",false,GrowthBatchBridge.class.getClassLoader()).getMethod("createScaled",AEProcessingPattern.class,long.class);}catch(ReflectiveOperationException|LinkageError absent){}FACTORY=method;}
    }
    public static Batch prepare(IPatternDetails original,long requested){
        if(requested<1||requested>64)throw new IllegalArgumentException("Invalid phase batch size");
        if(requested==1||!(original instanceof AEProcessingPattern)||Eap.FACTORY==null)return new Batch(original,1);
        try{
            if(!Boolean.TRUE.equals(original.getClass().getMethod("eap$allowScaling").invoke(original)))return new Batch(original,1);
            int limit=((Number)original.getClass().getMethod("eap$getMultiplierLimit").invoke(original)).intValue();
            if(limit<=0){var configClass=Class.forName("com.extendedae_plus.config.ModConfig",false,GrowthBatchBridge.class.getClassLoader());var config=configClass.getField("INSTANCE").get(null);limit=configClass.getField("smartScalingMaxMultiplier").getInt(config);}
            long times=limit>0?Math.min(requested,limit):requested;if(times<=1)return new Batch(original,1);
            Object scaled=Eap.FACTORY.invoke(null,original,times);
            if(scaled instanceof IPatternDetails details)return new Batch(details,times);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){/* Single original craft remains supported. */}
        return new Batch(original,1);
    }
}
