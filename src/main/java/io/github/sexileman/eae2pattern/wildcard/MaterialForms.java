package io.github.sexileman.eae2pattern.wildcard;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
/** Shared client/server form identities. Tags are exact families, never registry-name guesses. */
public final class MaterialForms {
    public static final List<String> FORMS=List.of("", "ingots","plates","double_plates","dense_plates","rods","long_rods","wires","fine_wires","gears","small_gears","dusts","small_dusts","tiny_dusts","nuggets","gems","blocks","foils","bolts","screws","rings","rotors","springs","small_springs","frames");
    private static final Map<String,String> PREFIXES=Map.ofEntries(Map.entry("ingot","ingots"),Map.entry("plate","plates"),Map.entry("plateDouble","double_plates"),Map.entry("plateDense","dense_plates"),Map.entry("rod","rods"),Map.entry("rodLong","long_rods"),Map.entry("wireGtSingle","wires"),Map.entry("wireFine","fine_wires"),Map.entry("gear","gears"),Map.entry("gearSmall","small_gears"),Map.entry("dust","dusts"),Map.entry("dustSmall","small_dusts"),Map.entry("dustTiny","tiny_dusts"),Map.entry("nugget","nuggets"),Map.entry("gem","gems"),Map.entry("block","blocks"),Map.entry("foil","foils"),Map.entry("bolt","bolts"),Map.entry("screw","screws"),Map.entry("ring","rings"),Map.entry("rotor","rotors"),Map.entry("spring","springs"),Map.entry("springSmall","small_springs"),Map.entry("frameGt","frames"));
    private record Classification(Set<String> tags,Set<String> forms,Set<String> materials){}
    private static final WeightedCache<String,Classification> CACHE=new WeightedCache<>(4096,1_000_000);
    public static void clear(){CACHE.clear();}
    private static Classification classify(StackSpec spec){
        String key=spec.type()+":"+spec.id();var cached=CACHE.get(key);if(cached!=null)return cached;
        var tags=readTags(spec);var forms=formsFromTags(tags);var materials=materialsFromTags(tags);
        if(spec.isItem())try{var helper=Class.forName("com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper");
            var prefix=helper.getMethod("getPrefix",net.minecraft.world.level.ItemLike.class).invoke(null,spec.toItemStack().getItem());
            if(prefix!=null){String name=(String)prefix.getClass().getMethod("name").invoke(prefix);String form=prefixForm(name);forms.clear();forms.add(form);}
            var entry=helper.getMethod("getMaterial",net.minecraft.world.item.ItemStack.class).invoke(null,spec.toItemStack());if(entry!=null){var material=entry.getClass().getMethod("material").invoke(entry);materials.add((String)material.getClass().getMethod("getName").invoke(material));}
        }catch(ReflectiveOperationException|LinkageError ignored){}
        var result=new Classification(Set.copyOf(tags),Set.copyOf(forms),Set.copyOf(materials));int weight=192;for(var group:List.of(tags,forms,materials))for(var value:group)weight+=48+value.length()*2;CACHE.put(key,result,weight);return result;
    }
    private static final Map<String,String> NESTED=Map.ofEntries(Map.entry("plates/double","double_plates"),Map.entry("plates/dense","dense_plates"),Map.entry("rods/long","long_rods"),Map.entry("dusts/small","small_dusts"),Map.entry("dusts/tiny","tiny_dusts"),Map.entry("gears/small","small_gears"),Map.entry("springs/small","small_springs"),Map.entry("wires/fine","fine_wires"));
    private MaterialForms(){}
    /** Stable serialized identities; legacy aliases keep already encoded rules working. */
    public static String prefixForm(String name){
        String known=PREFIXES.get(name);
        return known!=null?known:name.startsWith("wireGt")?"wires":"gt_prefix:"+name;
    }
    public static boolean validForm(String value){
        if(FORMS.contains(value))return true;
        if(value.startsWith("gt_prefix:"))return value.substring(10).matches("[A-Za-z][A-Za-z0-9_]{0,95}");
        if(value.startsWith("tag_family:")){
            var id=ResourceLocation.tryParse(value.substring(11));
            return id!=null&&(id.getNamespace().equals("forge")||id.getNamespace().equals("c"))&&!id.getPath().contains("/");
        }
        return false;
    }
    public static net.minecraft.network.chat.Component formName(String value){
        String key="eae2_pattern.editor."+(value.isEmpty()?"all_forms":"form_"+value);
        if(net.minecraft.locale.Language.getInstance().has(key))return net.minecraft.network.chat.Component.translatable(key);
        if(value.startsWith("gt_prefix:"))try{
            var type=Class.forName("com.gregtechceu.gtceu.api.data.tag.TagPrefix");
            var prefix=type.getMethod("get",String.class).invoke(null,value.substring(10));
            if(prefix!=null){String translation=(String)type.getMethod("getUnlocalizedName").invoke(prefix);
                if(net.minecraft.locale.Language.getInstance().has(translation))return net.minecraft.network.chat.Component.literal(net.minecraft.network.chat.Component.translatable(translation,"").getString().trim());}
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return net.minecraft.network.chat.Component.literal(value.startsWith("gt_prefix:")?value.substring(10):value.startsWith("tag_family:")?value.substring(11):value);
    }
    public static Set<String> tags(StackSpec spec){return classify(spec).tags();}
    private static Set<String> readTags(StackSpec spec){
        var result=new TreeSet<String>();
        if(spec.isItem()){var item=ForgeRegistries.ITEMS.getValue(spec.id());if(item!=null)item.builtInRegistryHolder().tags().forEach(t->result.add(t.location().toString()));}
        else {var fluid=ForgeRegistries.FLUIDS.getValue(spec.id());if(fluid!=null)fluid.builtInRegistryHolder().tags().forEach(t->result.add(t.location().toString()));}
        return result;
    }
    public static Set<String> formsFromTags(Collection<String> tags){
        var result=new TreeSet<String>();for(String tag:tags){var id=ResourceLocation.tryParse(tag);if(id==null||!(id.getNamespace().equals("forge")||id.getNamespace().equals("c")))continue;
            String family=id.getPath().split("/",2)[0];boolean nested=false;for(var e:NESTED.entrySet())if(id.getPath().equals(e.getKey())||id.getPath().startsWith(e.getKey()+"/")){result.add(e.getValue());nested=true;break;}if(!nested&&!family.isEmpty())result.add(FORMS.contains(family)?family:"tag_family:"+id.getNamespace()+":"+family);
        }
        for(var e:NESTED.entrySet())if(result.contains(e.getValue()))result.remove(e.getKey().split("/")[0]);return result;
    }
    public static Set<String> forms(StackSpec spec){return classify(spec).forms();}
    public static Set<String> materials(StackSpec spec){return classify(spec).materials();}
    public static Set<String> materialsFromTags(Collection<String> tags){
        var result=new TreeSet<String>();for(String tag:tags){var id=ResourceLocation.tryParse(tag);if(id==null||!(id.getNamespace().equals("forge")||id.getNamespace().equals("c")))continue;
            String path=id.getPath();boolean nested=false;for(var e:NESTED.entrySet())if(path.equals(e.getKey())||path.startsWith(e.getKey()+"/")){if(path.length()>e.getKey().length())result.add(path.substring(e.getKey().length()+1));nested=true;break;}if(!nested){var parts=path.split("/",2);if(parts.length==2&&FORMS.contains(parts[0]))result.add(parts[1]);}
        }return result;
    }
    public static boolean matches(StackSpec spec,String form,String material){return (form.isEmpty()||forms(spec).contains(form))&&(material.isEmpty()||materials(spec).contains(material));}
}
