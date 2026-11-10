package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.client.*;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Standard JEI APIs keep search, bookmark keys and ghost dragging on every tool page. */
public final class ToolJeiHandlers {
    private ToolJeiHandlers(){}
    public static void register(IGuiHandlerRegistration registration){
        registration.addGuiScreenHandler(appeng.client.gui.me.crafting.CraftConfirmScreen.class, screen -> {
            var area = CyclePlanOverlay.jeiBounds(screen).orElseGet(() ->
                    new Rect2i(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize()));
            return ToolGuiProperties.capture(screen.getClass(), area, screen.width, screen.height);
        });
        screen(registration,PatternWorkbenchScreen.class);screen(registration,WildcardEditorScreen.class);
        screen(registration,WildcardChoiceScreen.class);screen(registration,WildcardMachineScreen.class);
        screen(registration,MachinePickerScreen.class);screen(registration,CollectingScreen.class);
        registration.addGlobalGuiHandler(new IGlobalGuiHandler(){
            @Override public Collection<Rect2i> getGuiExtraAreas(){
                var current = Minecraft.getInstance().screen;
                var cycle = CyclePlanOverlay.jeiBounds(current);
                if(cycle.isPresent())return List.of(cycle.get());
                return current instanceof AeToolScreen tool?tool.jeiExtraAreas():List.of();
            }
            @Override public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double x,double y){
                if(!(Minecraft.getInstance().screen instanceof AeToolScreen tool))return Optional.empty();
                var runtime=JeiRuntimeHolder.get();if(runtime==null)return Optional.empty();
                return tool.jeiIngredientAt(x,y).flatMap(area->{
                    var spec=area.ingredient();
                    if(spec.isItem())return runtime.getIngredientManager().createClickableIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,spec.toItemStack(),area.bounds(),false).map(c->(IClickableIngredient<?>)c);
                    var fluid=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(spec.id());
                    if(fluid==null)return Optional.empty();
                    return runtime.getIngredientManager().createClickableIngredient(mezz.jei.api.forge.ForgeTypes.FLUID_STACK,new net.minecraftforge.fluids.FluidStack(fluid,(int)Math.min(Integer.MAX_VALUE,spec.amount()),spec.tag()),area.bounds(),false).map(c->(IClickableIngredient<?>)c);
                });
            }
        });
    }
    private static boolean sameArea(Rect2i a,Rect2i b){return a.getX()==b.getX()&&a.getY()==b.getY()&&a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight();}
    private static <T extends AeToolScreen> void screen(IGuiHandlerRegistration registration,Class<T> type){
        registration.addGuiScreenHandler(type,tool->ToolGuiProperties.capture(
                tool.getClass(),tool.toolBounds(),tool.width,tool.height));
        registration.addGhostIngredientHandler(type,new IGhostIngredientHandler<T>(){
            public <I> List<Target<I>> getTargetsTyped(T tool,ITypedIngredient<I> ingredient,boolean doStart){
                StackSpec spec;
                var item=ingredient.getItemStack();
                if(item.isPresent()&&!item.get().isEmpty())spec=StackSpec.item(item.get());
                else {var fluid=ingredient.getIngredient(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);if(fluid.isEmpty()||fluid.get().isEmpty())return List.of();spec=StackSpec.fluid(fluid.get());}
                var result=new ArrayList<Target<I>>();
                for(var field:tool.jeiGhostFields())result.add(new Target<>(){
                    public Rect2i getArea(){return field.bounds();}
                    public void accept(I ignored){
                        if(Minecraft.getInstance().screen!=tool)return;
                        for(var live:tool.jeiGhostFields())if(sameArea(live.bounds(),field.bounds())){live.receive().accept(spec);return;}
                    }
                });
                return result;
            }
            public void onComplete(){}
        });
    }
}
