package io.github.sexileman.eae2pattern.cycle;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import java.util.*;
/** Uses the host logger: no separate files, chat dependency, or unbounded per-tick logging. */
public final class CycleLog {
    private CycleLog(){}
    public static String key(AEKey key){
        String tag;
        try{tag=key.toTagGeneric().toString();}catch(RuntimeException|LinkageError unavailable){tag=key.getId().toString()+" [NBT unavailable]";}
        tag=tag.replace('\n',' ').replace('\r',' ');
        return tag.length()>512?tag.substring(0,512)+"...[truncated]":tag;
    }
    public static String stacks(List<GenericStack> stacks){
        return stacks.stream().limit(16).map(s->s.amount()+" x "+key(s.what())).toList()+(stacks.size()>16?" [truncated]":"");
    }
    public static String map(Map<AEKey,Long> stocks){return stacks(stocks.entrySet().stream().limit(17).map(e->new GenericStack(e.getKey(),e.getValue())).toList());}
    public static String stocks(KeyCounter stocks){
        var slots=new ArrayList<GenericStack>();for(var stack:stocks){if(slots.size()==17)break;slots.add(new GenericStack(stack.getKey(),stack.getLongValue()));}return stacks(slots);
    }
    public static void detected(ICraftingPlan plan,CycleCapture capture){
        if(capture==null||capture.witnesses().isEmpty())return;
        EAE2Pattern.LOGGER.info("[EAE2/Cycles] event=detected trace={} target={} amount={} nativeMissing={} candidates={} truncated={} experimentalExecution={}",capture.id(),key(plan.finalOutput().what()),plan.finalOutput().amount(),plan.simulation(),capture.witnesses().size(),capture.truncated(),io.github.sexileman.eae2pattern.config.EAE2PatternConfig.EXPERIMENTAL_CYCLES.get());
        int index=0;
        for(var witness:capture.witnesses()){
            EAE2Pattern.LOGGER.info("[EAE2/Cycles] event=candidate trace={} index={} path={}",capture.id(),index++,witness.path().stream().map(CycleLog::key).toList());
            for(var pattern:witness.patterns())EAE2Pattern.LOGGER.info("[EAE2/Cycles] event=recipe trace={} inputs={} outputs={} recipeTypes={}",capture.id(),stacks(pattern.inputs()),stacks(pattern.outputs()),pattern.recipeTypes());
        }
    }
}
