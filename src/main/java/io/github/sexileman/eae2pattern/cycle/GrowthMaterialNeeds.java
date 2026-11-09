package io.github.sexileman.eae2pattern.cycle;
import java.util.*;
import java.util.function.ToLongFunction;
/** Whole-job material shortfalls; never sums circulating A/B once per loop operation. */
public final class GrowthMaterialNeeds {
    public record Need<K>(K key,long required,long available,long missing,boolean circulating){}
    private GrowthMaterialNeeds(){}
    public static <K> List<Need<K>> evaluate(Map<K,Long> budget,Set<K> circulating,ToLongFunction<K> stock){
        var result=new ArrayList<Need<K>>();
        for(var entry:budget.entrySet()){
            if(entry.getValue()<=0)throw new IllegalArgumentException("Invalid material demand");
            long available=Math.max(0,stock.applyAsLong(entry.getKey()));
            long missing=available>=entry.getValue()?0:entry.getValue()-available;
            result.add(new Need<>(entry.getKey(),entry.getValue(),available,missing,circulating.contains(entry.getKey())));
        }
        result.sort(Comparator.comparing((Need<K> n)->n.missing()==0));return List.copyOf(result);
    }
}
