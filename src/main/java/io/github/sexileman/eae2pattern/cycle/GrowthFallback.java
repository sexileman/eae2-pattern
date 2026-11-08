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

/** Opt-in fallback only for a failed root request and two unambiguous native processing patterns. */
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
            var target=original.finalOutput();var a=target.what();
            var keys=new HashSet<>(candidate.path());if(keys.size()!=2||!keys.contains(a))continue;keys.remove(a);var b=keys.iterator().next();
            var recipes=new ArrayList<IPatternDetails>(candidate.patterns());
            Slots first=read(recipes.get(0)),second=read(recipes.get(1));
            if(first.out.containsKey(a)){Collections.reverse(recipes);var temp=first;first=second;second=temp;}
            if(!first.in.containsKey(a)||first.in.containsKey(b)||!first.out.containsKey(b)||first.out.containsKey(a)
                    ||!second.in.containsKey(b)||second.in.containsKey(a)||!second.out.containsKey(a)||second.out.containsKey(b))continue;
            long seedA=0,seedB=0,aIn=first.in.get(a),bIn=second.in.get(b);
            long startA=GrowthMaterials.startup(aIn,first.out.get(b),bIn),startB=GrowthMaterials.startup(bIn,second.out.get(a),aIn);
            if(stock.extract(a,startA,Actionable.SIMULATE)>=startA)seedA=startA;
            else if(stock.extract(b,startB,Actionable.SIMULATE)>=startB)seedB=startB;
            else{reason="seed";continue;}
            var plan=TwoRecipeGrowth.plan(aIn,first.out.get(b),bIn,second.out.get(a),seedA,seedB,Math.addExact(seedA,target.amount()),seedB);
            var seeds=new HashMap<AEKey,Long>();if(seedA>0)seeds.put(a,seedA);if(seedB>0)seeds.put(b,seedB);
            var times=new long[2];for(var phase:plan.stages())times[phase.recipe()]=Math.addExact(times[phase.recipe()],phase.times());
            var budget=GrowthMaterials.budget(List.of(first.in,second.in),times,Set.of(a,b),seeds);
            for(int i=0;i<2;i++)for(var output:recipes.get(i).getOutputs())Math.multiplyExact(Math.multiplyExact(output.amount(),times[i]),output.what().getAmountPerUnit());
            var missing=GrowthMaterials.missing(budget,key->stock.extract(key,budget.get(key),Actionable.SIMULATE));
            if(!missing.isEmpty()){var entry=missing.entrySet().iterator().next();reason="external";detail=entry.getKey().getId()+" / "+entry.getValue();continue;}
            var used=new KeyCounter();budget.forEach(used::add);
            long bytes=Math.addExact(1024+64L*plan.stages().size()+64L*used.size(),Math.multiplyExact(plan.operations(),8));
            notify(level,requester,"ready",Component.literal(""+plan.stages().size()));
            return new GrowthCraftingPlan(target,bytes,used,Map.of(recipes.get(0),times[0],recipes.get(1),times[1]),recipes,plan.stages());
        }catch(IllegalArgumentException|ArithmeticException unsupported){reason="unsupported";}
        if(!capture.candidates().isEmpty())notify(level,requester,reason,Component.literal(detail));
        return original;
    }
    private static Slots read(IPatternDetails details){
        if(!(details instanceof AEProcessingPattern)||details.getDefinition()==null||!details.supportsPushInputsToExternalInventory())throw new IllegalArgumentException("Native processing patterns only");
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
