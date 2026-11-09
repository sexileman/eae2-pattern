package io.github.sexileman.eae2pattern.cycle;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Method;

/** GTL buffers index the original pattern and consume every supplied counter, without a wrapper. */
public final class GrowthGtlBatchBridge {
    private static final String BASE="org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase";
    private GrowthGtlBatchBridge(){}
    private static final class OptionalApi {
        static final Method POWER;
        static{Method method=null;try{
            method=Class.forName("org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternPower",false,GrowthGtlBatchBridge.class.getClassLoader()).getMethod("forCpu",double.class,boolean.class,long.class);
        }catch(ReflectiveOperationException|LinkageError absent){}POWER=method;}
    }
    private static final ClassValue<Boolean> SUPPORTED=new ClassValue<>(){
        protected Boolean computeValue(Class<?> type){
            try{return type.getMethod("pushPattern",IPatternDetails.class,KeyCounter[].class).getDeclaringClass().getName().equals(BASE);}
            catch(ReflectiveOperationException|LinkageError absent){return false;}
        }
    };
    public static boolean supports(Object provider){return OptionalApi.POWER!=null&&SUPPORTED.get(provider.getClass());}
    public static double power(double extractedPower,long times){
        try{return ((Number)OptionalApi.POWER.invoke(null,extractedPower,true,times)).doubleValue();}
        catch(ReflectiveOperationException|RuntimeException failure){throw new IllegalStateException("GTL cycle batch energy contract unavailable",failure);}
    }
}
