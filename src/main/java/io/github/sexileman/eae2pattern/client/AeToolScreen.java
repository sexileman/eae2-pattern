package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Client screen contract used by JEI; no server menu or inventory ownership is changed. */
public abstract class AeToolScreen extends Screen {
    protected AeToolScreen(Component title){super(title);}
    public abstract Rect2i toolBounds();
    public boolean insidePanel(double x,double y){return toolBounds().contains((int)x,(int)y);}
    public record IngredientArea(StackSpec ingredient,Rect2i bounds){}
    public record GhostField(Rect2i bounds,Consumer<StackSpec> receive){}
    public Optional<IngredientArea> jeiIngredientAt(double x,double y){return Optional.empty();}
    public List<GhostField> jeiGhostFields(){return List.of();}
    public List<Rect2i> jeiExtraAreas(){return List.of();}
    protected GhostField ghostField(EditBox field,Consumer<StackSpec> receive){
        return new GhostField(new Rect2i(field.getX()-4,field.getY()-4,field.getWidth()+8,20),receive);
    }
    @Override public boolean isPauseScreen(){return false;}
}
