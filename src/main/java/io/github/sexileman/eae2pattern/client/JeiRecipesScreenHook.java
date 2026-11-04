package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Requirement #8: a control on JEI's own recipe screen that starts a batch from what is being shown.
 *
 * <p>JEI exposes no API for adding chrome to its recipe screen, so the button is drawn by us. It is placed inside the
 * catalyst tab on the left — the tab that says "these blocks can run this recipe" — as the next free 16x16 slot of
 * that tab's grid. That keeps it inside JEI's own frame instead of floating over the world, keeps it next to the
 * machine the batch is about, and never covers the recipe. The geometry is recomputed exactly the way
 * {@code RecipeCatalysts.updateLayout} does it from the window area exposed by {@link RecipesGui#getArea()}.</p>
 *
 * <p>When the tab happens to be filled to the last slot there is no free cell, so the button becomes one more slot
 * below the tab and paints a matching little tab behind itself; when the category has no catalysts at all it parks in
 * the window header, left of JEI's page arrows.</p>
 *
 * <p>The selected category is only reachable through a private {@code logic} field, so that one field is read
 * reflectively; everything is wrapped so a future JEI that renames it costs us the button, not a crash.</p>
 */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JeiRecipesScreenHook {

    /** JEI's catalyst slot pitch: a 16x16 icon. */
    private static final int SLOT_SIZE = 16;
    /** JEI draws catalyst icons at band origin + borderSize(5) + ingredientBorderSize(1). */
    private static final int SLOT_PADDING = 6;
    /** backgroundTab plus slotBackground: (2*5) border + (2*1) ingredient border. */
    private static final int CATALYST_BAND = 12;
    private static final int CATALYST_OVERLAP = 6;
    /** Height of JEI's own option-button stack in the bottom-left corner (2*1 + 2*5 + 2*16). */
    private static final int OPTION_BUTTONS_HEIGHT = 44;
    /** Gap JEI leaves between the catalyst tab and the option buttons. */
    private static final int OPTION_BUTTONS_GAP = 8;
    /** Width of the small page/category arrows in JEI's header. */
    private static final int HEADER_BUTTON_WIDTH = 13;
    private static final int HEADER_BORDER_PADDING = 6;

    private static final int COLOR_TAB_BORDER = 0xFF555555;
    private static final int COLOR_TAB_FACE = 0xFF8B8B8B;
    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_FACE = 0xFF8B8B8B;
    private static final int COLOR_SLOT_FACE_HOVER = 0xFFC6C6C6;

    @Nullable
    private static Field logicField;
    private static boolean logicFieldProbed;

    private JeiRecipesScreenHook() {
    }

    /** A button position, plus whether we have to paint our own little tab behind it. */
    private record Placement(ImmutableRect2i rect, boolean ownTab) {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        IRecipeGuiLogic logic = logic(event.getScreen());
        if (logic == null) {
            return;
        }
        Placement placement = placement(event.getScreen(), logic);
        if (placement == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        boolean hovered = placement.rect().contains(event.getMouseX(), event.getMouseY());
        if (placement.ownTab()) {
            drawTab(graphics, placement.rect());
        }
        drawButton(graphics, placement.rect(), hovered);
        if (hovered) {
            graphics.renderComponentTooltip(Minecraft.getInstance().font, List.of(
                    Component.translatable("eae2_pattern.jei.button"),
                    Component.translatable("eae2_pattern.jei.button.desc").withStyle(ChatFormatting.GRAY)
            ), (int) event.getMouseX(), (int) event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        Screen screen = event.getScreen();
        IRecipeGuiLogic logic = logic(screen);
        if (logic == null) {
            return;
        }
        Placement placement = placement(screen, logic);
        if (placement == null || !placement.rect().contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        event.setCanceled(true);
        if (logic.hasAllCategories()) {
            // JEI is showing the category index rather than a single recipe list; picking one is the only
            // honest interpretation of "everything this screen shows", because there is no machine here.
            List<IRecipeCategory<?>> categories = logic.getRecipeCategories().stream()
                    .filter(category -> !"jei".equals(category.getRecipeType().getUid().getNamespace()))
                    .toList();
            if (categories.isEmpty()) {
                EAE2PatternClient.message(Component.translatable("eae2_pattern.msg.no_categories"));
                return;
            }
            Minecraft.getInstance().setScreen(new MachinePickerScreen(
                    Component.translatable("eae2_pattern.picker.choose"), categories));
            return;
        }
        IRecipeCategory<?> category = logic.getSelectedRecipeCategory();
        if (category == null) {
            EAE2PatternClient.message(Component.translatable("eae2_pattern.msg.no_categories"));
            return;
        }
        EAE2PatternClient.openCategory(category);
    }

    /** The tab JEI itself would have drawn one row further down: dark border, light face. */
    private static void drawTab(GuiGraphics graphics, ImmutableRect2i rect) {
        int x = rect.getX();
        int y = rect.getY();
        graphics.fill(x - 3, y - 3, x + SLOT_SIZE + 3, y + SLOT_SIZE + 3, COLOR_TAB_BORDER);
        graphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, COLOR_TAB_FACE);
    }

    private static void drawButton(GuiGraphics graphics, ImmutableRect2i rect, boolean hovered) {
        int x = rect.getX();
        int y = rect.getY();
        int right = x + rect.getWidth();
        int bottom = y + rect.getHeight();

        // A 16x16 slot, styled like the catalyst slots around it, so the button reads as part of the tab.
        graphics.fill(x, y, right, bottom, COLOR_SLOT_BORDER);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, hovered ? COLOR_SLOT_FACE_HOVER : COLOR_SLOT_FACE);

        // A stack of pattern cards: slightly dimmed when idle, full contrast under the cursor.
        RecipeIcons.drawPatternStack(graphics, x, y, hovered ? 1.0F : 0.82F);
    }

    /**
     * Works out where the button goes by replaying JEI's own catalyst-tab layout.
     *
     * @return where to draw, or {@code null} when this is not JEI's recipe screen or JEI has not laid itself out yet.
     */
    @Nullable
    private static Placement placement(Screen screen, IRecipeGuiLogic logic) {
        if (!(screen instanceof RecipesGui recipesGui)) {
            return null;
        }
        ImmutableRect2i area = recipesGui.getArea();
        if (area.isEmpty() || area.getHeight() < 96 || area.getWidth() < 80) {
            return null;
        }

        int catalysts = catalystCount(logic);
        if (catalysts <= 0) {
            // No catalyst tab exists (also the case on the category index page): sit in the header instead.
            int x = area.getX() + area.getWidth() - HEADER_BORDER_PADDING - HEADER_BUTTON_WIDTH - 2 - SLOT_SIZE;
            return new Placement(new ImmutableRect2i(x, area.getY() + 4, SLOT_SIZE, SLOT_SIZE), false);
        }

        int availableHeight = area.getHeight() - OPTION_BUTTONS_HEIGHT - OPTION_BUTTONS_GAP;
        int perColumn = Math.max(1, (availableHeight - CATALYST_BAND) / SLOT_SIZE);
        int columns = ceilDiv(catalysts, perColumn);
        perColumn = ceilDiv(catalysts, columns);
        int bandWidth = CATALYST_BAND + columns * SLOT_SIZE;
        int bandHeight = CATALYST_BAND + perColumn * SLOT_SIZE;
        int left = area.getX() - bandWidth + CATALYST_OVERLAP;
        int top = area.getY();

        // JEI fills the tab column by column, so the first free cell is simply index == catalysts.
        boolean insideTab = catalysts < columns * perColumn;
        int column = insideTab ? catalysts / perColumn : 0;
        int row = insideTab ? catalysts % perColumn : perColumn;
        int x = left + SLOT_PADDING + column * SLOT_SIZE;
        int y = top + SLOT_PADDING + row * SLOT_SIZE;
        if (insideTab) {
            return new Placement(new ImmutableRect2i(x, y, SLOT_SIZE, SLOT_SIZE), false);
        }

        // Tab is full: add one more slot right below it, but keep it inside the window.
        y = top + bandHeight + 1;
        int lowest = area.getY() + area.getHeight() - OPTION_BUTTONS_HEIGHT - SLOT_SIZE - 2;
        y = Math.min(y, lowest);
        return new Placement(new ImmutableRect2i(x, y, SLOT_SIZE, SLOT_SIZE), true);
    }

    private static int catalystCount(IRecipeGuiLogic logic) {
        try {
            if (logic.getSelectedRecipeCategory() == null) {
                return 0;
            }
            // Same call RecipesGui makes when it lays the tab out.
            return (int) logic.getRecipeCatalysts().count();
        } catch (RuntimeException e) {
            EAE2Pattern.LOGGER.debug("Unable to read JEI catalysts", e);
            return 0;
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    @Nullable
    private static IRecipeGuiLogic logic(Screen screen) {
        if (!(screen instanceof RecipesGui)) {
            return null;
        }
        Field field = logicField(screen.getClass());
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(screen);
            if (value instanceof IRecipeGuiLogic guiLogic) {
                return guiLogic;
            }
        } catch (IllegalAccessException | RuntimeException e) {
            EAE2Pattern.LOGGER.debug("Unable to read JEI's recipe gui logic", e);
        }
        return null;
    }

    @Nullable
    private static Field logicField(Class<?> screenClass) {
        if (logicFieldProbed) {
            return logicField;
        }
        logicFieldProbed = true;
        try {
            Field field = screenClass.getDeclaredField("logic");
            field.setAccessible(true);
            logicField = field;
        } catch (NoSuchFieldException | RuntimeException e) {
            EAE2Pattern.LOGGER.warn("JEI recipe screen no longer has a 'logic' field; the in-screen button is disabled");
        }
        return logicField;
    }
}
