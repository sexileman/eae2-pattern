package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.StackSpec;
import io.github.sexileman.eae2pattern.wildcard.MaterialForms;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Screen-local representatives, collected within the existing filtering tick budget. */
public final class MaterialChoiceCatalog {
    private record Entry(PickerOption option,int rank) {}
    private final SortedMap<String,Entry> entries=new TreeMap<>();
    public void add(StackSpec spec) {
        var materials=MaterialForms.materials(spec);
        if(materials.isEmpty())return;
        int rank=rank(MaterialForms.forms(spec),spec.isItem());
        for(String family:materials) {
            var previous=entries.get(family);
            if(previous!=null&&(previous.rank()<rank || previous.rank()==rank
                    && previous.option().icon().id().toString().compareTo(spec.id().toString())<=0))continue;
            PickerOption option=describe(family,spec);
            entries.put(family,new Entry(option,rank));
        }
    }
    public static int rank(Set<String> forms,boolean item) {
        if(!item)return 6;
        String[] preferred={"ingots","gems","dusts","plates","rods"};
        for(int i=0;i<preferred.length;i++)if(forms.contains(preferred[i]))return i;
        return 5;
    }
    private static PickerOption describe(String family,StackSpec example) {
        try {
            var helper=Class.forName("com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper");
            Object material;
            if(example.isItem()) {
                var stack=helper.getMethod("getMaterial",net.minecraft.world.item.ItemStack.class).invoke(null,example.toItemStack());
                material=stack==null?null:stack.getClass().getMethod("material").invoke(stack);
            } else {
                var fluid=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(example.id());
                material=fluid==null?null:helper.getMethod("getMaterial",net.minecraft.world.level.material.Fluid.class).invoke(null,fluid);
            }
            if(material!=null&&family.equals(material.getClass().getMethod("getName").invoke(material))) {
                var id=(ResourceLocation)material.getClass().getMethod("getResourceLocation").invoke(material);
                var name=(Component)material.getClass().getMethod("getLocalizedName").invoke(material);
                if(id!=null)return new PickerOption(family,name,Component.literal(id.toString()),example);
            }
        } catch(ReflectiveOperationException|LinkageError ignored) { }
        String translation="material.gtceu."+family;
        Component name=net.minecraft.locale.Language.getInstance().has(translation)?Component.translatable(translation):Component.literal(family);
        String tag=MaterialForms.tags(example).stream().filter(t->MaterialForms.materialsFromTags(List.of(t)).contains(family)).sorted().findFirst().orElse("");
        Component detail=tag.isEmpty()?Component.literal(example.id().toString()):WildcardEditorScreen.tr("material_tag",tag);
        return new PickerOption(family,name,detail,example);
    }
    public List<PickerOption> options() { return entries.values().stream().map(Entry::option).toList(); }
}
