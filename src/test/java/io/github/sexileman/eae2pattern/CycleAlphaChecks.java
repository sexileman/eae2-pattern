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
        var testKey=new appeng.api.stacks.AEKey(){
            public appeng.api.stacks.AEKeyType getType(){return null;}
            public appeng.api.stacks.AEKey dropSecondary(){return this;}
            public net.minecraft.nbt.CompoundTag toTag(){return new net.minecraft.nbt.CompoundTag();}
            public Object getPrimaryKey(){return this;}
            public net.minecraft.resources.ResourceLocation getId(){return new net.minecraft.resources.ResourceLocation("test:batch");}
            public void writeToPacket(net.minecraft.network.FriendlyByteBuf buffer){}
            protected net.minecraft.network.chat.Component computeDisplayName(){return net.minecraft.network.chat.Component.literal("batch");}
            public void addDrops(long amount,List<net.minecraft.world.item.ItemStack> drops,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos position){}
        };
        check(CycleLog.key(testKey).contains("NBT unavailable"),"diagnostic key serialization failure falls back without aborting crafting");
        check(CycleLog.stacks(java.util.Collections.nCopies(17,new appeng.api.stacks.GenericStack(testKey,1))).contains("[truncated]"),"diagnostic ingredient lists have a bounded output");
        var input=new appeng.api.crafting.IPatternDetails.IInput(){
            public appeng.api.stacks.GenericStack[] getPossibleInputs(){return new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(testKey,2)};}
            public long getMultiplier(){return 3;}
            public boolean isValid(appeng.api.stacks.AEKey key,net.minecraft.world.level.Level level){return true;}
            public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key){return null;}
        };
        var source=new appeng.api.crafting.IPatternDetails(){
            public appeng.api.stacks.AEItemKey getDefinition(){return null;}
            public IInput[] getInputs(){return new IInput[]{input};}
            public appeng.api.stacks.GenericStack[] getOutputs(){return new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(testKey,7)};}
        };
        var scaled=new GrowthBatchPattern(source,10000);
        check(scaled.original()==source&&scaled.getInputs()[0].getMultiplier()==30000&&scaled.getInputs()[0].getPossibleInputs()[0].amount()==2,"scaled descriptor matches all six input units per craft without changing the registered pattern");
        check(scaled.getOutputs()[0].amount()==70000&&source.getOutputs()[0].amount()==7,"batch output quota scales exactly and leaves original unchanged");
        var originalBatch=GrowthBatchBridge.prepare(plain,64);check(originalBatch.pattern()==plain&&originalBatch.times()==1,"unknown or vanilla provider path never receives artificially oversized pattern inputs");
        refused(()->GrowthBatchBridge.prepare(plain,TwoRecipeGrowth.MAX_OPERATIONS+1),"batch bridge remains bounded");
        var stages=List.of(new TwoRecipeGrowth.Stage(0,65),new TwoRecipeGrowth.Stage(1,3));
        var ledger=new GrowthLedger<String>(stages);
        check(ledger.batch()==65,"phase batch obeys bounded scaling");
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
        var independent=new GrowthLedger<String>(stages);refused(()->independent.sent(66,Map.of("B",130L)),"oversized batches are refused before changing the ledger");check(independent.batch()==65,"rejected batch does not advance progress");
        check(GrowthBatchSizing.affordable(10000,Map.of("A",1L),k->10000)==10000,"ten thousand available crafts dispatch without a 64-operation cap");
        check(GrowthBatchSizing.affordable(10000,Map.of("A",2L,"fluid",1000L),k->k.equals("A")?20000:3500)==3,"fluid stock bounds the same batch as item stock");
        check(GrowthBatchSizing.affordable(10000,Map.of("A",3L),k->8)==2,"combined duplicate-key input demand bounds affordability");
        var bigLedger=new GrowthLedger<String>(List.of(new TwoRecipeGrowth.Stage(0,10000)));
        bigLedger.sent(10000,Map.of("B",20000L));
        var bigRestored=new GrowthLedger<String>(List.of(new TwoRecipeGrowth.Stage(0,10000)),bigLedger.snapshot());
        check(bigRestored.waiting()&&bigRestored.receive("B",20000)==20000,"large in-flight batch persists exact return quotas");
        check(GrowthWorkingStock.reserve(10000,1,1000,1,1)==1001,"existing circulating stock is reserved above the minimum seed but bounded by demand");
        check(GrowthWorkingStock.reserve(20,1,1000,1,1)==20,"working capital never exceeds actual network stock");
        check(GrowthWorkingStock.reserve(1000,0,11,10,3)==6,"B working capital rounds complete crafts with original units");
        check(!GrowthGtlBatchBridge.supports(new Object()),"absent GTL or unrelated providers never enable its original-pattern bulk contract");
        var warm=TwoRecipeGrowth.plan(1,2,1,2,1001,0,11001,0);
        replay(warm,1,2,1,2,1001,0,11001,0);
        check(warm.stages().get(0).times()>1&&warm.stages().size()<large.stages().size(),"preexisting working stock permits a large first dispatch while preserving requested net output");
        var warmB=TwoRecipeGrowth.plan(1,2,1,2,0,1000,10000,1000);
        replay(warmB,1,2,1,2,0,1000,10000,1000);
        check(warmB.stages().get(0).recipe()==1&&warmB.stages().get(0).times()==1000,"a B-only working reserve is dispatched in bulk instead of a one-craft pivot");
        consumers();
        return checks;
    }
    private static void consumers(){
        var demand=GrowthDemand.consumer(11,10,2,3,1,0,1,0,1,2,1,2);
        check(demand.times()==2&&demand.demandA()==4&&demand.demandB()==6,"C demand rounds whole crafts and sums both loop inputs");
        var loop=TwoRecipeGrowth.plan(1,2,1,2,demand.initialA(),demand.initialB(),demand.goalA(),demand.goalB());
        replay(loop,1,2,1,2,demand.initialA(),demand.initialB(),demand.goalA(),demand.goalB());
        var phases=GrowthDemand.appendConsumer(loop,demand.times());var counts=GrowthDemand.counts(phases,3);
        check(phases.get(phases.size()-1).recipe()==2&&counts[2]==2,"C is scheduled only after both A and B goals are ready");
        check(loop.finalA()-demand.demandA()>=1&&loop.finalB()-demand.demandB()>=0,"C consumption leaves the protected startup stock intact");
        var budget=GrowthMaterials.budget(List.of(Map.of("A",1L,"X",3L),Map.of("B",1L,"Y",2L),Map.of("A",2L,"B",3L,"X",1L,"Z",5L)),counts,Set.of("A","B"),Map.of("A",demand.initialA()));
        check(budget.get("X")==counts[0]*3+2&&budget.get("Y")==counts[1]*2&&budget.get("Z")==10,"combined budget includes external costs from the loop and root C");
        check(budget.get("A")==demand.initialA()&&!budget.containsKey("B"),"C internal requirements are produced in the loop rather than falsely reserved twice");
        check(GrowthMaterials.missing(budget,k->k.equals("Z")?9:budget.get(k)).equals(Map.of("Z",1L)),"one missing C-only external material blocks the entire task");
        var abundantA=GrowthDemand.consumer(11,10,2,3,1,0,100,0,1,2,1,2);
        check(abundantA.initialA()==8,"existing surplus A can pay the conversions needed for B without withdrawing all stock");
        var fromA=TwoRecipeGrowth.plan(1,2,1,2,abundantA.initialA(),abundantA.initialB(),abundantA.goalA(),abundantA.goalB());
        var fromACounts=GrowthDemand.counts(GrowthDemand.appendConsumer(fromA,2),3);
        check(fromACounts[0]==3&&fromACounts[1]==0,"existing A avoids unnecessary B-to-A growth and its external cost");
        var abundantB=GrowthDemand.consumer(11,10,2,3,1,0,1,100,1,2,1,2);
        check(abundantB.initialB()==8,"existing B may supply both the consumer and conversion inputs for missing A");
        var fromB=TwoRecipeGrowth.plan(1,2,1,2,abundantB.initialA(),abundantB.initialB(),abundantB.goalA(),abundantB.goalB());
        var fromBCounts=GrowthDemand.counts(GrowthDemand.appendConsumer(fromB,2),3);
        check(fromBCounts[0]==0&&fromBCounts[1]==2,"surplus B is used without unnecessary A-to-B growth");
        var single=GrowthDemand.consumer(7,2,3,0,1,0,1,0,1,2,1,2);
        check(single.times()==4&&single.demandA()==12&&single.demandB()==0,"one-loop-input C also uses whole recipe quantities");
        refused(()->GrowthDemand.consumer(2,1,Long.MAX_VALUE,1,1,0,1,0,1,2,1,2),"C input multiplication overflow is refused");
        refused(()->GrowthDemand.consumer(1,1,0,0,1,0,1,0,1,2,1,2),"unrelated root consumer cannot be attached to a cycle");
        refused(()->GrowthDemand.counts(List.of(new TwoRecipeGrowth.Stage(2,1)),2),"legacy two-recipe jobs cannot reference a third recipe");
        refused(()->GrowthDemand.appendConsumer(loop,TwoRecipeGrowth.MAX_OPERATIONS),"root operations count toward the same execution budget");
        var ledger=new GrowthLedger<String>(phases);
        int phaseIndex=0;boolean enteredRoot=false;
        while(!ledger.done()){
            ledger.advance();if(ledger.done())break;
            int recipe=ledger.recipe();long batch=ledger.batch();String key=recipe==0?"B":recipe==1?"A":"C";
            if(recipe==2){enteredRoot=true;check(ledger.remainingOperations(0)==0&&ledger.remainingOperations(1)==0,"root phase begins only after all loop operations have completed");}
            ledger.sent(batch,Map.of(key,batch));
            var restored=new GrowthLedger<String>(phases,ledger.snapshot());
            check(restored.waiting()&&restored.recipe()==recipe&&restored.batch()==0,"three-recipe snapshots preserve in-flight stage identity");
            restored.receive(key,batch);ledger=restored;phaseIndex++;
            if(phaseIndex>1000)throw new AssertionError("consumer ledger failed to finish");
        }
        check(enteredRoot&&ledger.done()&&ledger.remainingOperations(2)==0,"consumer ledger completes only after C has physically returned");
        for(int aStock=1;aStock<=7;aStock++)for(int bStock=0;bStock<=7;bStock++)for(int aNeed=0;aNeed<=3;aNeed++)for(int bNeed=0;bNeed<=3;bNeed++)if(aNeed+bNeed>0){
            var request=GrowthDemand.consumer(13,4,aNeed,bNeed,1,0,aStock,bStock,1,2,1,2);
            var growth=TwoRecipeGrowth.plan(1,2,1,2,request.initialA(),request.initialB(),request.goalA(),request.goalB());
            check(request.initialA()<=aStock&&request.initialB()<=bStock,"consumer reservations cannot exceed actual stock");
            check(growth.finalA()>=request.demandA()+1&&growth.finalB()>=request.demandB(),"varying C demands and starting stocks satisfy both inputs plus startup reserve");
            check(GrowthDemand.counts(GrowthDemand.appendConsumer(growth,request.times()),3)[2]==4,"every C test case executes the rounded root craft count");
        }
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
