package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.compat.jei.JeiRuntimeHolder;
import io.github.sexileman.eae2pattern.wildcard.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Native tooltip typography: primary fields, secondary context, then advanced/debug data. */
@Mod.EventBusSubscriber(modid=EAE2Pattern.MOD_ID,value=Dist.CLIENT)
public final class WildcardTooltip {
    private static Object lastRuntime;
    private static final Map<String,Component> NAMES=new LinkedHashMap<>();
    private WildcardTooltip() {}
    public static void clearNames() { lastRuntime=null; NAMES.clear(); }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var stack=event.getItemStack();
        if(!MachineWildcard.isOwnItem(stack)&&!MachineWildcard.isBound(stack))return;
        var lines=event.getToolTip();
        if(!MachineWildcard.isBound(stack)){
            lines.add(Component.translatable("eae2_pattern.editor.blank_help").withStyle(ChatFormatting.GRAY));return;
        }
        if(!lines.isEmpty()&&((MachineWildcard.isOwnItem(stack)&&!stack.hasCustomHoverName())||WildcardTooltipText.isGeneratedName(stack.getHoverName())))
            lines.set(0,WildcardTooltipText.name(stack));
        var root=stack.getTag();var data=root.getCompound(MachineWildcardData.KEY);
        String category=root.getCompound("editor").getString("category");
        if(data.getInt("version")==3)category=data.getCompound("rules").getString("category");
        var content=WildcardTooltipText.lines(typeName(data.getString("machine"),category),MachineWildcardData.count(data),
                data.getInt("version")==3,root.getString("encodePlayer"));
        lines.addAll(Math.min(1,lines.size()),content);
    }
    @Mod.EventBusSubscriber(modid=EAE2Pattern.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> WildcardTooltipText.setTypeNames(WildcardTooltip::typeName));
        }
    }
    private static Component typeName(String machine,String category) {
        var runtime=JeiRuntimeHolder.get();
        if(runtime!=lastRuntime){lastRuntime=runtime;NAMES.clear();}
        if(runtime==null)return Component.literal(machine);
        String key=machine+"|"+category;var cached=NAMES.get(key);if(cached!=null)return cached;
        Component name=runtime.getRecipeManager().createRecipeCategoryLookup().get()
                .filter(c->c.getRecipeType().getUid().toString().equals(category.isEmpty()?machine:category))
                .map(c->c.getTitle().copy()).findFirst().orElseGet(()->Component.literal(machine));
        if(NAMES.size()>=128)NAMES.clear();NAMES.put(key,name);return name;
    }
}
