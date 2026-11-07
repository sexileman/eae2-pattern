package io.github.sexileman.eae2pattern.wildcard;
import io.github.sexileman.eae2pattern.pattern.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.lang.reflect.*;
/** Optional GT reader: never derives a recipe from material tags. Unknown capabilities fail closed. */
public final class ServerRecipeReader {
    private static final String GT="com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final ClassValue<Map<String,AccessibleObject>> MEMBERS=new ClassValue<>(){
        protected Map<String,AccessibleObject> computeValue(Class<?> type){var result=new HashMap<String,AccessibleObject>();for(var f:type.getFields())result.put(f.getName(),f);for(var m:type.getMethods())if(m.getParameterCount()==0)result.putIfAbsent(m.getName(),m);return result;}
    };
    private ServerRecipeReader(){}
    public static boolean supports(Recipe<?> recipe){return recipe.getClass().getName().equals(GT)||recipe instanceof AbstractCookingRecipe;}
    public static Object member(Object object,String name)throws ReflectiveOperationException{
        var a=MEMBERS.get(object.getClass()).get(name);if(a instanceof Field f)return f.get(object);if(a instanceof Method m)return m.invoke(object);throw new NoSuchFieldException(name);
    }
    public static RuleRecipe read(Recipe<?> recipe,Level level){
        try{
            if(recipe instanceof AbstractCookingRecipe){
                var inputs=new ArrayList<RuleRecipe.Slot>();for(var i:recipe.getIngredients()){var options=i.getItems();if(options.length==0)return null;var stack=options[0].copy();stack.setCount(1);inputs.add(new RuleRecipe.Slot(StackSpec.item(stack),1));}
                var out=recipe.getResultItem(level.registryAccess());if(out.isEmpty())return null;
                return new RuleRecipe(recipe.getId(),inputs,List.of(new RuleRecipe.Slot(StackSpec.item(out),1)));
            }
            if(!recipe.getClass().getName().equals(GT))return null;
            // Energy is handled by the machine. Other unknown capabilities cannot be represented by AE2.
            for(String side:List.of("tickInputs","tickOutputs"))for(var e:((Map<?,?>)member(recipe,side)).entrySet()){
                if(!((List<?>)e.getValue()).isEmpty()&&!e.getKey().getClass().getSimpleName().equals("EURecipeCapability"))return null;
            }
            var in=side((Map<?,?>)member(recipe,"inputs"));var out=side((Map<?,?>)member(recipe,"outputs"));
            if(in==null||out==null||in.isEmpty()||out.isEmpty())return null;
            return new RuleRecipe(recipe.getId(),in,out);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){return null;}
    }
    private static List<RuleRecipe.Slot> side(Map<?,?> map)throws ReflectiveOperationException{
        var items=new ArrayList<RuleRecipe.Slot>();var fluids=new ArrayList<RuleRecipe.Slot>();
        for(var e:map.entrySet()){
            String cap=e.getKey().getClass().getSimpleName();boolean item=cap.equals("ItemRecipeCapability"),fluid=cap.equals("FluidRecipeCapability");
            if(!item&&!fluid){if(!((List<?>)e.getValue()).isEmpty())return null;continue;}
            for(Object content:(List<?>)e.getValue()){
                Object value=member(content,"content");int max=((Number)member(content,"maxChance")).intValue(),chance=((Number)member(content,"chance")).intValue();
                if(max<=0||chance<0||chance>max)return null;float probability=(float)chance/max;StackSpec spec;
                if(item){
                    ItemStack stack;
                    if(value instanceof Ingredient ingredient){var choices=ingredient.getItems();if(choices.length==0)return null;stack=choices[0].copy();}
                    else if(value instanceof ItemStack s)stack=s.copy();else return null;
                    if(stack.isEmpty())return null;
                    long amount=stack.getCount();try{amount=((Number)member(value,"getAmount")).longValue();}catch(NoSuchFieldException ignored){}
                    spec=StackSpec.item(stack).withAmount(amount);
                }else{
                    Object stack=value;try{Object[] choices=(Object[])member(value,"getStacks");if(choices.length==0)return null;stack=choices[0];}catch(NoSuchFieldException ignored){}
                    Fluid f=(Fluid)member(stack,"getFluid");long amount=((Number)member(stack,"getAmount")).longValue();
                    CompoundTag tag=(CompoundTag)member(stack,"getTag");spec=new StackSpec(StackType.FLUID,ForgeRegistries.FLUIDS.getKey(f),tag,amount);
                }
                (item?items:fluids).add(new RuleRecipe.Slot(spec,probability));
            }
        }items.addAll(fluids);return items;
    }
}
