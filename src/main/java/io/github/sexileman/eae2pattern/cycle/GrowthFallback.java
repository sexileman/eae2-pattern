package io.github.sexileman.eae2pattern.cycle;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

/** Failed root request: direct growth material or one native root consumer of a two-material loop. */
public final class GrowthFallback {
    private GrowthFallback(){}
    private record Slots(Map<AEKey,Long> in,Map<AEKey,Long> out){}
    public static ICraftingPlan replace(Level level,ICraftingSimulationRequester requester,ICraftingInventory stock,ICraftingPlan original,CycleCapture capture){
        if(!CycleRuntimeGate.supported||!EAE2PatternConfig.EXPERIMENTAL_CYCLES.get()||!original.simulation()||capture==null)return original;
        var node=requester.getGridNode();if(node==null)return original;
        for(var cpu:node.getGrid().getCraftingService().getCpus())if(!(cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster)){
            notify(level,requester,"cpu",Component.empty());return original;
        }
        String reason="unsupported";String detail="";
        for(var candidate:capture.candidates())try{
            if(candidate.patterns().size()!=2||candidate.path().size()!=3)continue;
            var target=original.finalOutput();
            var keys=new HashSet<>(candidate.path());if(keys.size()!=2)continue;
            boolean downstream=!keys.contains(target.what());
            if(downstream&&candidate.upstream().size()!=1)continue;
            var a=downstream?candidate.path().get(0):target.what();keys.remove(a);var b=keys.iterator().next();
            var recipes=new ArrayList<IPatternDetails>(candidate.patterns());
            Slots first=read(recipes.get(0),false),second=read(recipes.get(1),false);
            if(first.out.containsKey(a)){Collections.reverse(recipes);var temp=first;first=second;second=temp;}
            if(!first.in.containsKey(a)||first.in.containsKey(b)||!first.out.containsKey(b)||first.out.containsKey(a)
                    ||!second.in.containsKey(b)||second.in.containsKey(a)||!second.out.containsKey(a)||second.out.containsKey(b))continue;
            long seedA=0,seedB=0,aIn=first.in.get(a),bIn=second.in.get(b);
            long startA=GrowthMaterials.startup(aIn,first.out.get(b),bIn),startB=GrowthMaterials.startup(bIn,second.out.get(a),aIn);
            if(stock.extract(a,startA,Actionable.SIMULATE)>=startA)seedA=startA;
            else if(stock.extract(b,startB,Actionable.SIMULATE)>=startB)seedB=startB;
            else{reason="seed";continue;}
            var inputMaps=new ArrayList<Map<AEKey,Long>>();inputMaps.add(first.in);inputMaps.add(second.in);
            List<TwoRecipeGrowth.Stage> phases;long initialA=seedA,initialB=seedB;
            if(downstream){
                var terminal=candidate.upstream().get(0);if(recipes.contains(terminal))continue;
                var root=read(terminal,true);Long output=root.out.get(target.what());
                if(output==null||root.in.containsKey(target.what()))continue;
                long availableA=stock.extract(a,Long.MAX_VALUE,Actionable.SIMULATE),availableB=stock.extract(b,Long.MAX_VALUE,Actionable.SIMULATE);
                var demand=GrowthDemand.consumer(target.amount(),output,root.in.getOrDefault(a,0L),root.in.getOrDefault(b,0L),seedA,seedB,
                    availableA,availableB,aIn,first.out.get(b),bIn,second.out.get(a));
                initialA=demand.initialA();initialB=demand.initialB();
                var loop=TwoRecipeGrowth.plan(aIn,first.out.get(b),bIn,second.out.get(a),initialA,initialB,demand.goalA(),demand.goalB());
                phases=GrowthDemand.appendConsumer(loop,demand.times());recipes.add(terminal);inputMaps.add(root.in);
            }else{
                phases=TwoRecipeGrowth.plan(aIn,first.out.get(b),bIn,second.out.get(a),seedA,seedB,Math.addExact(seedA,target.amount()),seedB).stages();
            }
            var seeds=new HashMap<AEKey,Long>();if(initialA>0)seeds.put(a,initialA);if(initialB>0)seeds.put(b,initialB);
            var times=GrowthDemand.counts(phases,recipes.size());
            var budget=GrowthMaterials.budget(inputMaps,times,Set.of(a,b),seeds);
            var tasks=new LinkedHashMap<IPatternDetails,Long>();long operations=0;
            for(int i=0;i<recipes.size();i++){
                if(times[i]>0)tasks.put(recipes.get(i),times[i]);operations=Math.addExact(operations,times[i]);
                for(var output:recipes.get(i).getOutputs())Math.multiplyExact(Math.multiplyExact(output.amount(),times[i]),output.what().getAmountPerUnit());
            }
            var missing=GrowthMaterials.missing(budget,key->stock.extract(key,budget.get(key),Actionable.SIMULATE));
            if(!missing.isEmpty()){var entry=missing.entrySet().iterator().next();reason="external";detail=entry.getKey().getId()+" / "+entry.getValue();continue;}
            var used=new KeyCounter();budget.forEach(used::add);
            long bytes=Math.addExact(1024+64L*phases.size()+64L*used.size(),Math.multiplyExact(operations,8));
            var growth=new GrowthCraftingPlan(target,bytes,used,tasks,recipes,phases);
            var nativePlan=growth.nativePlan();
            if(!((Object)nativePlan instanceof GrowthPlanView attached))throw new IllegalStateException("Native cycle plan attachment unavailable");
            attached.eae2$growthPlan(growth);
            notify(level,requester,"ready",Component.literal(""+phases.size()));
            return nativePlan;
        }catch(IllegalArgumentException|ArithmeticException unsupported){reason="unsupported";}
        if(!capture.candidates().isEmpty())notify(level,requester,reason,Component.literal(detail));
        return original;
    }
    private static Slots read(IPatternDetails details,boolean terminal){
        if((!(details instanceof AEProcessingPattern)&&!(terminal&&details instanceof appeng.crafting.pattern.AECraftingPattern))||details.getDefinition()==null
                ||(!terminal&&!details.supportsPushInputsToExternalInventory()))throw new IllegalArgumentException("Unsupported native recipe type");
        if(details.getInputs().length>64||details.getOutputs().length>64)throw new IllegalArgumentException("Too many slots");
        var in=new HashMap<AEKey,Long>();var out=new HashMap<AEKey,Long>();
        for(var input:details.getInputs()){
            var choices=input.getPossibleInputs();if(choices.length!=1||choices[0].amount()<=0||input.getMultiplier()<=0||input.getRemainingKey(choices[0].what())!=null)throw new IllegalArgumentException("Ambiguous or returning input");
            in.merge(choices[0].what(),Math.multiplyExact(choices[0].amount(),input.getMultiplier()),Math::addExact);
        }
        for(var output:details.getOutputs()){if(output.amount()<=0)throw new IllegalArgumentException("Invalid output");out.merge(output.what(),output.amount(),Math::addExact);}
        return new Slots(Map.copyOf(in),Map.copyOf(out));
    }
    public static void notify(Level level,ICraftingSimulationRequester requester,String key,Component detail){
        if(level.getServer()==null)return;var actor=requester.getActionSource().player().orElse(null);
        if(actor instanceof ServerPlayer player){var id=player.getUUID();level.getServer().execute(()->{var online=level.getServer().getPlayerList().getPlayer(id);if(online!=null)online.displayClientMessage(Component.translatable("eae2_pattern.cycle.alpha."+key,detail),false);});}
    }
}
