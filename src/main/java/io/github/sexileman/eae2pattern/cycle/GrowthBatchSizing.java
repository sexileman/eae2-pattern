package io.github.sexileman.eae2pattern.cycle;
import java.util.Map;
import java.util.function.ToLongFunction;
/** Affordability uses real stock, with duplicate input keys already aggregated. */
public final class GrowthBatchSizing {
    private GrowthBatchSizing(){}
    public static <K> long affordable(long requested,Map<K,Long> inputs,ToLongFunction<K> stock){
        if(requested<0||requested>TwoRecipeGrowth.MAX_OPERATIONS)throw new IllegalArgumentException("Invalid cycle batch");
        long result=requested;
        for(var entry:inputs.entrySet()){
            if(entry.getValue()<=0)throw new IllegalArgumentException("Invalid cycle input");
            result=Math.min(result,Math.max(0,stock.applyAsLong(entry.getKey()))/entry.getValue());
        }
        return result;
    }
}
