package io.github.sexileman.eae2pattern.cycle;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.CraftingTreeNode;
import io.github.sexileman.eae2pattern.mixin.CycleProcessAccessor;
import java.util.*;
/** One bounded capture per calculation; no static graph or per-pattern scans on successful checks. */
public final class CycleCapture {
    public record Pattern(List<GenericStack> inputs,List<GenericStack> outputs,List<String> recipeTypes){}
    public record Witness(List<AEKey> path,List<Pattern> patterns){}
    private final List<Witness> witnesses=new ArrayList<>();
    private int rejections;
    private boolean truncated;
    public List<Witness> witnesses(){return List.copyOf(witnesses);}
    public boolean truncated(){return truncated;}
    public void rejected(CraftingTreeNode leaf,IPatternDetails candidate){
        if(rejections>=CyclePath.MAX_REJECTIONS||witnesses.size()>=CyclePath.MAX_REPORTS){truncated=true;return;}
        rejections++;
        try{
            var keys=new ArrayList<AEKey>();var selected=new ArrayList<IPatternDetails>();
            CraftingTreeNode node=leaf;
            while(node!=null){
                if(keys.size()==CyclePath.MAX_DEPTH){truncated=true;return;}
                var view=(CycleNode)(Object)node;keys.add(view.eae2$cycleKey());
                var parent=view.eae2$cycleParent();if(parent==null)break;
                var process=(CycleProcessAccessor)(Object)parent;selected.add(process.eae2$details());node=process.eae2$parent();
            }
            Collections.reverse(keys);Collections.reverse(selected);
            var consumed=new ArrayList<AEKey>();
            for(var input:candidate.getInputs()){
                var possible=input.getPossibleInputs();if(possible.length==0)continue;
                var key=possible[0].what();
                if(possible[0].amount()>0&&input.getMultiplier()>0&&!key.equals(input.getRemainingKey(key)))consumed.add(key);
            }
            var path=CyclePath.close(keys,consumed);
            if(path.isEmpty()||witnesses.stream().anyMatch(w->w.path().equals(path)))return;
            int first=keys.size()-(path.size()-1);
            var patterns=new ArrayList<Pattern>();
            for(int i=Math.max(0,first);i<selected.size();i++)patterns.add(snapshot(selected.get(i)));
            patterns.add(snapshot(candidate));witnesses.add(new Witness(path,List.copyOf(patterns)));
        }catch(RuntimeException | LinkageError invalid){truncated=true;}
    }
    private static Pattern snapshot(IPatternDetails details){
        var inputs=new ArrayList<GenericStack>();
        for(var input:details.getInputs()){
            var options=input.getPossibleInputs();if(options.length>0)inputs.add(new GenericStack(options[0].what(),Math.multiplyExact(options[0].amount(),input.getMultiplier())));
        }
        var types=new ArrayList<String>();var definition=details.getDefinition();
        if(definition!=null&&definition.getTag()!=null){var ids=definition.getTag().getCompound("gtlcore").getList("patternQuickUploadRecipeTypes",8);
            for(int i=0;i<Math.min(4,ids.size());i++){var id=ids.getString(i);if(id.length()<=96)types.add(id);}}
        return new Pattern(List.copyOf(inputs),List.of(details.getOutputs()),List.copyOf(types));
    }
}
