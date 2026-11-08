package io.github.sexileman.eae2pattern;
import io.github.sexileman.eae2pattern.cycle.*;
import java.util.*;

/** Independent stock replay and transport-ledger boundaries for the experimental executor. */
public final class CycleAlphaChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void refused(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){rejected=true;}check(rejected,message);}
    public static int run(){
        var used=new appeng.api.stacks.KeyCounter();
        var metadata=new GrowthCraftingPlan(null,42,used,Map.of(),List.of(),List.of());
        var nativePlan=metadata.nativePlan();
        check(appeng.crafting.CraftingPlan.class.cast(nativePlan)==nativePlan,"experimental plan is the exact concrete AE2 type required by preview consumers");
        check(nativePlan.usedItems()==used&&nativePlan.bytes()==42&&!nativePlan.simulation()&&nativePlan.patternTimes().isEmpty(),"native plan preserves costs and execution fields independently of phase metadata");
        var large=TwoRecipeGrowth.plan(1,2,1,2,1,0,10001,0);
        replay(large,1,2,1,2,1,0,10001,0);
        check(large.stages().size()<40,"large target uses growth phases instead of one stage per item");
        var rounded=TwoRecipeGrowth.plan(4,3,2,3,8,0,41,0);
        replay(rounded,4,3,2,3,8,0,41,0);
        check(GrowthMaterials.startup(4,3,2)==8,"startup accounts for integer round trip rather than merely enabling one return craft");
        var counts=new long[2];for(var phase:large.stages())counts[phase.recipe()]+=phase.times();
        var budget=GrowthMaterials.budget(List.of(Map.of("A",1L,"X",3L),Map.of("B",1L,"X",2L,"fluid",1000L)),counts,Set.of("A","B"),Map.of("A",1L));
        check(budget.get("X")==counts[0]*3+counts[1]*2&&budget.get("fluid")==counts[1]*1000,"whole-job external cost includes both recipes and fluid units");
        check(budget.get("A")==1&&!budget.containsKey("B"),"circulating inputs are reserved once rather than once per operation");
        var missing=GrowthMaterials.missing(budget,key->key.equals("X")?budget.get(key)-1:budget.get(key));
        check(missing.equals(Map.of("X",1L)),"even one missing external item rejects the full budget");
        check(GrowthMaterials.missing(budget,budget::get).isEmpty(),"exact external stock is sufficient");
        refused(()->GrowthMaterials.budget(List.of(Map.of("X",Long.MAX_VALUE),Map.of("X",1L)),new long[]{1,1},Set.of(),Map.of()),"combined external quantities cannot wrap into a smaller requirement");
        check(GrowthMaterials.startup(3,2,4)==6,"startup includes enough first recipe operations to enable the return recipe");
        refused(()->TwoRecipeGrowth.plan(1,1,1,1,1,0,10,0),"zero net-growth cycles are not accepted");
        refused(()->TwoRecipeGrowth.plan(2,1,2,1,2,0,10,0),"loss cycles are not accepted");
        refused(()->TwoRecipeGrowth.plan(1,2,1,2,0,0,10,0),"no seed does not fabricate a first batch");
        refused(()->TwoRecipeGrowth.plan(1,2,1,2,1,0,10_000_000,0),"large plans have an explicit operation limit");
        refused(()->TwoRecipeGrowth.plan(1,Long.MAX_VALUE,1,2,2,0,Long.MAX_VALUE,0),"stock arithmetic overflow rejects rather than fabricating stock");
        for(int ai=1;ai<=4;ai++)for(int bo=1;bo<=4;bo++)for(int bi=1;bi<=4;bi++)for(int ao=1;ao<=4;ao++)if((long)bo*ao>(long)ai*bi){
            long seedA=GrowthMaterials.startup(ai,bo,bi),seedB=GrowthMaterials.startup(bi,ao,ai);
            replay(TwoRecipeGrowth.plan(ai,bo,bi,ao,seedA,0,seedA+37,0),ai,bo,bi,ao,seedA,0,seedA+37,0);
            replay(TwoRecipeGrowth.plan(ai,bo,bi,ao,0,seedB,37,seedB),ai,bo,bi,ao,0,seedB,37,seedB);
            replay(TwoRecipeGrowth.plan(ai,bo,bi,ao,seedA,0,seedA+17,13),ai,bo,bi,ao,seedA,0,seedA+17,13);
        }
        var plain=(appeng.api.crafting.IPatternDetails)java.lang.reflect.Proxy.newProxyInstance(CycleAlphaChecks.class.getClassLoader(),new Class[]{appeng.api.crafting.IPatternDetails.class},(object,method,args)->null);
        var originalBatch=GrowthBatchBridge.prepare(plain,64);check(originalBatch.pattern()==plain&&originalBatch.times()==1,"unknown or vanilla provider path never receives artificially oversized pattern inputs");
        refused(()->GrowthBatchBridge.prepare(plain,65),"batch bridge remains bounded");
        var stages=List.of(new TwoRecipeGrowth.Stage(0,65),new TwoRecipeGrowth.Stage(1,3));
        var ledger=new GrowthLedger<String>(stages);
        check(ledger.batch()==64,"phase batch obeys bounded scaling");
        ledger.sent(64,Map.of("B",128L,"side",64L));
        check(ledger.waiting()&&ledger.batch()==0&&ledger.remainingOperations(0)==1,"future inputs are not credited when a batch is dispatched");
        check(ledger.expected("B",1000)==128&&ledger.snapshot().awaiting().get("B")==128,"simulation does not consume a return quota");
        check(ledger.receive("B",1000)==128&&ledger.waiting(),"excess returns are rejected and every side output must return");
        var restored=new GrowthLedger<String>(stages,ledger.snapshot());
        check(restored.receive("side",63)==63&&restored.waiting(),"partial return survives restore without restarting the stage");
        restored.advance();check(restored.recipe()==0&&restored.batch()==0,"one missing physical item blocks the next batch");
        restored.receive("side",1);restored.advance();check(restored.batch()==1,"final phase remainder dispatches independently");
        restored.sent(1,Map.of("B",2L));restored.receive("B",2);restored.advance();check(restored.recipe()==1&&restored.batch()==3,"stage transition waits for all outputs");
        restored.sent(3,Map.of("A",6L));restored.receive("A",6);restored.advance();check(restored.done(),"completion requires the final batch to return");
        var done=new GrowthLedger<String>(stages,restored.snapshot());check(done.done()&&done.remainingOperations(0)==0&&done.remainingOperations(1)==0,"completed ledger restores without replaying operations");
        refused(()->new GrowthLedger<String>(stages,new GrowthLedger.Snapshot<>(0,-1,0,Map.of())),"negative persisted phase remainder is refused");
        refused(()->new GrowthLedger<String>(stages,new GrowthLedger.Snapshot<>(2,0,1,Map.of("B",1L))),"completed state cannot retain in-flight materials");
        var independent=new GrowthLedger<String>(stages);refused(()->independent.sent(65,Map.of("B",130L)),"oversized batches are refused before changing the ledger");check(independent.batch()==64,"rejected batch does not advance progress");
        return checks;
    }
    private static void replay(TwoRecipeGrowth.Result result,long ai,long bo,long bi,long ao,long seedA,long seedB,long goalA,long goalB){
        long a=seedA,b=seedB,operations=0;
        for(var stage:result.stages()){
            if(stage.recipe()==0){check(a>=ai*stage.times(),"replay can pay the entire A batch before receiving B");a-=ai*stage.times();b+=bo*stage.times();}
            else{check(b>=bi*stage.times(),"replay can pay the entire B batch before receiving A");b-=bi*stage.times();a+=ao*stage.times();}
            operations+=stage.times();
        }
        check(a>=goalA&&b>=goalB&&a==result.finalA()&&b==result.finalB()&&operations==result.operations(),"independent replay reaches every requested target without negative inventory");
    }
}
