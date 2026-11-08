package io.github.sexileman.eae2pattern.cycle;
import java.math.BigInteger;
import java.util.*;

/** Integer-complete growth rounds avoid rounding a positive ratio into a deadlocked half-round. */
public final class TwoRecipeGrowth {
    public static final int MAX_STAGES=512;
    public static final long MAX_OPERATIONS=1_000_000;
    public record Stage(int recipe,long times){public Stage{if((recipe!=0&&recipe!=1)||times<=0)throw new IllegalArgumentException("Invalid cycle stage");}}
    public record Result(List<Stage> stages,long operations,long finalA,long finalB){public Result{stages=List.copyOf(stages);}}
    private TwoRecipeGrowth(){}
    public static Result plan(long aIn,long bOut,long bIn,long aOut,long seedA,long seedB,long targetA,long targetB){
        if(aIn<=0||bOut<=0||bIn<=0||aOut<=0||seedA<0||seedB<0||targetA<0||targetB<0)throw new IllegalArgumentException("Invalid cycle quantities");
        if(BigInteger.valueOf(bOut).multiply(BigInteger.valueOf(aOut)).compareTo(BigInteger.valueOf(aIn).multiply(BigInteger.valueOf(bIn)))<=0)throw new IllegalArgumentException("Cycle is not strictly growing");
        long gB=gcd(bOut,bIn),aSteps=bIn/gB,bSteps=bOut/gB;
        long aSeed=Math.multiplyExact(aIn,aSteps),aGain=Math.subtractExact(Math.multiplyExact(aOut,bSteps),aSeed);
        long gA=gcd(aOut,aIn),reverseB=aIn/gA,reverseA=aOut/gA;
        long bSeed=Math.multiplyExact(bIn,reverseB),bGain=Math.subtractExact(Math.multiplyExact(bOut,reverseA),bSeed);
        var state=new State(aIn,bOut,bIn,aOut,seedA,seedB);
        while(state.a<targetA||state.b<targetB){
            long finalBSteps=ceil(Math.max(0,targetB-state.b),bOut);
            long neededA=Math.addExact(targetA,Math.multiplyExact(finalBSteps,aIn));
            if(state.a>=neededA){if(finalBSteps>0)state.add(0,finalBSteps);break;}
            if(state.a>=aSeed){
                long rounds=Math.min(state.a/aSeed,ceil(neededA-state.a,aGain));
                state.add(0,Math.multiplyExact(rounds,aSteps));state.add(1,Math.multiplyExact(rounds,bSteps));
            }else{
                long pivot=ceil(aSeed-state.a,aOut),neededB=Math.multiplyExact(pivot,bIn);
                if(state.b>=neededB)state.add(1,pivot);
                else if(state.b>=bSeed){
                    long rounds=Math.min(state.b/bSeed,ceil(neededB-state.b,bGain));
                    state.add(1,Math.multiplyExact(rounds,reverseB));state.add(0,Math.multiplyExact(rounds,reverseA));
                }else throw new IllegalArgumentException("Insufficient stock for an integer-complete startup round");
            }
        }
        return new Result(state.stages,state.operations,state.a,state.b);
    }
    private static final class State {
        final long ai,bo,bi,ao;long a,b,operations;final List<Stage> stages=new ArrayList<>();
        State(long ai,long bo,long bi,long ao,long a,long b){this.ai=ai;this.bo=bo;this.bi=bi;this.ao=ao;this.a=a;this.b=b;}
        void add(int recipe,long times){
            if(times<=0||stages.size()>=MAX_STAGES)throw new IllegalArgumentException("Cycle stage limit reached");
            operations=Math.addExact(operations,times);if(operations>MAX_OPERATIONS)throw new IllegalArgumentException("Cycle operation limit reached");
            if(recipe==0){long spent=Math.multiplyExact(ai,times);if(a<spent)throw new IllegalArgumentException("Insufficient A");a-=spent;b=Math.addExact(b,Math.multiplyExact(bo,times));}
            else{long spent=Math.multiplyExact(bi,times);if(b<spent)throw new IllegalArgumentException("Insufficient B");b-=spent;a=Math.addExact(a,Math.multiplyExact(ao,times));}
            stages.add(new Stage(recipe,times));
        }
    }
    public static long gcd(long a,long b){while(b!=0){long next=a%b;a=b;b=next;}return a;}
    private static long ceil(long amount,long unit){return amount/unit+(amount%unit==0?0:1);}
}
