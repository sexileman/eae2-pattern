package io.github.sexileman.eae2pattern.cycle;
import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;
import io.github.sexileman.eae2pattern.mixin.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** One in-flight phase batch, isolated in the native CPU inventory. Never global stock polling. */
public final class GrowthRun {
    public static final String TAG="eae2_growth_alpha";
    private final List<IPatternDetails> recipes;
    private final List<TwoRecipeGrowth.Stage> stages;
    private GrowthLedger<AEKey> ledger;
    private final long[] batchLimits={TwoRecipeGrowth.MAX_OPERATIONS,TwoRecipeGrowth.MAX_OPERATIONS,TwoRecipeGrowth.MAX_OPERATIONS};
    public GrowthRun(GrowthCraftingPlan plan){recipes=plan.recipes();stages=plan.stages();GrowthDemand.counts(stages,recipes.size());ledger=new GrowthLedger<>(stages);}
    private GrowthRun(List<IPatternDetails> recipes,List<TwoRecipeGrowth.Stage> stages){this.recipes=List.copyOf(recipes);this.stages=List.copyOf(stages);GrowthDemand.counts(stages,recipes.size());ledger=new GrowthLedger<>(stages);}
    private static GrowthJobAccessor job(GrowthCpuView cpu){return (GrowthJobAccessor)(Object)cpu.eae2$job();}
    public int step(GrowthCpuView cpu,int max,CraftingService service,IEnergyService energy,Level level){
        if(max<1||ledger.waiting())return 0;
        ledger.advance();
        if(ledger.done()){deliver(cpu);return 0;}
        var job=job(cpu);var pattern=recipes.get(ledger.recipe());
        var perCraft=new HashMap<AEKey,Long>();
        for(var input:pattern.getInputs()){
            var options=input.getPossibleInputs();
            if(options.length!=1)throw new IllegalStateException("Cycle input alternatives changed");
            perCraft.merge(options[0].what(),Math.multiplyExact(options[0].amount(),input.getMultiplier()),Math::addExact);
        }
        long affordable=GrowthBatchSizing.affordable(Math.min(ledger.batch(),batchLimits[ledger.recipe()]),perCraft,
                key->cpu.eae2$inventory().extract(key,Long.MAX_VALUE,Actionable.SIMULATE));
        if(affordable==0)return 0;
        var dispatch=GrowthBatchBridge.prepare(pattern,affordable);
        long batch=dispatch.times();var progress=(GrowthProgressAccessor)job.eae2$tasks().get(pattern);
        if(progress==null||progress.eae2$value()<batch)throw new IllegalStateException("Cycle task counts disagree");
        var outputs=new KeyCounter();var containers=new KeyCounter();
        var inputs=CraftingCpuHelper.extractPatternInputs(dispatch.pattern(),cpu.eae2$inventory(),level,outputs,containers);
        if(inputs==null)return 0;
        boolean sent=false,attempted=false;
        try{
            double power=CraftingCpuHelper.calculatePatternPower(inputs);
            if(!Double.isFinite(power))throw new IllegalStateException("Invalid cycle energy");
            if(energy.extractAEPower(power,Actionable.SIMULATE,PowerMultiplier.CONFIG)<power-.01){batchLimits[ledger.recipe()]=Math.max(1,batch/2);return 0;}
            int checked=0;
            for(var provider:service.getProviders(pattern)){
                if(++checked>256)break;
                if(provider.isBusy())continue;
                attempted=true;
                if(provider.pushPattern(dispatch.pattern(),inputs)){
                    sent=true;batchLimits[ledger.recipe()]=TwoRecipeGrowth.MAX_OPERATIONS;energy.extractAEPower(power,Actionable.MODULATE,PowerMultiplier.CONFIG);
                    var expected=new HashMap<AEKey,Long>();for(var output:outputs)expected.put(output.getKey(),output.getLongValue());ledger.sent(batch,expected);progress.eae2$value(progress.eae2$value()-batch);
                    if(progress.eae2$value()==0)job.eae2$tasks().remove(pattern);
                    for(var output:outputs){job.eae2$waiting().insert(output.getKey(),output.getLongValue(),Actionable.MODULATE);}
                    cpu.eae2$cluster().markDirty();return 1;
                }
            }
            return 0;
        }finally{if(!sent){CraftingCpuHelper.reinjectPatternInputs(cpu.eae2$inventory(),inputs);if(attempted)batchLimits[ledger.recipe()]=Math.max(1,batch/2);}}
    }
    public long accept(GrowthCpuView cpu,AEKey key,long amount,Actionable action){
        if(amount<=0)return 0;var job=job(cpu);
        long accepted=Math.min(amount,Math.min(ledger.expected(key,amount),job.eae2$waiting().extract(key,amount,Actionable.SIMULATE)));
        if(accepted<=0)return 0;
        if(action==Actionable.MODULATE){
            ledger.receive(key,accepted);job.eae2$waiting().extract(key,accepted,Actionable.MODULATE);
            cpu.eae2$inventory().insert(key,accepted,Actionable.MODULATE);
            ((GrowthTrackerInvoker)(Object)job.eae2$tracker()).eae2$decrement(accepted,key.getType());cpu.eae2$cluster().markDirty();
        }
        return accepted;
    }
    private void deliver(GrowthCpuView cpu){
        var job=job(cpu);var key=job.eae2$output().what();long needed=job.eae2$remaining();
        long held=cpu.eae2$inventory().extract(key,needed,Actionable.MODULATE);if(held<=0)return;
        long inserted=0;
        try{
            if(job.eae2$link().isStandalone()){
                var grid=cpu.eae2$cluster().getGrid();if(grid==null)return;
                inserted=grid.getStorageService().getInventory().insert(key,held,Actionable.MODULATE,cpu.eae2$cluster().getSrc());
            }else inserted=job.eae2$link().insert(key,held,Actionable.MODULATE);
            if(inserted<0||inserted>held)throw new IllegalStateException("Invalid output acceptance");
        }finally{if(inserted>=0&&inserted<=held)cpu.eae2$inventory().insert(key,held-inserted,Actionable.MODULATE);}
        if(cpu.eae2$job()==null)return;
        job.eae2$remaining(needed-inserted);cpu.eae2$cluster().markDirty();
        if(job.eae2$remaining()==0){cpu.eae2$finish(true);cpu.eae2$cluster().updateOutput(null);}
        else cpu.eae2$cluster().updateOutput(new GenericStack(key,job.eae2$remaining()));
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("version",recipes.size()==2?1:2);var definitions=new ListTag();for(var recipe:recipes)definitions.add(recipe.getDefinition().toTag());tag.put("recipes",definitions);
        var phases=new ListTag();for(var phase:stages){var row=new CompoundTag();row.putInt("recipe",phase.recipe());row.putLong("times",phase.times());phases.add(row);}tag.put("stages",phases);
        var state=ledger.snapshot();tag.putInt("stage",state.stage());tag.putLong("remaining",state.remaining());tag.putLong("in_flight",state.inFlight());
        var waiting=new ListTag();for(var entry:state.awaiting().entrySet())waiting.add(GenericStack.writeTag(new GenericStack(entry.getKey(),entry.getValue())));tag.put("awaiting",waiting);
        return tag;
    }
    public static GrowthRun load(CompoundTag tag,GrowthCpuView cpu){
        int version=tag.getInt("version");var definitions=tag.getList("recipes",10);
        if(!((version==1&&definitions.size()==2)||(version==2&&definitions.size()==3)))throw new IllegalArgumentException("Unknown cycle state or invalid recipe count");
        var recipes=new ArrayList<IPatternDetails>();for(int i=0;i<definitions.size();i++){
            var key=AEItemKey.fromTag(definitions.getCompound(i));var details=key==null?null:PatternDetailsHelper.decodePattern(key,cpu.eae2$cluster().getLevel());
            if(!(details instanceof appeng.crafting.pattern.AEProcessingPattern)&&!(i==2&&details instanceof appeng.crafting.pattern.AECraftingPattern))throw new IllegalArgumentException("Cycle recipe unavailable");recipes.add(details);
        }
        var phases=tag.getList("stages",10);if(phases.isEmpty()||phases.size()>TwoRecipeGrowth.MAX_STAGES)throw new IllegalArgumentException("Invalid phase count");
        var stages=new ArrayList<TwoRecipeGrowth.Stage>();long count=0;
        for(int i=0;i<phases.size();i++){var row=phases.getCompound(i);var phase=new TwoRecipeGrowth.Stage(row.getInt("recipe"),row.getLong("times"));count=Math.addExact(count,phase.times());if(count>TwoRecipeGrowth.MAX_OPERATIONS)throw new IllegalArgumentException("Too many cycle operations");stages.add(phase);}
        var result=new GrowthRun(recipes,stages);
        int stage=tag.getInt("stage");long remaining=tag.getLong("remaining"),inFlight=tag.getLong("in_flight");
        var waiting=tag.getList("awaiting",10);if(waiting.size()>64||stage<0||stage>stages.size()||inFlight<0||inFlight>TwoRecipeGrowth.MAX_OPERATIONS)throw new IllegalArgumentException("Invalid pending phase");
        var allowed=new KeyCounter();if(stage<stages.size())for(var output:recipes.get(stages.get(stage).recipe()).getOutputs())allowed.add(output.what(),Math.multiplyExact(output.amount(),inFlight));
        var awaiting=new HashMap<AEKey,Long>();
        for(int i=0;i<waiting.size();i++){var stack=GenericStack.readTag(waiting.getCompound(i));if(stack==null||stack.amount()<=0||stack.amount()>allowed.get(stack.what())||awaiting.putIfAbsent(stack.what(),stack.amount())!=null)throw new IllegalArgumentException("Invalid pending cycle output");}
        result.ledger=new GrowthLedger<>(stages,new GrowthLedger.Snapshot<>(stage,remaining,inFlight,awaiting));
        for(var entry:awaiting.entrySet())if(job(cpu).eae2$waiting().extract(entry.getKey(),Long.MAX_VALUE,Actionable.SIMULATE)!=entry.getValue())throw new IllegalArgumentException("Cycle wait counts disagree");
        for(var entry:job(cpu).eae2$waiting().list)if(entry.getLongValue()>0&&awaiting.getOrDefault(entry.getKey(),0L)!=entry.getLongValue())throw new IllegalArgumentException("Unexpected native cycle wait");
        for(int i=0;i<recipes.size();i++){var progress=job(cpu).eae2$tasks().get(recipes.get(i));long value=progress==null?0:((GrowthProgressAccessor)progress).eae2$value();if(value!=result.ledger.remainingOperations(i))throw new IllegalArgumentException("Cycle task counts disagree");}
        return result;
    }
}
