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
 * <p>JEI exposes no API for adding chrome to its recipe screen, so the button is drawn by us. It sits in the left
 * band, inside the catalyst tab that JEI uses to show "which blocks craft this recipe" — the button is stacked
 * directly underneath those catalyst slots, so it is always adjacent to the machine it acts on and never overlaps
 * the recipe itself. The band geometry is recomputed from JEI's published window area
 * ({@link RecipesGui#getArea()}), which is what keeps it glued to JEI's frame as windows resize.</p>
 *
 * <p>The selected category is only reachable through a private {@code logic} field, so that one field is read
 * reflectively; everything is wrapped so a future JEI that renames it costs us the button, not a crash.</p>
 */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JeiRecipesScreenHook {

    /** Matches JEI's catalyst slot pitch: 16x16 icon inside a 1px border. */
    private static final int SLOT_SIZE = 16;
    private static final int CATALYST_BORDER = 6;
    private static final int CATALYST_OVERLAP = 6;
    private static final int CATALYST_BAND = 12;
    /** Height of JEI's own option-button stack in the bottom-left corner (2*1 + 2*5 + 2*16). */
    private static final int OPTION_BUTTONS_HEIGHT = 44;

    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_FACE = 0xFF8B8B8B;
    private static final int COLOR_SLOT_FACE_HOVER = 0xFFC6C6C6;
    private static final int COLOR_ICON_DIM = 0xFF3D3D3D;
    private static final int COLOR_ICON_ACCENT = 0xFF2F5F8F;

    @Nullable
    private static Field logicField;
    private static boolean logicFieldProbed;

    private JeiRecipesScreenHook() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        IRecipeGuiLogic logic = logic(event.getScreen());
        if (logic == null) {
            return;
        }
        ImmutableRect2i rect = buttonRect(event.getScreen(), logic);
        if (rect == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        boolean hovered = rect.contains(event.getMouseX(), event.getMouseY());
        drawButton(graphics, rect, hovered);
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
        ImmutableRect2i rect = buttonRect(screen, logic);
        if (rect == null || !rect.contains(event.getMouseX(), event.getMouseY())) {
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

    private static void drawButton(GuiGraphics graphics, ImmutableRect2i rect, boolean hovered) {
        int x = rect.getX();
        int y = rect.getY();
        int right = x + rect.getWidth();
        int bottom = y + rect.getHeight();

        // A 16x16 slot, styled like the catalyst slots directly above it, so the button reads as part of the tab.
        graphics.fill(x, y, right, bottom, COLOR_SLOT_BORDER);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, hovered ? COLOR_SLOT_FACE_HOVER : COLOR_SLOT_FACE);

        // 3x3 recipe grid: 4px cells with a 1px gap, which is exactly the 14px inner area.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int cellLeft = x + 1 + column * 5;
                int cellTop = y + 1 + row * 5;
                boolean centre = row == 1 && column == 1;
                graphics.fill(cellLeft, cellTop, cellLeft + 4, cellTop + 4,
                        centre ? COLOR_ICON_ACCENT : COLOR_ICON_DIM);
            }
        }
    }

    /**
     * Recomputes JEI's catalyst band the same way {@code RecipeCatalysts.updateLayout} does, then puts the button
     * directly under the last row of catalyst slots. Falls back to a single empty column when the category has no
     * catalysts, so the button does not jump around between categories.
     *
     * @return the button rectangle in screen space, or {@code null} when this is not JEI's recipe screen or JEI has
     * not laid itself out yet.
     */
    @Nullable
    private static ImmutableRect2i buttonRect(Screen screen, IRecipeGuiLogic logic) {
        if (!(screen instanceof RecipesGui recipesGui)) {
            return null;
        }
        ImmutableRect2i area = recipesGui.getArea();
        if (area.isEmpty() || area.getHeight() < 96 || area.getWidth() < 80) {
            return null;
        }

        int catalysts = catalystCount(logic);
        int bandWidth;
        int bandHeight;
        if (catalysts > 0) {
            int availableHeight = area.getHeight() - OPTION_BUTTONS_HEIGHT - 8;
            int perColumn = Math.max(1, (availableHeight - CATALYST_BAND) / SLOT_SIZE);
            int columns = ceilDiv(catalysts, perColumn);
            perColumn = ceilDiv(catalysts, columns);
            bandWidth = CATALYST_BAND + columns * SLOT_SIZE;
            bandHeight = CATALYST_BAND + perColumn * SLOT_SIZE;
        } else {
            bandWidth = CATALYST_BAND + SLOT_SIZE;
            bandHeight = 0;
        }

        int left = area.getX() - bandWidth + CATALYST_OVERLAP;
        int x = left + CATALYST_BORDER;
        int y = area.getY() + bandHeight + 2;
        // Never slide into JEI's optional-button stack in the bottom-left corner, and never above the frame.
        int top = area.getY() + 2;
        int lowest = Math.max(top, area.getY() + area.getHeight() - OPTION_BUTTONS_HEIGHT - SLOT_SIZE - 2);
        y = Math.max(top, Math.min(y, lowest));
        return new ImmutableRect2i(x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private static int catalystCount(IRecipeGuiLogic logic) {
        try {
            IRecipeCategory<?> category = logic.getSelectedRecipeCategory();
            if (category == null) {
                return 0;
            }
            return (int) logic.getRecipeCatalysts(category).count();
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
