package io.github.sexileman.eae2pattern.wildcard;
import io.github.sexileman.eae2pattern.pattern.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Server-readable material slots, separate from JEI and client-only classes. */
public record RuleRecipe(ResourceLocation id,List<Slot> inputs,List<Slot> outputs) {
    public record Slot(StackSpec stack,float chance){public boolean auxiliary(){return chance<=0;}}
    public RuleRecipe{inputs=List.copyOf(inputs);outputs=List.copyOf(outputs);}
    public boolean canEncode(int policy,boolean dropTools){
        int in=0,out=0;for(var s:inputs)if(!dropTools||!s.auxiliary())in++;
        for(var s:outputs)if(policy==0||s.chance()>=1){out++;if(policy==2)break;}
        return in>0&&in<=PatternSpec.MAX_INPUTS&&out>0&&out<=PatternSpec.MAX_OUTPUTS;
    }
    public PatternSpec encodeSpec(int multiplier,int policy,boolean dropTools){
        if(!canEncode(policy,dropTools))return null;
        var in=new ArrayList<StackSpec>();var out=new ArrayList<StackSpec>();
        for(var s:inputs)if(!dropTools||!s.auxiliary())in.add(scale(s.stack(),s.auxiliary()?1:multiplier));
        for(var s:outputs){if(policy!=0&&s.chance()<1)continue;out.add(scale(s.stack(),multiplier));if(policy==2)break;}
        if(in.isEmpty()||out.isEmpty())return null;
        var result=PatternSpec.processing(id,in,out);return result.fits()?result:null;
    }
    private static StackSpec scale(StackSpec s,int n){return s.withAmount(Math.multiplyExact(s.amount(),n));}
}
