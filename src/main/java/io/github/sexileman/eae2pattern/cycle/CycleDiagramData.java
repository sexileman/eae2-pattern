package io.github.sexileman.eae2pattern.cycle;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import java.util.*;
/** Bounded read-only snapshot of the exact confirmation plan, never the last player report. */
public final class CycleDiagramData {
    public static final int MAX_BYTES=262144;
    private CycleDiagramData(){}
    public static CompoundTag status(String state){var tag=new CompoundTag();tag.putString("status",state);return tag;}
    public static ListTag stacks(List<GenericStack> stacks){return stacks(stacks,64);}
    private static ListTag stacks(List<GenericStack> stacks,int max){var list=new ListTag();for(var stack:stacks.stream().limit(max).toList())list.add(GenericStack.writeTag(stack));return list;}
    public static CompoundTag build(ICraftingPlan plan,CycleCapture capture){
        var growth=plan instanceof GrowthCraftingPlan internal?internal:(Object)plan instanceof GrowthPlanView view?view.eae2$growthPlan():null;
        if(growth==null&&(capture==null||capture.witnesses().isEmpty()))return status("none");
        var tag=status(growth!=null?"planned":capture!=null&&!capture.witnesses().isEmpty()?"detected":"none");
        tag.put("target",GenericStack.writeTag(plan.finalOutput()));tag.putLong("bytes",plan.bytes());
        tag.putBoolean("missing",plan.simulation());
        if(capture!=null&&capture.materials()!=null){
            var report=capture.materials();var needs=new ListTag();
            for(var need:report.rows().stream().limit(192).toList()){
                var row=new CompoundTag();row.put("stack",GenericStack.writeTag(new GenericStack(need.key(),need.required())));
                row.putLong("required",need.required());row.putLong("available",need.available());row.putLong("missing",need.missing());row.putBoolean("circulating",need.circulating());needs.add(row);
            }
            tag.put("material_needs",needs);tag.putString("material_reason",report.reason());tag.putBoolean("seed_alternatives",report.alternatives());
            tag.put("material_path",stacks(report.path().stream().limit(9).map(k->new GenericStack(k,1)).toList()));
        }
        var paths=new ListTag();var recipes=new ListTag();var phases=new ListTag();
        if(capture!=null){
            tag.putBoolean("limited",capture.truncated());
            for(var witness:capture.witnesses().stream().limit(8).toList()){
                var row=new CompoundTag();row.put("nodes",stacks(witness.path().stream().limit(9).map(k->new GenericStack(k,1)).toList()));paths.add(row);
                if(growth==null)for(var recipe:witness.patterns().stream().limit(4).toList()){
                    var entry=new CompoundTag();entry.put("in",stacks(recipe.inputs()));entry.put("out",stacks(recipe.outputs()));recipes.add(entry);
                }
            }
        }
        if(growth!=null){
            for(var recipe:growth.recipes()){
                var in=new ArrayList<GenericStack>();for(var input:recipe.getInputs()){var choice=input.getPossibleInputs()[0];in.add(new GenericStack(choice.what(),Math.multiplyExact(choice.amount(),input.getMultiplier())));}
                var row=new CompoundTag();row.put("in",stacks(in));row.put("out",stacks(List.of(recipe.getOutputs())));recipes.add(row);
            }
            for(var stage:growth.stages()){var row=new CompoundTag();row.putInt("recipe",stage.recipe());row.putLong("times",stage.times());phases.add(row);}
        }
        var reserved=new ArrayList<GenericStack>();if(growth!=null)for(var entry:plan.usedItems())if(reserved.size()<192)reserved.add(new GenericStack(entry.getKey(),entry.getLongValue()));
        tag.put("reserved",stacks(reserved,192));tag.put("paths",paths);tag.put("recipes",recipes);tag.put("phases",phases);return tag.sizeInBytes()>MAX_BYTES?status("too_large"):tag;
    }
}
