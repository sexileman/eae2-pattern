package io.github.sexileman.eae2pattern.cycle;
import java.util.*;
/** Pure phase ledger: a next phase cannot consume an output that has not physically returned. */
public final class GrowthLedger<K> {
    public record Snapshot<K>(int stage,long remaining,long inFlight,Map<K,Long> awaiting){public Snapshot{awaiting=Map.copyOf(awaiting);}}
    private final List<TwoRecipeGrowth.Stage> stages;
    private final Map<K,Long> awaiting=new HashMap<>();
    private int stage;private long remaining,inFlight;
    public GrowthLedger(List<TwoRecipeGrowth.Stage> stages){this(stages,new Snapshot<>(0,stages.isEmpty()?0:stages.get(0).times(),0,Map.of()));}
    public GrowthLedger(List<TwoRecipeGrowth.Stage> stages,Snapshot<K> state){
        if(stages.isEmpty()||stages.size()>TwoRecipeGrowth.MAX_STAGES)throw new IllegalArgumentException("Invalid cycle phases");
        this.stages=List.copyOf(stages);stage=state.stage();remaining=state.remaining();inFlight=state.inFlight();
        long operations=0;for(var phase:stages)operations=Math.addExact(operations,phase.times());
        if(operations>TwoRecipeGrowth.MAX_OPERATIONS||stage<0||stage>stages.size()||remaining<0||inFlight<0||inFlight>64
                ||(stage==stages.size()?remaining!=0||!state.awaiting().isEmpty():remaining>stages.get(stage).times())
                ||state.awaiting().size()>64||(!state.awaiting().isEmpty()&&inFlight==0)||state.awaiting().values().stream().anyMatch(n->n<=0))throw new IllegalArgumentException("Invalid cycle ledger");
        awaiting.putAll(state.awaiting());
    }
    public boolean waiting(){return !awaiting.isEmpty();}
    public void advance(){if(waiting())return;while(stage<stages.size()&&remaining==0){stage++;if(stage<stages.size())remaining=stages.get(stage).times();}}
    public boolean done(){return stage==stages.size();}
    public int recipe(){if(done())throw new IllegalStateException("Cycle is complete");return stages.get(stage).recipe();}
    public long batch(){return waiting()||done()?0:Math.min(remaining,64);}
    public void sent(long times,Map<K,Long> outputs){
        if(times<=0||times>batch()||outputs.isEmpty()||outputs.size()>64||outputs.values().stream().anyMatch(n->n<=0))throw new IllegalArgumentException("Invalid phase dispatch");
        remaining-=times;inFlight=times;awaiting.putAll(outputs);
    }
    public long expected(K key,long amount){return amount<=0?0:Math.min(amount,awaiting.getOrDefault(key,0L));}
    public long receive(K key,long amount){long accepted=expected(key,amount);if(accepted>0){long rest=awaiting.get(key)-accepted;if(rest==0)awaiting.remove(key);else awaiting.put(key,rest);}return accepted;}
    public Snapshot<K> snapshot(){return new Snapshot<>(stage,remaining,inFlight,awaiting);}
    public long remainingOperations(int recipe){long total=0;for(int i=stage;i<stages.size();i++)if(stages.get(i).recipe()==recipe)total=Math.addExact(total,i==stage?remaining:stages.get(i).times());return total;}
}
