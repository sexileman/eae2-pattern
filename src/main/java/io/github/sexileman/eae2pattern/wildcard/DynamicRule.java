package io.github.sexileman.eae2pattern.wildcard;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
/** Compiled portable rule shared by the editor and authoritative source-recipe resolver. */
public final class DynamicRule {
    private final CompoundTag state; private final PortableRuleQuery[] queries=new PortableRuleQuery[4];
    private final CircuitCondition circuit;
    private final Set<String> excluded=new HashSet<>();
    public DynamicRule(CompoundTag raw){state=WildcardEditorState.clean(raw);circuit=CircuitCondition.read(state);
        String[] keys={"in_include","in_exclude","out_include","out_exclude"};for(int i=0;i<4;i++)queries[i]=new PortableRuleQuery(state.getString(keys[i]));
        var ids=state.getList("exclude_ids",8);for(int i=0;i<ids.size();i++)excluded.add(ids.getString(i));
    }
    public boolean matches(RuleRecipe recipe){return circuit.matches(recipe.inputs())&&!excluded.contains(recipe.id().toString())&&side(recipe.inputs(),true)&&side(recipe.outputs(),false);}
    private boolean side(List<RuleRecipe.Slot> source,boolean input){
        String prefix=input?"in_":"out_";int scope=state.getInt(prefix+"scope");
        var slots=source.stream().filter(s->!input||state.getBoolean("search_tools")||!s.auxiliary()).filter(s->scope==0||(scope==1)==s.stack().isItem()).map(RuleRecipe.Slot::stack).toList();
        String form=state.getString(prefix+"form"),material=state.getString("material");
        // Structural form filters ignore tools even when advanced text search includes tools.
        var business=source.stream().filter(s->!input||!s.auxiliary()).map(RuleRecipe.Slot::stack).toList();
        if((!form.isEmpty()||!material.isEmpty())&&business.stream().noneMatch(s->MaterialForms.matches(s,form,material)))return false;
        int i=input?0:2; if(!queries[i+1].isEmpty()&&queries[i+1].matches(slots))return false;
        if(state.getBoolean(prefix+"all"))return !slots.isEmpty()?slots.stream().allMatch(s->queries[i].matches(List.of(s))):queries[i].isEmpty();
        return queries[i].matches(slots);
    }
}
