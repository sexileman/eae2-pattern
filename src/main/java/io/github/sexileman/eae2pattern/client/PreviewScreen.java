package io.github.sexileman.eae2pattern.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The preview screen, laid out as four cards: filter on the left, the recipe list in the middle, the selected
 * recipe on the right and the action card underneath it.
 *
 * <p>Every rectangle is computed once in {@link #layout()} and reused for both drawing and hit testing. Earlier
 * versions positioned controls from fixed offsets and ended up drawing the filter column on top of the footer
 * buttons on small screens, where a click on a checkbox silently hit a button instead. One layout method makes
 * that class of bug impossible.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    // ------------------------------------------------------------------ layout constants

    private static final int PAD = 8;
    private static final int HEADER_H = 30;
    private static final int CARD_TITLE_H = 15;
    private static final int COLUMN_H = 11;
    private static final int ROW_H = 14;
    private static final int STEP = 15;
    private static final int ICON = 12;
    private static final int ICON_STEP = 13;
    private static final int CHECKBOX = 9;
    private static final int FIELD_H = 13;
    private static final int COND_H = 11;
    private static final int COND_STEP = 13;
    private static final int FOOTER_H = 13;
    private static final int MULTIPLIER_W = 14;

    // ------------------------------------------------------------------ palette

    private static final int SCRIM = 0xB4000000;
    private static final int PANEL = 0xB41B1D23;
    private static final int PANEL_BORDER = 0x2EFFFFFF;
    private static final int CARD = 0x780A0C10;
    private static final int CARD_BORDER = 0x1CFFFFFF;
    private static final int CARD_DIVIDER = 0x22FFFFFF;
    private static final int ROW_ON = 0xA62F4E77;
    private static final int ROW_HOVER = 0x33FFFFFF;
    private static final int FIELD = 0xDC06070A;
    private static final int FIELD_BORDER = 0x33FFFFFF;
    private static final int ACCENT = 0xFF4C9AFF;
    private static final int ACCENT_SOFT = 0x33304A6E;
    private static final int TEXT = 0xFFEEF0F5;
    private static final int TEXT_DIM = 0xFF9CA2AC;
    private static final int TEXT_MUTED = 0xFF7C828C;
    private static final int OK = 0xFF6EC88A;
    private static final int WARN = 0xFFE8AE58;
    private static final int BAD = 0xFFE87C7C;
    private static final int BUTTON = 0x1CFFFFFF;
    private static final int BUTTON_HOVER = 0x33FFFFFF;

    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};
    private static final String[] CONDITION_KEYS = {
            "unconsumed", "chance_input", "drop_unconsumed", "chance_output", "chance_byproduct", "drop_chance"};
    private static final String[] TOOL_KEYS = {"select_all", "invert", "dedupe", "only_selected"};

    // ------------------------------------------------------------------ state

    private final RecipePlan plan;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();
    private List<PatternSpec> pending = List.of();

    private ItemSearchQuery productsQuery = ItemSearchQuery.empty();
    private ItemSearchQuery ingredientsQuery = ItemSearchQuery.empty();
    private EditBox productsBox;
    private EditBox ingredientsBox;

    private boolean onlyNeverConsumed;
    private boolean onlyProbabilisticInput;
    private boolean onlyProbabilisticOutput;
    private boolean onlyProbabilisticByproduct;
    private boolean onlySelected;
    private boolean dropChanceOutputs;
    private boolean dropNeverConsumed = true;
    private int multiplier = 1;

    private int page;
    private Component notice;
    private boolean noticeFailure;
    private long noticeUntil;

    // Panel and cards, all rebuilt in layout().
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private Box filterCard = Box.EMPTY;
    private Box listCard = Box.EMPTY;
    private Box detailCard = Box.EMPTY;
    private Box actionCard = Box.EMPTY;
    private Box rowArea = Box.EMPTY;
    private Box listFooter = Box.EMPTY;
    private Box closeButton = Box.EMPTY;
    private Box productsHelp = Box.EMPTY;
    private Box ingredientsHelp = Box.EMPTY;
    private Box productsField = Box.EMPTY;
    private Box ingredientsField = Box.EMPTY;
    private Box legendBox = Box.EMPTY;
    private Box detailGrid = Box.EMPTY;
    private Box previousPage = Box.EMPTY;
    private Box nextPage = Box.EMPTY;
    private Box generateButton = Box.EMPTY;
    private Box cancelButton = Box.EMPTY;
    private final List<Box> toggles = new ArrayList<>();
    private final List<Box> tools = new ArrayList<>();
    private final List<Box> multiplierCells = new ArrayList<>();
    private int inputIconX;
    private int outputIconX;
    private int labelX;
    private int availableInputIcons;
    private int availableOutputIcons;

    public PreviewScreen(RecipePlan plan) {
        super(Component.translatable("eae2_pattern.preview.title", plan.machineName()));
        this.plan = plan;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        this.productsBox = new EditBox(this.font, 0, 0, 10, FIELD_H, Component.empty());
        this.productsBox.setBordered(false);
        this.productsBox.setMaxLength(64);
        this.productsBox.setTextColor(TEXT);
        this.productsBox.setHint(Component.empty());
        this.productsBox.setResponder(text -> {
            this.productsQuery = ItemSearchQuery.parse(text);
            refresh();
        });
        this.productsBox.setValue(this.productsQuery.raw());
        this.addRenderableWidget(this.productsBox);

        this.ingredientsBox = new EditBox(this.font, 0, 0, 10, FIELD_H, Component.empty());
        this.ingredientsBox.setBordered(false);
        this.ingredientsBox.setMaxLength(64);
        this.ingredientsBox.setTextColor(TEXT);
        this.ingredientsBox.setHint(Component.empty());
        this.ingredientsBox.setResponder(text -> {
            this.ingredientsQuery = ItemSearchQuery.parse(text);
            refresh();
        });
        this.ingredientsBox.setValue(this.ingredientsQuery.raw());
        this.addRenderableWidget(this.ingredientsBox);

        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
        refresh();
    }

    @Override
    public void removed() {
        ClientGenerationFeedback.setListener(null);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private void layout() {
        this.panelWidth = Math.min(this.width - 2 * PAD, 620);
        this.panelHeight = Math.min(this.height - 2 * PAD, 380);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int left = this.panelLeft + PAD;
        int right = this.panelLeft + this.panelWidth - PAD;
        int top = this.panelTop + HEADER_H;
        int bottom = this.panelTop + this.panelHeight - PAD;

        int detailWidth = Mth.clamp(Math.round(this.panelWidth * 0.28F), 104, 128);
        int filterWidth = Mth.clamp(Math.round(this.panelWidth * 0.29F), 116, 146);
        int gap = 6;

        this.filterCard = new Box(left, top, filterWidth, bottom - top);
        this.detailCard = new Box(right - detailWidth, top, detailWidth, bottom - top);

        int listLeft = this.filterCard.right() + gap;
        int listRight = this.detailCard.left() - gap;
        this.listCard = new Box(listLeft, top, Math.max(60, listRight - listLeft), bottom - top);

        // The action card takes the lower third of the right column.
        int actionHeight = Math.min(72, Math.max(56, this.detailCard.height() / 3));
        this.detailCard = new Box(this.detailCard.x(), this.detailCard.y(), this.detailCard.width(),
                this.detailCard.height() - actionHeight - gap);
        this.actionCard = new Box(this.detailCard.x(), this.detailCard.bottom() + gap,
                this.detailCard.width(), actionHeight);

        this.closeButton = new Box(right - 12, this.panelTop + 8, 11, 11);

        // Filter card contents.
        int fx = this.filterCard.x() + 6;
        int fw = this.filterCard.width() - 12;
        int fy = this.filterCard.y() + CARD_TITLE_H;
        this.ingredientsField = new Box(fx, fy, fw, FIELD_H);
        this.productsField = new Box(fx, fy + FIELD_H + 5, fw, FIELD_H);
        this.productsHelp = new Box(this.productsField.right() - 11, fy + 2, 8, 8);
        this.ingredientsHelp = new Box(this.ingredientsField.right() - 11, this.ingredientsField.y() + 2, 8, 8);

        int condTop = this.ingredientsField.bottom() + 5;
        this.toggles.clear();
        for (int i = 0; i < CONDITION_KEYS.length; i++) {
            int y = condTop + i * COND_STEP;
            if (y + COND_H > this.filterCard.bottom() - 26) {
                break;
            }
            this.toggles.add(new Box(fx, y, fw, COND_H));
        }
        this.legendBox = new Box(fx, this.filterCard.bottom() - 24, fw, 18);

        // List card contents.
        int listX = this.listCard.x() + 5;
        int listWidth = this.listCard.width() - 10;
        int listTop = this.listCard.y() + CARD_TITLE_H + COLUMN_H;
        this.listFooter = new Box(listX, this.listCard.bottom() - FOOTER_H - 3, listWidth, FOOTER_H);
        this.rowArea = new Box(listX, listTop, listWidth, this.listFooter.y() - 3 - listTop);

        int rows = rowsPerPage();
        int available = Math.max(1, rows);
        this.availableInputIcons = listWidth >= 150 ? 2 : 1;
        this.availableOutputIcons = listWidth >= 150 ? 2 : 1;
        this.inputIconX = listX + CHECKBOX + 7;
        this.outputIconX = this.inputIconX + this.availableInputIcons * ICON_STEP + 10;
        this.labelX = this.outputIconX + this.availableOutputIcons * ICON_STEP + 3;
        if (this.labelX > this.rowArea.right() - 30) {
            this.availableOutputIcons = 1;
            this.outputIconX = this.inputIconX + this.availableInputIcons * ICON_STEP + 10;
            this.labelX = this.outputIconX + this.availableOutputIcons * ICON_STEP + 3;
        }

        // List footer: tools on the left, pager on the right.
        this.tools.clear();
        int tx = this.listFooter.x();
        for (int i = 0; i < TOOL_KEYS.length; i++) {
            int width = this.font.width(Component.translatable("eae2_pattern.preview.tool." + TOOL_KEYS[i])) + 2;
            if (tx + width > this.listFooter.right() - 40) {
                break;
            }
            this.tools.add(new Box(tx, this.listFooter.y(), width, FOOTER_H));
            tx += width + 5;
        }
        this.nextPage = new Box(this.listFooter.right() - 12, this.listFooter.y() + 1, 12, FOOTER_H - 1);
        this.previousPage = new Box(this.nextPage.x() - 14, this.listFooter.y() + 1, 12, FOOTER_H - 1);

        // Detail card contents.
        int dx = this.detailCard.x() + 6;
        int dw = this.detailCard.width() - 12;
        int cell = Math.min(20, Math.max(14, (dw - 2 * 2) / 3));
        this.detailGrid = new Box(dx, this.detailCard.y() + CARD_TITLE_H, dw, cell * 3 + 4);

        // Action card contents: multiplier row, generate, cancel.
        int ax = this.actionCard.x() + 6;
        int aw = this.actionCard.width() - 12;
        this.multiplierCells.clear();
        int cellW = Math.min(MULTIPLIER_W, (aw - 6 * 2) / MULTIPLIERS.length);
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            this.multiplierCells.add(new Box(ax + i * (cellW + 2), this.actionCard.y() + CARD_TITLE_H, cellW, 10));
        }
        int buttonsTop = this.actionCard.y() + CARD_TITLE_H + 14;
        int buttonHeight = Math.min(13, this.actionCard.bottom() - buttonsTop - 14);
        this.generateButton = new Box(ax, buttonsTop, aw, Math.max(11, buttonHeight));
        this.cancelButton = new Box(ax, this.generateButton.bottom() + 3, aw,
                Math.max(11, Math.min(13, this.actionCard.bottom() - this.generateButton.bottom() - 6)));

        if (this.productsBox != null) {
            this.productsBox.setX(this.productsField.x() + 4);
            this.productsBox.setY(this.productsField.y() + 2);
            this.productsBox.setWidth(this.productsField.width() - 18);
        }
        if (this.ingredientsBox != null) {
            this.ingredientsBox.setX(this.ingredientsField.x() + 4);
            this.ingredientsBox.setY(this.ingredientsField.y() + 2);
            this.ingredientsBox.setWidth(this.ingredientsField.width() - 18);
        }
        this.page = Mth.clamp(this.page, 0, Math.max(0, pageCount() - 1));
    }

    private int rowsPerPage() {
        return Math.max(1, (this.rowArea.height() + (STEP - ROW_H)) / STEP);
    }

    private int pageCount() {
        int rows = rowsPerPage();
        return Math.max(1, (this.visible.size() + rows - 1) / rows);
    }

    // ------------------------------------------------------------------ filtering

    private void refresh() {
        rebuildVisible();
        afterSelectionChanged();
    }

    private void rebuildVisible() {
        RecipeFilters filters = new RecipeFilters(this.productsQuery, this.ingredientsQuery,
                this.onlyNeverConsumed, this.onlyProbabilisticInput, this.onlyProbabilisticOutput,
                this.onlyProbabilisticByproduct, this.onlySelected);
        this.visible.clear();
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (!filters.rejects(entry)) {
                this.visible.add(entry);
            }
        }
        this.page = Mth.clamp(this.page, 0, Math.max(0, pageCount() - 1));
    }

    private void afterSelectionChanged() {
        this.pending = this.plan.selectedSpecs(this.visible, this.multiplier, this.dropChanceOutputs,
                this.dropNeverConsumed);
        layout();
    }

    private void turnPage(int delta) {
        int target = Mth.clamp(this.page + delta, 0, Math.max(0, pageCount() - 1));
        if (target != this.page) {
            this.page = target;
        }
    }

    private void selectVisible(boolean selected) {
        for (RecipePlan.Entry entry : this.visible) {
            entry.setSelected(selected);
        }
        afterSelectionChanged();
    }

    private void invertVisible() {
        for (RecipePlan.Entry entry : this.visible) {
            entry.toggle();
        }
        afterSelectionChanged();
    }

    private void dropDuplicates() {
        Set<String> seen = new HashSet<>();
        int dropped = 0;
        for (RecipePlan.Entry entry : this.visible) {
            if (seen.add(entry.outputKey())) {
                continue;
            }
            if (entry.isSelected()) {
                entry.setSelected(false);
                dropped++;
            }
        }
        if (dropped > 0) {
            this.notice = Component.translatable("eae2_pattern.preview.deduped", dropped);
        }
        afterSelectionChanged();
    }

    private void cycleMultiplier() {
        int index = 0;
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            if (MULTIPLIERS[i] == this.multiplier) {
                index = i;
            }
        }
        this.multiplier = MULTIPLIERS[(index + 1) % MULTIPLIERS.length];
        afterSelectionChanged();
    }

    private boolean[] conditionValues() {
        return new boolean[]{
                this.onlyNeverConsumed, this.onlyProbabilisticInput, this.dropNeverConsumed,
                this.onlyProbabilisticOutput, this.onlyProbabilisticByproduct, this.dropChanceOutputs};
    }

    private void dropCondition(int index, boolean value) {
        switch (index) {
            case 0 -> this.onlyNeverConsumed = value;
            case 1 -> this.onlyProbabilisticInput = value;
            case 2 -> this.dropNeverConsumed = value;
            case 3 -> this.onlyProbabilisticOutput = value;
            case 4 -> this.onlyProbabilisticByproduct = value;
            case 5 -> this.dropChanceOutputs = value;
            default -> {
                return;
            }
        }
        refresh();
    }

    private void filterByIcon(RecipePlan.Entry entry, int index, boolean output) {
        List<Ingredient> list = output ? entry.outputs() : entry.inputs();
        if (index < 0 || index >= list.size()) {
            return;
        }
        StackSpec spec = list.get(index).spec();
        ItemStack stack = spec.toItemStack();
        String name = stack.isEmpty() ? spec.id().toString() : stack.getHoverName().getString();
        EditBox box = output ? this.productsBox : this.ingredientsBox;
        box.setValue(name);
        box.setFocused(true);
        this.setFocused(box);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, SCRIM);
        layout();

        UiKit.roundedRect(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, 7, PANEL);
        UiKit.roundedFrame(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, 7,
                PANEL, PANEL_BORDER);

        renderHeader(graphics, mouseX, mouseY);
        renderFilterCard(graphics, mouseX, mouseY);
        renderListCard(graphics, mouseX, mouseY);
        renderDetailCard(graphics, mouseX, mouseY);
        renderActionCard(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        renderNotice(graphics);
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderNotice(GuiGraphics graphics) {
        if (this.notice == null || Util.getMillis() >= this.noticeUntil) {
            this.notice = null;
            return;
        }
        String text = this.notice.getString();
        int width = Math.min(this.panelWidth - 2 * PAD - 24, this.font.width(text) + 16);
        int x = this.panelLeft + (this.panelWidth - width) / 2;
        int y = this.panelTop + HEADER_H - 11;
        int border = this.noticeFailure ? BAD : OK;
        UiKit.roundedRect(graphics, x, y, width, 12, 3, this.noticeFailure ? 0xE03A2024 : 0xE01F3A28);
        UiKit.roundedFrame(graphics, x, y, width, 12, 3, 0x00000000, border);
        graphics.drawString(this.font, UiKit.fit(this.font, text, width - 10), x + 5, y + 3, TEXT, false);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.panelLeft + PAD + 3, this.panelTop + 8, TEXT, false);
        String subtitle = Component.translatable("eae2_pattern.preview.subtitle",
                this.plan.selectedCount(), this.visible.size(), this.plan.entries().size()).getString();
        graphics.drawString(this.font, subtitle, this.panelLeft + PAD + 3, this.panelTop + 19, TEXT_DIM, false);

        boolean hovered = over(mouseX, mouseY, this.closeButton);
        UiKit.roundedRect(graphics, this.closeButton.x(), this.closeButton.y(),
                this.closeButton.width(), this.closeButton.height(), 3, hovered ? BUTTON_HOVER : BUTTON);
        graphics.drawString(this.font, "x", this.closeButton.x() + 3, this.closeButton.y() + 1,
                hovered ? TEXT : TEXT_DIM, false);
    }

    private void renderCards(GuiGraphics graphics, Box card, Component title, String trailing) {
        UiKit.roundedRect(graphics, card.x(), card.y(), card.width(), card.height(), 5, CARD);
        UiKit.roundedFrame(graphics, card.x(), card.y(), card.width(), card.height(), 5, CARD, CARD_BORDER);
        graphics.drawString(this.font, title, card.x() + 7, card.y() + 4, TEXT_DIM, false);
        if (trailing != null) {
            graphics.drawString(this.font, trailing,
                    card.right() - 7 - this.font.width(trailing), card.y() + 4, TEXT_MUTED, false);
        }
        graphics.fill(card.x() + 6, card.y() + CARD_TITLE_H - 3, card.right() - 6,
                card.y() + CARD_TITLE_H - 2, CARD_DIVIDER);
    }

    private void renderFilterCard(GuiGraphics graphics, int mouseX, int mouseY) {
        renderCards(graphics, this.filterCard, Component.translatable("eae2_pattern.preview.module.filter"),
                Component.translatable("eae2_pattern.preview.module.filter_count", 2).getString());

        drawField(graphics, this.ingredientsField, this.ingredientsHelp, this.ingredientsBox,
                Component.translatable("eae2_pattern.preview.filter.ingredients_hint"), mouseX, mouseY);
        drawField(graphics, this.productsField, this.productsHelp, this.productsBox,
                Component.translatable("eae2_pattern.preview.filter.products_hint"), mouseX, mouseY);

        boolean[] values = conditionValues();
        for (int i = 0; i < this.toggles.size(); i++) {
            Box box = this.toggles.get(i);
            boolean on = values[i];
            boolean hovered = over(mouseX, mouseY, box);
            UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 2,
                    on ? ACCENT_SOFT : (hovered ? BUTTON_HOVER : BUTTON));
            UiKit.checkbox(graphics, box.x() + 3, box.y() + 1, CHECKBOX, on, ACCENT, 0x28FFFFFF, 0x14000000);
            graphics.drawString(this.font,
                    Component.translatable("eae2_pattern.preview.filter." + CONDITION_KEYS[i]).getString(),
                    box.x() + 3 + CHECKBOX + 4, box.y() + 2, on ? TEXT : TEXT_DIM, false);
        }

        int legendTop = this.legendBox.y();
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.legend.title"), this.legendBox.x(),
                legendTop, TEXT_MUTED, false);
        String[] names = {"NC", "IN%", "OUT%", "BY%"};
        for (int i = 0; i < names.length; i++) {
            int y = legendTop + 10 + i * 9;
            if (y + 8 > this.legendBox.bottom()) {
                break;
            }
            graphics.drawString(this.font, names[i], this.legendBox.x(), y, badgeColor(i), false);
            graphics.drawString(this.font,
                    Component.translatable("eae2_pattern.preview.legend." + i).getString(),
                    this.legendBox.x() + 26, y, TEXT_MUTED, false);
        }
    }

    private void drawField(GuiGraphics graphics, Box field, Box help, @Nullable EditBox box, Component placeholder,
                           int mouseX, int mouseY) {
        boolean focused = box != null && box.isFocused();
        UiKit.roundedRect(graphics, field.x(), field.y(), field.width(), field.height(), 3, FIELD);
        UiKit.roundedFrame(graphics, field.x(), field.y(), field.width(), field.height(), 3, FIELD,
                focused ? ACCENT : FIELD_BORDER);
        if (box != null && box.getValue().isEmpty()) {
            // Placeholder: drawn here rather than through EditBox so it keeps the same muted style when focused.
            graphics.drawString(this.font, UiKit.fit(this.font, placeholder.getString(), field.width() - 18),
                    field.x() + 4, field.y() + 3, TEXT_MUTED, false);
        }
        boolean hovered = over(mouseX, mouseY, help);
        UiKit.roundedRect(graphics, help.x(), help.y(), help.width(), help.height(), 2,
                hovered ? ACCENT : 0x22FFFFFF);
        graphics.drawString(this.font, "?", help.x() + 2, help.y() + 1, hovered ? 0xFF0E1116 : TEXT_MUTED, false);
    }

    private void renderListCard(GuiGraphics graphics, int mouseX, int mouseY) {
        String trailing = this.visible.size() + " / " + this.plan.entries().size();
        renderCards(graphics, this.listCard, Component.translatable("eae2_pattern.preview.module.list"), trailing);

        int headerY = this.listCard.y() + CARD_TITLE_H;
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.input"), this.inputIconX,
                headerY, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.output"), this.outputIconX,
                headerY, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.recipe"), this.labelX,
                headerY, TEXT_MUTED, false);

        int rows = rowsPerPage();
        int first = this.page * rows;
        int last = Math.min(this.visible.size(), first + rows);

        graphics.enableScissor(this.rowArea.x(), this.rowArea.y(), this.rowArea.right(), this.rowArea.bottom());
        if (this.visible.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.no_match"),
                    this.rowArea.x() + 6, this.rowArea.y() + 6, TEXT_MUTED, false);
        }
        int hoveredRow = -1;
        if (mouseY >= this.rowArea.y() && mouseY < this.rowArea.bottom()
                && mouseX >= this.rowArea.x() && mouseX < this.rowArea.right()) {
            hoveredRow = (mouseY - this.rowArea.y()) / STEP;
        }
        for (int i = first; i < last; i++) {
            int row = i - first;
            int y = this.rowArea.y() + row * STEP;
            renderRow(graphics, this.visible.get(i), i, y, row == hoveredRow);
        }
        graphics.disableScissor();

        renderListFooter(graphics, mouseX, mouseY);
    }

    private void renderListFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < this.tools.size(); i++) {
            Box box = this.tools.get(i);
            boolean hovered = over(mouseX, mouseY, box);
            if (hovered) {
                UiKit.roundedRect(graphics, box.x() - 2, box.y() + 1, box.width() + 2, box.height() - 2, 2,
                        BUTTON_HOVER);
            }
            String label = Component.translatable("eae2_pattern.preview.tool." + TOOL_KEYS[i]).getString();
            graphics.drawString(this.font, label, box.x(), box.y() + 3, hovered ? TEXT : TEXT_DIM, false);
        }

        String pageLabel = (this.page + 1) + " / " + pageCount();
        graphics.drawString(this.font, pageLabel,
                this.previousPage.x() - 6 - this.font.width(pageLabel), this.listFooter.y() + 3, TEXT_DIM, false);
        drawSmallArrow(graphics, this.previousPage, "<", over(mouseX, mouseY, this.previousPage));
        drawSmallArrow(graphics, this.nextPage, ">", over(mouseX, mouseY, this.nextPage));
    }

    private void drawSmallArrow(GuiGraphics graphics, Box box, String glyph, boolean hovered) {
        UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 2,
                hovered ? BUTTON_HOVER : BUTTON);
        graphics.drawString(this.font, glyph, box.x() + 4, box.y() + 2, hovered ? TEXT : TEXT_DIM, false);
    }

    private void renderRow(GuiGraphics graphics, RecipePlan.Entry entry, int index, int y, boolean hovered) {
        boolean selected = entry.isSelected();
        int left = this.rowArea.x();
        int right = this.rowArea.right();
        // Zebra stripes make consecutive recipes distinguishable when the names are long and similar.
        if (index % 2 == 1) {
            graphics.fill(left, y - 1, right, y - 1 + ROW_H, 0x14FFFFFF);
        }
        if (selected) {
            UiKit.roundedRect(graphics, left, y - 1, right - left, ROW_H, 3, ROW_ON);
            graphics.fill(left, y - 1, left + 2, y - 1 + ROW_H, ACCENT);
        } else if (hovered) {
            UiKit.roundedRect(graphics, left, y - 1, right - left, ROW_H, 3, ROW_HOVER);
        }
        graphics.fill(left, y - 1 + ROW_H, right, y + ROW_H, 0x10FFFFFF);
        UiKit.checkbox(graphics, left + 4, y + 2, CHECKBOX, selected, ACCENT, 0x33FFFFFF, 0x14000000);

        List<Ingredient> inputs = entry.inputs();
        int shownInputs = Math.min(inputs.size(), this.availableInputIcons);
        for (int i = 0; i < shownInputs; i++) {
            drawIngredient(graphics, inputs.get(i), this.inputIconX + i * ICON_STEP, y);
        }
        int arrowX = this.inputIconX + shownInputs * ICON_STEP + 1;
        graphics.drawString(this.font, ">", arrowX, y + 2, TEXT_MUTED, false);

        List<Ingredient> outputs = entry.outputs();
        int shownOutputs = Math.min(outputs.size(), this.availableOutputIcons);
        for (int i = 0; i < shownOutputs; i++) {
            drawIngredient(graphics, outputs.get(i), this.outputIconX + i * ICON_STEP, y);
        }

        int badgeWidth = badgeWidth(entry);
        int textRoom = right - 4 - badgeWidth - this.labelX;
        String label = UiKit.fit(this.font, entry.label().getString(), Math.max(8, textRoom));
        graphics.drawString(this.font, label, this.labelX, y + 2, TEXT, false);
        drawRowBadges(graphics, entry, right - 4 - badgeWidth, y);
    }

    private int badgeWidth(RecipePlan.Entry entry) {
        int width = 0;
        if (entry.hasNeverConsumedInput()) {
            width += this.font.width("NC") + 3;
        }
        if (entry.hasProbabilisticInput()) {
            width += this.font.width("IN%") + 3;
        }
        if (entry.hasProbabilisticOutput()) {
            width += this.font.width("OUT%") + 3;
        }
        if (entry.hasProbabilisticByproduct()) {
            width += this.font.width("BY%") + 3;
        }
        return width;
    }

    private void drawRowBadges(GuiGraphics graphics, RecipePlan.Entry entry, int x, int y) {
        int cursor = x;
        if (entry.hasNeverConsumedInput()) {
            graphics.drawString(this.font, "NC", cursor, y + 2, badgeColor(0), false);
            cursor += this.font.width("NC") + 3;
        }
        if (entry.hasProbabilisticInput()) {
            graphics.drawString(this.font, "IN%", cursor, y + 2, badgeColor(1), false);
            cursor += this.font.width("IN%") + 3;
        }
        if (entry.hasProbabilisticOutput()) {
            graphics.drawString(this.font, "OUT%", cursor, y + 2, badgeColor(2), false);
            cursor += this.font.width("OUT%") + 3;
        }
        if (entry.hasProbabilisticByproduct()) {
            graphics.drawString(this.font, "BY%", cursor, y + 2, badgeColor(3), false);
        }
    }

    private static int badgeColor(int index) {
        return switch (index) {
            case 0 -> 0xFF6EB2FF;
            case 1 -> 0xFFE8AE58;
            case 2 -> 0xFFE87C7C;
            default -> 0xFFD99A5A;
        };
    }

    private void renderDetailCard(GuiGraphics graphics, int mouseX, int mouseY) {
        renderCards(graphics, this.detailCard, Component.translatable("eae2_pattern.preview.module.detail"),
                Component.translatable("eae2_pattern.preview.detail.counter", this.plan.selectedCount()).getString());

        RecipePlan.Entry entry = hoveredEntry(mouseX, mouseY);
        if (entry == null) {
            entry = firstSelected();
        }
        if (entry == null) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.detail.none"),
                    this.detailGrid.x(), this.detailGrid.y() + 4, TEXT_MUTED, false);
            return;
        }

        int inner = this.detailCard.width() - 16;
        int cell = Math.min(20, Math.max(14, (inner - 4) / 3));
        int gridWidth = cell * 3 + 4;
        int gridLeft = this.detailCard.x() + (this.detailCard.width() - gridWidth) / 2;
        int gridTop = this.detailCard.y() + CARD_TITLE_H + 2;
        for (int i = 0; i < 9; i++) {
            int cx = gridLeft + (i % 3) * (cell + 2);
            int cy = gridTop + (i / 3) * (cell + 2);
            UiKit.roundedRect(graphics, cx, cy, cell, cell, 2, 0x1CFFFFFF);
        }
        int slot = 0;
        for (Ingredient input : entry.inputs()) {
            if (slot >= 9) {
                break;
            }
            // Centre the 12px icon inside the slot instead of letting it hug the corner.
            drawIngredient(graphics, input, gridLeft + (slot % 3) * (cell + 2) + (cell - ICON) / 2,
                    gridTop + (slot / 3) * (cell + 2) + (cell - ICON) / 2);
            slot++;
        }
        if (entry.inputs().isEmpty()) {
            String none = "-";
            graphics.drawString(this.font, none, gridLeft + (gridWidth - this.font.width(none)) / 2,
                    gridTop + cell + 1, TEXT_MUTED, false);
        }

        int x = this.detailCard.x() + 8;
        int ty = gridTop + cell * 3 + 5;
        // Title, divider and then label/value rows: the labels are muted, the values brighter, so the
        // block reads as a list instead of a wall of same-weight text.
        graphics.drawString(this.font, UiKit.fit(this.font, entry.label().getString(), inner), x, ty, TEXT, false);
        graphics.fill(x, ty + 10, x + inner, ty + 11, CARD_DIVIDER);

        int row = ty + 14;
        detailRow(graphics, x, row, inner, Component.translatable("eae2_pattern.preview.detail.label.io"),
                Component.translatable("eae2_pattern.preview.detail.io", entry.inputs().size(),
                        entry.outputs().size()));
        row += 11;
        if (row + 9 < this.detailCard.bottom() - 14) {
            detailRow(graphics, x, row, inner, Component.translatable("eae2_pattern.preview.detail.label.kind"),
                    kindName(entry.kind()));
        }
        row += 11;
        if (row + 9 < this.detailCard.bottom() - 14) {
            detailRow(graphics, x, row, inner, Component.translatable("eae2_pattern.preview.detail.label.id"),
                    Component.literal(entry.recipeId().toString()));
        }
        row += 11;
        if (row + 8 < this.detailCard.bottom()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.detail.marks"), x, row,
                    TEXT_MUTED, false);
            drawRowBadges(graphics, entry, x, row + 9);
        }
    }

    /** One label / value pair: muted label on the left, value on the right, both on the same baseline. */
    private void detailRow(GuiGraphics graphics, int x, int y, int width, Component label, Component value) {
        String text = label.getString();
        graphics.drawString(this.font, text, x, y, TEXT_MUTED, false);
        int valueX = x + Math.min(this.font.width(text) + 6, Math.max(18, width / 3));
        graphics.drawString(this.font, UiKit.fit(this.font, value.getString(), x + width - valueX), valueX, y,
                TEXT_DIM, false);
    }

    private void renderActionCard(GuiGraphics graphics, int mouseX, int mouseY) {
        renderCards(graphics, this.actionCard, Component.translatable("eae2_pattern.preview.module.action"),
                this.pending.isEmpty() ? Component.translatable("eae2_pattern.preview.action.none").getString()
                        : Component.translatable("eae2_pattern.preview.action.count", this.pending.size()).getString());

        for (int i = 0; i < this.multiplierCells.size(); i++) {
            Box box = this.multiplierCells.get(i);
            boolean active = MULTIPLIERS[i] == this.multiplier;
            boolean hovered = over(mouseX, mouseY, box);
            UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 2,
                    active ? ACCENT : (hovered ? BUTTON_HOVER : BUTTON));
            String label = MULTIPLIERS[i] + "x";
            graphics.drawString(this.font, label, box.x() + (box.width() - this.font.width(label)) / 2,
                    box.y() + 2, active ? 0xFF0E1116 : TEXT_MUTED, false);
        }

        boolean canGenerate = !this.pending.isEmpty();
        String generateLabel = canGenerate
                ? Component.translatable("eae2_pattern.preview.generate_count", this.pending.size()).getString()
                : Component.translatable("eae2_pattern.preview.generate").getString();
        drawButton(graphics, this.generateButton, generateLabel, canGenerate, over(mouseX, mouseY, this.generateButton),
                true);
        drawButton(graphics, this.cancelButton, Component.translatable("eae2_pattern.preview.cancel").getString(),
                true, over(mouseX, mouseY, this.cancelButton), false);
    }

    private void drawButton(GuiGraphics graphics, Box box, String label, boolean enabled, boolean hovered,
                            boolean accent) {
        int fill;
        int color;
        if (!enabled) {
            fill = 0x22FFFFFF;
            color = TEXT_MUTED;
        } else if (accent) {
            fill = hovered ? 0xFF5FA8FF : ACCENT;
            color = 0xFF0E1116;
        } else {
            fill = hovered ? BUTTON_HOVER : BUTTON;
            color = TEXT_DIM;
        }
        UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 3, fill);
        graphics.drawString(this.font, label, box.x() + (box.width() - this.font.width(label)) / 2,
                box.y() + (box.height() - 8) / 2, color, false);
    }

    private void drawIngredient(GuiGraphics graphics, Ingredient ingredient, int x, int y) {
        StackSpec spec = ingredient.spec();
        if (spec.isItem()) {
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0.0F);
                graphics.pose().scale(ICON / 16.0F, ICON / 16.0F, 1.0F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }
        } else {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
            int tint = 0xFF3F76E4;
            if (fluid != null) {
                try {
                    tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
                } catch (Throwable ignored) {
                    // A fluid without client extensions is not worth a crash.
                }
            }
            graphics.fill(x, y, x + ICON, y + ICON, 0xFF202020);
            graphics.fill(x + 1, y + 1, x + ICON - 1, y + ICON - 1, tint);
        }
        if (ingredient.neverConsumed()) {
            graphics.fill(x, y + ICON - 1, x + ICON, y + ICON, ACCENT);
        } else if (ingredient.probabilistic() || !ingredient.guaranteedOutput()) {
            graphics.fill(x, y + ICON - 1, x + ICON, y + ICON, WARN);
        }
        if (spec.amount() > 1) {
            String amount = formatAmount(spec.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(x + ICON - 1, y + ICON - 1, 200.0F);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(this.font, amount, -this.font.width(amount), -this.font.lineHeight, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    private static String formatAmount(long amount) {
        if (amount < 1000) {
            return Long.toString(amount);
        }
        if (amount < 1_000_000) {
            return (amount / 100) / 10.0D + "k";
        }
        return (amount / 100_000) / 10.0D + "M";
    }

    private RecipePlan.Entry hoveredEntry(int mouseX, int mouseY) {
        if (mouseX < this.rowArea.x() || mouseX >= this.rowArea.right()
                || mouseY < this.rowArea.y() || mouseY >= this.rowArea.bottom()) {
            return null;
        }
        int index = this.page * rowsPerPage() + (mouseY - this.rowArea.y()) / STEP;
        return index >= 0 && index < this.visible.size() ? this.visible.get(index) : null;
    }

    private RecipePlan.Entry firstSelected() {
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (entry.isSelected()) {
                return entry;
            }
        }
        return this.plan.entries().isEmpty() ? null : this.plan.entries().get(0);
    }

    // ------------------------------------------------------------------ tooltip

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (over(mouseX, mouseY, this.productsHelp) || over(mouseX, mouseY, this.ingredientsHelp)) {
            graphics.renderComponentTooltip(this.font, syntaxHelp(), mouseX, mouseY);
            return;
        }
        for (int i = 0; i < this.multiplierCells.size(); i++) {
            if (over(mouseX, mouseY, this.multiplierCells.get(i))) {
                graphics.renderTooltip(this.font, Component.translatable("eae2_pattern.preview.tooltip.multiplier"),
                        mouseX, mouseY);
                return;
            }
        }
        if (over(mouseX, mouseY, this.cancelButton)) {
            graphics.renderTooltip(this.font, Component.translatable("eae2_pattern.preview.tooltip.cancel"),
                    mouseX, mouseY);
            return;
        }
        RecipePlan.Entry entry = hoveredEntry(mouseX, mouseY);
        if (entry == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (Ingredient output : entry.outputs()) {
            lines.add(describe(output, true));
        }
        lines.add(Component.empty());
        for (Ingredient input : entry.inputs()) {
            lines.add(describe(input, false));
        }
        for (Ingredient catalyst : entry.catalysts()) {
            lines.add(Component.translatable("eae2_pattern.preview.tooltip.catalyst",
                    catalyst.spec().amount(), nameOf(catalyst.spec())));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("eae2_pattern.preview.tooltip.kind", kindName(entry.kind()))
                .withStyle(ChatFormatting.GRAY));
        if (this.multiplier > 1) {
            lines.add(Component.translatable(entry.kind() == PatternKind.PROCESSING
                            ? "eae2_pattern.preview.tooltip.scaled"
                            : "eae2_pattern.preview.tooltip.not_scaled", this.multiplier)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal(entry.recipeId().toString()).withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private List<Component> syntaxHelp() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("eae2_pattern.preview.syntax.title").withStyle(ChatFormatting.WHITE));
        for (String key : new String[]{"name", "mod", "tag", "tooltip", "id", "tab", "negate", "or", "quote"}) {
            lines.add(Component.translatable("eae2_pattern.preview.syntax." + key).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Component describe(Ingredient ingredient, boolean output) {
        Component name = nameOf(ingredient.spec());
        if (ingredient.neverConsumed() && !output) {
            return Component.translatable("eae2_pattern.preview.tooltip.catalyst",
                    ingredient.spec().amount(), name);
        }
        if (!ingredient.guaranteedOutput() || ingredient.probabilistic()) {
            return Component.translatable("eae2_pattern.preview.tooltip.chance",
                    ingredient.spec().amount(), name, ingredient.chanceText());
        }
        return Component.translatable("eae2_pattern.preview.tooltip.plain", ingredient.spec().amount(), name);
    }

    private static Component nameOf(StackSpec spec) {
        ItemStack stack = spec.toItemStack();
        return stack.isEmpty() ? Component.literal(spec.id().toString()) : stack.getHoverName();
    }

    private static Component kindName(PatternKind kind) {
        return Component.translatable("eae2_pattern.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (over(mouseX, mouseY, this.closeButton)) {
            onClose();
            return true;
        }
        if (over(mouseX, mouseY, this.previousPage)) {
            turnPage(-1);
            return true;
        }
        if (over(mouseX, mouseY, this.nextPage)) {
            turnPage(1);
            return true;
        }
        if (over(mouseX, mouseY, this.generateButton)) {
            generate();
            return true;
        }
        if (over(mouseX, mouseY, this.cancelButton)) {
            onClose();
            return true;
        }
        for (int i = 0; i < this.multiplierCells.size(); i++) {
            if (over(mouseX, mouseY, this.multiplierCells.get(i))) {
                this.multiplier = MULTIPLIERS[i];
                afterSelectionChanged();
                return true;
            }
        }
        for (int i = 0; i < this.tools.size(); i++) {
            if (over(mouseX, mouseY, this.tools.get(i))) {
                switch (i) {
                    case 0 -> selectVisible(true);
                    case 1 -> invertVisible();
                    case 2 -> dropDuplicates();
                    case 3 -> {
                        this.onlySelected = !this.onlySelected;
                        refresh();
                    }
                    default -> {
                    }
                }
                return true;
            }
        }
        for (int i = 0; i < this.toggles.size(); i++) {
            if (over(mouseX, mouseY, this.toggles.get(i))) {
                dropCondition(i, !conditionValues()[i]);
                return true;
            }
        }
        return clickRow(mouseX, mouseY);
    }

    private boolean clickRow(double mouseX, double mouseY) {
        if (mouseX < this.rowArea.x() || mouseX >= this.rowArea.right()
                || mouseY < this.rowArea.y() || mouseY >= this.rowArea.bottom()) {
            return false;
        }
        int row = (int) ((mouseY - this.rowArea.y()) / STEP);
        int index = this.page * rowsPerPage() + row;
        if (index < 0 || index >= this.visible.size()) {
            return false;
        }
        int rowY = this.rowArea.y() + row * STEP;
        if (mouseY < rowY || mouseY >= rowY + ROW_H) {
            return false;
        }
        RecipePlan.Entry entry = this.visible.get(index);

        int inputIndex = (int) Math.floor((mouseX - this.inputIconX) / (double) ICON_STEP);
        if (inputIndex >= 0 && inputIndex < Math.min(entry.inputs().size(), this.availableInputIcons)
                && mouseX < this.inputIconX + Math.min(entry.inputs().size(), this.availableInputIcons) * ICON_STEP) {
            filterByIcon(entry, inputIndex, false);
            return true;
        }
        int outputIndex = (int) Math.floor((mouseX - this.outputIconX) / (double) ICON_STEP);
        if (outputIndex >= 0 && outputIndex < Math.min(entry.outputs().size(), this.availableOutputIcons)
                && mouseX < this.outputIconX + Math.min(entry.outputs().size(), this.availableOutputIcons)
                * ICON_STEP) {
            filterByIcon(entry, outputIndex, true);
            return true;
        }

        entry.toggle();
        afterSelectionChanged();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (over(mouseX, mouseY, this.rowArea)) {
            turnPage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getFocused() instanceof EditBox box && box.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> {
                turnPage(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> {
                turnPage(1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                generate();
                return true;
            }
            case InputConstants.KEY_ESCAPE -> {
                onClose();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void generate() {
        if (this.pending.isEmpty()) {
            return;
        }
        PatternRequestSender.send(this.pending);
        showNotice(Component.translatable("eae2_pattern.preview.sent", this.pending.size()), false);
    }

    @Override
    public void onGenerationFinished(GenerationReport report) {
        boolean ok = report.reason() == GenerationReport.StopReason.COMPLETED;
        showNotice(ClientGenerationFeedback.describe(report), !ok);
    }

    private void showNotice(Component message, boolean failure) {
        this.notice = message;
        this.noticeFailure = failure;
        this.noticeUntil = Util.getMillis() + 8000L;
    }

    private static boolean over(double mouseX, double mouseY, Box box) {
        return mouseX >= box.x() && mouseX < box.x() + box.width()
                && mouseY >= box.y() && mouseY < box.y() + box.height();
    }

    /** Immutable rectangle used for the hand-drawn controls. */
    private record Box(int x, int y, int width, int height) {
        private static final Box EMPTY = new Box(0, 0, 0, 0);

        private int right() {
            return this.x + this.width;
        }

        private int bottom() {
            return this.y + this.height;
        }

        private int left() {
            return this.x;
        }
    }
}
