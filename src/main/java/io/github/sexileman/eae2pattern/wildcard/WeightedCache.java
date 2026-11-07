package io.github.sexileman.eae2pattern.wildcard;
import java.util.*;
/** Access-ordered cache with explicit retained-data budget; oversized entries are never retained. */
public final class WeightedCache<K,V> {
    private record Entry<V>(V value, int weight) {}
    private final LinkedHashMap<K,Entry<V>> entries = new LinkedHashMap<>(16,.75f,true);
    private final int maxEntries, maxWeight; private int weight;
    public WeightedCache(int maxEntries,int maxWeight) { this.maxEntries=maxEntries;this.maxWeight=maxWeight; }
    public synchronized V get(K key) { var e=entries.get(key); return e==null?null:e.value(); }
    public synchronized void put(K key,V value,int cost) {
        var old=entries.remove(key);if(old!=null)weight-=old.weight();
        if(cost<0||cost>maxWeight)return;
        entries.put(key,new Entry<>(value,cost));weight+=cost;
        var it=entries.entrySet().iterator();while(entries.size()>maxEntries||weight>maxWeight){weight-=it.next().getValue().weight();it.remove();}
    }
    public synchronized void clear(){entries.clear();weight=0;}
    public synchronized int size(){return entries.size();}
    public synchronized int weight(){return weight;}
}
