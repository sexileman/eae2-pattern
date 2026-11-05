package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Requirement #6: show what would be generated, and let the player narrow it down before spending anything.
 *
 * <p>Laid out like a modern settings window: a dark translucent panel, a filter rail down the left, the recipe list
 * on the right, and a footer that holds the bulk tools, the multiplier and the two actions. Controls are drawn by
 * hand (see {@link UiKit}) because the vanilla button texture reads as a 2011 menu next to a flat dark panel.</p>
 *
 * <p>Every row draws the recipe itself - what goes in, an arrow, what comes out - because one machine routinely makes
 * the same item in several ways: a GregTech bending machine turns two iron ingots into a double iron plate, and it
 * also turns two iron plates into one. Only the ingredients tell those rows apart.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    private static final int PAD = 14;
    private static final int HEADER_H = 34;
    private static final int FOOTER_H = 52;
    private static final int SIDEBAR_W = 178;
    private static final int ROW_H = 26;
    private static final int ROW_STEP = 28;
    private static final int ICON = 16;
    private static final int ICON_STEP = 18;
    private static final int MAX_INPUT_ICONS = 3;
    private static final int MAX_OUTPUT_ICONS = 2;

    private static final int SCRIM = 0xC4000000;
    private static final int PANEL = 0xF21B1C1F;
    private static final int PANEL_BORDER = 0xFF34363D;
    private static final int DIVIDER = 0xFF2C2E34;
    private static final int CARD = 0xFF23242A;
    private static final int CARD_HOVER = 0xFF2D2F36;
    private static final int CARD_SELECTED = 0xFF1D2A3A;
    private static final int ACCENT = 0xFF4C9AFF;
    private static final int ACCENT_SOFT = 0x3D4C9AFF;
    private static final int TEXT = 0xFFE9EBEF;
    private static final int TEXT_DIM = 0xFF9AA0AA;
    private static final int TEXT_MUTED = 0xFF70757F;
    private static final int OK = 0xFF5FC97A;
    private static final int WARN = 0xFFE0A040;
    private static final int BAD = 0xFFE06A6A;
    private static final int TRACK = 0xFF3A3D44;
    private static final int KNOB = 0xFFF2F4F8;
    private static final int FIELD = 0xFF15161A;
    private static final int FIELD_BORDER = 0xFF35373E;
    private static final int GHOST = 0x00000000;
    private static final int GHOST_HOVER = 0x22FFFFFF;

    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};

    private final RecipePlan plan;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();

    @Nullable
    private EditBox productsBox;
    @Nullable
    private EditBox ingredientsBox;

    private ItemSearchQuery productsQuery = ItemSearchQuery.empty();
    private ItemSearchQuery ingredientsQuery = ItemSearchQuery.empty();

    private boolean onlyNeverConsumed;
    private boolean onlyProbabilisticInput;
    private boolean onlyProbabilisticOutput;
    private boolean onlyProbabilisticByproduct;
    private boolean onlySelected;
    private boolean dropChanceOutputs;
    private boolean dropNeverConsumed = true;
    private int multiplier = 1;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int sidebarLeft;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int footerTop;
    private int page;

    private Box previousPage = Box.EMPTY;
    private Box nextPage = Box.EMPTY;
    private final List<Box> toggles = new ArrayList<>();
    private final List<Box> multiplierCells = new ArrayList<>();
    private final List<Box> tools = new ArrayList<>();
    private Box generateButton = Box.EMPTY;
    private Box cancelButton = Box.EMPTY;
    private Box listBox = Box.EMPTY;
    private Box scrollBar = Box.EMPTY;

    @Nullable
    private Component lastResult;

    public PreviewScreen(RecipePlan plan) {
        super(Component.translatable("eae2_pattern.preview.title", plan.machineName()));
        this.plan = plan;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(566, this.width - 20);
        this.panelHeight = Math.min(348, this.height - 16);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.sidebarLeft = this.panelLeft + PAD;
        this.listLeft = this.sidebarLeft + SIDEBAR_W + PAD;
        this.listRight = this.panelLeft + this.panelWidth - PAD - 6;
        this.listTop = this.panelTop + HEADER_H + 8;
        this.footerTop = this.panelTop + this.panelHeight - FOOTER_H;
        this.listBottom = this.footerTop - 8;
        this.listBox = new Box(this.listLeft - 4, this.listTop - 4,
                this.listRight - this.listLeft + 8, this.listBottom - this.listTop + 8);

        this.nextPage = new Box(this.panelLeft + this.panelWidth - PAD - 20, this.panelTop + 9, 20, 16);
        this.previousPage = new Box(this.nextPage.x() - 24, this.panelTop + 9, 20, 16);

        int fieldTop = this.panelTop + HEADER_H + 26;
        this.productsBox = createField(this.sidebarLeft + 6, fieldTop, SIDEBAR_W - 12, text -> {
            this.productsQuery = ItemSearchQuery.parse(text);
            rebuildVisible();
        });
        this.ingredientsBox = createField(this.sidebarLeft + 6, fieldTop + 42, SIDEBAR_W - 12, text -> {
            this.ingredientsQuery = ItemSearchQuery.parse(text);
            rebuildVisible();
        });

        layoutToggles();
        layoutMultiplier();
        layoutFooter();

        this.page = Mth.clamp(this.page, 0, pageCount() - 1);
        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
        rebuildVisible();
    }

    private EditBox createField(int x, int y, int width, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(this.font, x, y, width, 16, Component.empty());
        box.setBordered(false);
        box.setMaxLength(96);
        box.setTextColor(TEXT);
        box.setTextColorUneditable(TEXT_MUTED);
        box.setHint(Component.translatable("eae2_pattern.preview.filter.hint"));
        box.setResponder(responder);
        this.addRenderableWidget(box);
        return box;
    }

    private void layoutToggles() {
        this.toggles.clear();
        int top = this.panelTop + HEADER_H + 112;
        int cellWidth = SIDEBAR_W / 2;
        for (int i = 0; i < 6; i++) {
            int column = i % 2;
            int row = i / 2;
            this.toggles.add(new Box(this.sidebarLeft + column * cellWidth, top + row * 18, cellWidth - 4, 16));
        }
    }

    private void layoutMultiplier() {
        this.multiplierCells.clear();
        int cellWidth = 20;
        int startX = this.panelLeft + this.panelWidth - PAD - MULTIPLIERS.length * cellWidth;
        int y = this.footerTop + 4;
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            this.multiplierCells.add(new Box(startX + i * cellWidth, y, cellWidth - 2, 18));
        }
    }

    private void layoutFooter() {
        this.tools.clear();
        int y = this.footerTop + 4;
        int[] widths = {38, 42, 38, 50, 56};
        int x = this.sidebarLeft;
        for (int width : widths) {
            this.tools.add(new Box(x, y, width, 18));
            x += width + 5;
        }
        int actionY = this.footerTop + 28;
        this.generateButton = new Box(this.panelLeft + this.panelWidth - PAD - 74, actionY, 74, 20);
        this.cancelButton = new Box(this.generateButton.x() - 58, actionY, 54, 20);
    }

    private static int nextMultiplier(int current) {
        for (int value : MULTIPLIERS) {
            if (value > current) {
                return value;
            }
        }
        return MULTIPLIERS[0];
    }

    private RecipeFilters filters() {
        return new RecipeFilters(this.productsQuery, this.ingredientsQuery, this.onlyNeverConsumed,
                this.onlyProbabilisticInput, this.onlyProbabilisticOutput, this.onlyProbabilisticByproduct,
                this.onlySelected);
    }

    private void rebuildVisible() {
        RecipeFilters filters = filters();
        this.visible.clear();
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (!filters.rejects(entry)) {
                this.visible.add(entry);
            }
        }
        this.page = Mth.clamp(this.page, 0, Math.max(0, pageCount() - 1));
    }

    private int rowsPerPage() {
        return Math.max(1, (this.listBottom - this.listTop + 2) / ROW_STEP);
    }

    private int pageCount() {
        return Math.max(1, (this.visible.size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void turnPage(int delta) {
        this.page = Mth.clamp(this.page + delta, 0, pageCount() - 1);
    }

    private void selectVisible(boolean value) {
        for (RecipePlan.Entry entry : this.visible) {
            entry.setSelected(value);
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
            if (!entry.isSelected()) {
                continue;
            }
            if (!seen.add(entry.outputKey())) {
                entry.setSelected(false);
                dropped++;
            }
        }
        this.lastResult = Component.translatable("eae2_pattern.preview.deduped", dropped);
        afterSelectionChanged();
    }

    private void afterSelectionChanged() {
        if (this.onlySelected) {
            rebuildVisible();
        }
    }

    private List<PatternSpec> buildSpecs() {
        return this.plan.selectedSpecs(this.multiplier, this.dropChanceOutputs, this.dropNeverConsumed);
    }

    private void generate() {
        List<PatternSpec> specs = buildSpecs();
        if (specs.isEmpty()) {
            this.lastResult = Component.translatable("eae2_pattern.preview.nothing_selected");
            return;
        }
        PatternRequestSender.send(specs);
        this.lastResult = Component.translatable("eae2_pattern.preview.sent", specs.size());
    }

    @Override
    public void onGenerationFinished(GenerationReport report) {
        this.lastResult = ClientGenerationFeedback.describe(report);
    }

    @Override
    public void onClose() {
        ClientGenerationFeedback.setListener(null);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, SCRIM);

        UiKit.roundedFrame(graphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, 9,
                PANEL, PANEL_BORDER);

        renderHeader(graphics, mouseX, mouseY);
        renderSidebar(graphics, mouseX, mouseY);
        renderList(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        renderResult(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.panelLeft + PAD;
        UiKit.text(graphics, this.font,
                Component.literal(UiKit.fit(this.font, this.title.getString(), this.panelWidth - 150)),
                left, this.panelTop + 9, TEXT);

        int selected = this.plan.selectedCount();
        Component status = Component.translatable("eae2_pattern.preview.status", selected, this.visible.size(),
                this.plan.entries().size());
        UiKit.text(graphics, this.font, status, left, this.panelTop + 21, selected == 0 ? TEXT_MUTED : TEXT_DIM);

        boolean hasNetwork = ClientNetworkState.hasNetwork();
        long blanks = ClientNetworkState.blankPatterns();
        Component right;
        int color;
        if (!hasNetwork) {
            right = Component.translatable("eae2_pattern.preview.no_network");
            color = BAD;
        } else if (selected > blanks) {
            right = Component.translatable("eae2_pattern.preview.not_enough_blanks", blanks);
            color = WARN;
        } else {
            right = Component.translatable("eae2_pattern.preview.blanks", blanks);
            color = blanks > 0 ? OK : BAD;
        }
        int rightLimit = this.previousPage.x() - 10;
        String fitted = UiKit.fit(this.font, right.getString(), rightLimit - left - 90);
        graphics.drawString(this.font, fitted, rightLimit - this.font.width(fitted), this.panelTop + 21, color, false);

        drawPageButton(graphics, this.previousPage, "<", over(mouseX, mouseY, this.previousPage),
                this.page > 0);
        drawPageButton(graphics, this.nextPage, ">", over(mouseX, mouseY, this.nextPage),
                this.page < pageCount() - 1);

        String pageLabel = (this.page + 1) + " / " + pageCount();
        graphics.drawString(this.font, pageLabel, this.previousPage.x() - 8 - this.font.width(pageLabel),
                this.panelTop + 14, TEXT_DIM, false);

        graphics.fill(this.panelLeft + PAD, this.panelTop + HEADER_H - 1,
                this.panelLeft + this.panelWidth - PAD, this.panelTop + HEADER_H, DIVIDER);
    }

    private void drawPageButton(GuiGraphics graphics, Box box, String glyph, boolean hovered, boolean enabled) {
        if (enabled) {
            UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 5,
                    hovered ? CARD_HOVER : CARD);
            graphics.drawString(this.font, glyph, box.x() + box.width() / 2 - 2, box.y() + 4, TEXT, false);
        } else {
            UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 5, 0x33232529);
            graphics.drawString(this.font, glyph, box.x() + box.width() / 2 - 2, box.y() + 4, TEXT_MUTED, false);
        }
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.sidebarLeft;
        int headerBottom = this.panelTop + HEADER_H;

        UiKit.caption(graphics, this.font, Component.translatable("eae2_pattern.preview.section.search"),
                x, headerBottom + 10, TEXT_MUTED);

        UiKit.text(graphics, this.font, Component.translatable("eae2_pattern.preview.filter.products"),
                x, headerBottom + 22, TEXT_DIM);
        drawField(graphics, this.productsBox);

        UiKit.text(graphics, this.font, Component.translatable("eae2_pattern.preview.filter.ingredients"),
                x, headerBottom + 64, TEXT_DIM);
        drawField(graphics, this.ingredientsBox);

        UiKit.caption(graphics, this.font, Component.translatable("eae2_pattern.preview.section.conditions"),
                x, headerBottom + 102, TEXT_MUTED);

        boolean[] values = {this.onlyNeverConsumed, this.onlyProbabilisticInput, this.onlyProbabilisticOutput,
                this.onlyProbabilisticByproduct, this.dropChanceOutputs, this.dropNeverConsumed};
        String[] keys = {"unconsumed", "chance_input", "chance_output", "chance_byproduct", "drop_chance",
                "drop_unconsumed"};
        for (int i = 0; i < values.length; i++) {
            Box box = this.toggles.get(i);
            boolean hovered = over(mouseX, mouseY, box);
            if (hovered) {
                UiKit.roundedRect(graphics, box.x() - 2, box.y() - 1, box.width() + 4, box.height() + 2, 5, GHOST_HOVER);
            }
            UiKit.toggle(graphics, box.x(), box.y(), values[i], values[i] ? ACCENT : TRACK, TRACK, KNOB);
            Component label = Component.translatable("eae2_pattern.preview.filter." + keys[i]);
            String fitted = UiKit.fit(this.font, label.getString(), box.width() - 36);
            graphics.drawString(this.font, fitted, box.x() + 34, box.y() + 5, values[i] ? TEXT : TEXT_DIM, false);
        }
    }

    private void drawField(GuiGraphics graphics, @Nullable EditBox box) {
        if (box == null) {
            return;
        }
        int x = box.getX() - 6;
        int y = box.getY() - 4;
        int width = box.getWidth() + 12;
        boolean focused = box.isFocused();
        UiKit.roundedFrame(graphics, x, y, width, box.getHeight() + 8, 5, FIELD, focused ? ACCENT : FIELD_BORDER);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        UiKit.roundedRect(graphics, this.listBox.x(), this.listBox.y(), this.listBox.width(), this.listBox.height(),
                6, 0x26FFFFFF);

        graphics.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);
        int rows = rowsPerPage();
        int start = this.page * rows;
        for (int row = 0; row < rows; row++) {
            int index = start + row;
            if (index >= this.visible.size()) {
                break;
            }
            int y = this.listTop + row * ROW_STEP;
            boolean hovered = mouseX >= this.listLeft && mouseX < this.listRight
                    && mouseY >= y && mouseY < y + ROW_H;
            renderRow(graphics, this.visible.get(index), y, hovered);
        }
        graphics.disableScissor();

        if (this.visible.isEmpty()) {
            Component empty = Component.translatable("eae2_pattern.preview.no_match");
            graphics.drawString(this.font, empty, this.listLeft + 10, this.listTop + 10, TEXT_MUTED, false);
        }

        renderScrollBar(graphics);
    }

    private void renderScrollBar(GuiGraphics graphics) {
        this.scrollBar = new Box(this.listRight + 1, this.listTop, 4, this.listBottom - this.listTop);
        UiKit.roundedRect(graphics, this.scrollBar.x(), this.scrollBar.y(), this.scrollBar.width(),
                this.scrollBar.height(), 2, 0x22FFFFFF);
        int pages = pageCount();
        if (pages <= 1) {
            return;
        }
        int trackHeight = this.scrollBar.height();
        int thumbHeight = Math.max(16, trackHeight / pages);
        int travel = trackHeight - thumbHeight;
        int thumbY = this.scrollBar.y() + (pages <= 1 ? 0 : travel * this.page / (pages - 1));
        UiKit.roundedRect(graphics, this.scrollBar.x(), thumbY, this.scrollBar.width(), thumbHeight, 2, ACCENT);
    }

    private void renderRow(GuiGraphics graphics, RecipePlan.Entry entry, int y, boolean hovered) {
        int x = this.listLeft;
        int width = this.listRight - this.listLeft;
        boolean selected = entry.isSelected();
        int fill = selected ? CARD_SELECTED : (hovered ? CARD_HOVER : 0x00000000);
        if (fill != 0) {
            UiKit.roundedRect(graphics, x, y, width, ROW_H, 5, fill);
        }
        if (selected) {
            UiKit.roundedRect(graphics, x, y + 4, 3, ROW_H - 8, 1, ACCENT);
        }

        UiKit.checkbox(graphics, x + 9, y + 7, selected, ACCENT, 0x55151519, 0xFF4A4D55);

        int iconY = y + 5;
        int cursor = x + 27;
        List<Ingredient> inputs = entry.inputs();
        int shownInputs = Math.min(inputs.size(), MAX_INPUT_ICONS);
        for (int i = 0; i < shownInputs; i++) {
            drawIngredient(graphics, inputs.get(i), cursor + i * ICON_STEP, iconY);
        }
        if (inputs.size() > shownInputs) {
            graphics.drawString(this.font, "+" + (inputs.size() - shownInputs),
                    cursor + shownInputs * ICON_STEP, iconY + 5, TEXT_MUTED, false);
        }
        cursor += MAX_INPUT_ICONS * ICON_STEP;

        graphics.drawString(this.font, "\u2192", cursor + 1, y + 10, TEXT_MUTED, false);
        cursor += 13;

        List<Ingredient> outputs = entry.outputs();
        int shownOutputs = Math.min(outputs.size(), MAX_OUTPUT_ICONS);
        for (int i = 0; i < shownOutputs; i++) {
            drawIngredient(graphics, outputs.get(i), cursor + i * ICON_STEP, iconY);
        }
        cursor += MAX_OUTPUT_ICONS * ICON_STEP + 4;

        int badges = (entry.hasProbabilisticOutput() ? 1 : 0) + (entry.hasProbabilisticInput() ? 1 : 0)
                + (entry.hasNeverConsumedInput() ? 1 : 0);
        int badgeWidth = badges * 18;
        int labelWidth = Math.max(20, width - (cursor - x) - badgeWidth - 12);
        String label = UiKit.fit(this.font, entry.label().getString(), labelWidth);
        graphics.drawString(this.font, label, cursor, y + 10, selected ? TEXT : TEXT_DIM, false);

        int badgeX = x + width - 8;
        if (entry.hasNeverConsumedInput()) {
            badgeX = drawBadge(graphics, badgeX, y + 10, ACCENT,
                    Component.translatable("eae2_pattern.preview.badge.unconsumed").getString());
        }
        if (entry.hasProbabilisticInput()) {
            badgeX = drawBadge(graphics, badgeX, y + 10, WARN,
                    Component.translatable("eae2_pattern.preview.badge.chance_input").getString());
        }
        if (entry.hasProbabilisticOutput()) {
            badgeX = drawBadge(graphics, badgeX, y + 10, BAD,
                    Component.translatable("eae2_pattern.preview.badge.chance_output").getString());
        }
    }

    /** Small tinted chip; returns the x the next chip should end at. */
    private int drawBadge(GuiGraphics graphics, int rightEdge, int y, int color, String text) {
        int width = this.font.width(text) + 6;
        int x = rightEdge - width;
        UiKit.roundedRect(graphics, x, y, width, 10, 3, (color & 0x00FFFFFF) | 0x40000000);
        graphics.drawString(this.font, text, x + 3, y + 2, color, false);
        return x - 4;
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(this.panelLeft + PAD, this.footerTop, this.panelLeft + this.panelWidth - PAD,
                this.footerTop + 1, DIVIDER);

        String[] keys = {"select_all", "select_none", "invert", "dedupe", "only_selected"};
        for (int i = 0; i < this.tools.size(); i++) {
            Box box = this.tools.get(i);
            boolean hovered = over(mouseX, mouseY, box);
            String text = Component.translatable("eae2_pattern.preview." + keys[i]).getString();
            boolean active = i == 4 && this.onlySelected;
            if (hovered || active) {
                UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 4,
                        active ? ACCENT_SOFT : GHOST_HOVER);
            }
            graphics.drawString(this.font, text, box.x() + (box.width() - this.font.width(text)) / 2,
                    box.y() + 5, active ? TEXT : TEXT_DIM, false);
        }

        String caption = Component.translatable("eae2_pattern.preview.multiplier_caption").getString();
        graphics.drawString(this.font, caption, this.multiplierCells.get(0).x() - this.font.width(caption) - 6,
                this.footerTop + 8, TEXT_MUTED, false);
        for (int i = 0; i < this.multiplierCells.size(); i++) {
            Box box = this.multiplierCells.get(i);
            boolean on = MULTIPLIERS[i] == this.multiplier;
            boolean hovered = over(mouseX, mouseY, box);
            UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 4,
                    on ? ACCENT : (hovered ? CARD_HOVER : CARD));
            String text = MULTIPLIERS[i] + "x";
            graphics.drawString(this.font, text, box.x() + (box.width() - this.font.width(text)) / 2,
                    box.y() + 5, on ? 0xFF101418 : TEXT_DIM, false);
        }

        boolean canGenerate = !buildSpecs().isEmpty();
        boolean generateHovered = over(mouseX, mouseY, this.generateButton);
        drawActionButton(graphics, this.generateButton,
                Component.translatable("eae2_pattern.preview.generate").getString(), canGenerate, generateHovered, true);
        drawActionButton(graphics, this.cancelButton,
                Component.translatable("eae2_pattern.preview.cancel").getString(), true,
                over(mouseX, mouseY, this.cancelButton), false);
    }

    private void drawActionButton(GuiGraphics graphics, Box box, String text, boolean enabled, boolean hovered,
                                  boolean accent) {
        int fill;
        int textColor;
        if (!enabled) {
            fill = 0x33232529;
            textColor = TEXT_MUTED;
        } else if (accent) {
            fill = hovered ? 0xFF5FA8FF : ACCENT;
            textColor = 0xFF0E1116;
        } else {
            fill = hovered ? CARD_HOVER : CARD;
            textColor = TEXT;
        }
        UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 5, fill);
        graphics.drawString(this.font, text, box.x() + (box.width() - this.font.width(text)) / 2,
                box.y() + 6, textColor, false);
    }

    private void renderResult(GuiGraphics graphics) {
        if (this.lastResult == null) {
            return;
        }
        int width = this.cancelButton.x() - this.sidebarLeft - 10;
        String text = UiKit.fit(this.font, this.lastResult.getString(), width);
        graphics.drawString(this.font, text, this.sidebarLeft, this.footerTop + 34, TEXT_DIM, false);
    }

    // ---------------------------------------------------------------- ingredients

    private void drawIngredient(GuiGraphics graphics, Ingredient ingredient, int x, int y) {
        StackSpec spec = ingredient.spec();
        if (spec.isItem()) {
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
            }
        } else {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
            int tint = 0xFF3F76E4;
            if (fluid != null) {
                try {
                    tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
                } catch (Throwable ignored) {
                    // A fluid type without client extensions is not worth a crash.
                }
            }
            graphics.fill(x, y, x + ICON, y + ICON, 0xFF202020);
            graphics.fill(x + 1, y + 1, x + ICON - 1, y + ICON - 1, tint);
        }
        drawAmount(graphics, spec.amount(), x, y);
        if (ingredient.neverConsumed()) {
            UiKit.roundedRect(graphics, x, y + ICON - 2, ICON, 2, 1, ACCENT);
        } else if (ingredient.probabilistic() || !ingredient.guaranteedOutput()) {
            UiKit.roundedRect(graphics, x, y + ICON - 2, ICON, 2, 1, WARN);
        }
    }

    private void drawAmount(GuiGraphics graphics, long amount, int x, int y) {
        if (amount <= 1) {
            return;
        }
        String text = formatAmount(amount);
        graphics.pose().pushPose();
        graphics.pose().translate(x + ICON - 1, y + ICON - 1, 200.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(this.font, text, -this.font.width(text), -this.font.lineHeight, 0xFFFFFFFF, true);
        graphics.pose().popPose();
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

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < this.listLeft || mouseX >= this.listRight || mouseY < this.listTop || mouseY >= this.listBottom) {
            return;
        }
        int index = this.page * rowsPerPage() + (mouseY - this.listTop) / ROW_STEP;
        if (index < 0 || index >= this.visible.size()) {
            return;
        }
        RecipePlan.Entry entry = this.visible.get(index);
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

    private Component describe(Ingredient ingredient, boolean output) {
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

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (over(mouseX, mouseY, this.previousPage) && this.page > 0) {
            turnPage(-1);
            return true;
        }
        if (over(mouseX, mouseY, this.nextPage) && this.page < pageCount() - 1) {
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
                return true;
            }
        }
        for (int i = 0; i < this.tools.size(); i++) {
            if (over(mouseX, mouseY, this.tools.get(i))) {
                runTool(i);
                return true;
            }
        }
        for (int i = 0; i < this.toggles.size(); i++) {
            if (over(mouseX, mouseY, this.toggles.get(i))) {
                toggleCondition(i);
                return true;
            }
        }
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom) {
            int index = this.page * rowsPerPage() + (int) ((mouseY - this.listTop) / ROW_STEP);
            if (index >= 0 && index < this.visible.size()) {
                this.visible.get(index).toggle();
                afterSelectionChanged();
                return true;
            }
        }
        return false;
    }

    private void runTool(int index) {
        switch (index) {
            case 0 -> selectVisible(true);
            case 1 -> selectVisible(false);
            case 2 -> invertVisible();
            case 3 -> dropDuplicates();
            case 4 -> {
                this.onlySelected = !this.onlySelected;
                rebuildVisible();
            }
            default -> {
            }
        }
    }

    private void toggleCondition(int index) {
        switch (index) {
            case 0 -> this.onlyNeverConsumed = !this.onlyNeverConsumed;
            case 1 -> this.onlyProbabilisticInput = !this.onlyProbabilisticInput;
            case 2 -> this.onlyProbabilisticOutput = !this.onlyProbabilisticOutput;
            case 3 -> this.onlyProbabilisticByproduct = !this.onlyProbabilisticByproduct;
            case 4 -> this.dropChanceOutputs = !this.dropChanceOutputs;
            case 5 -> this.dropNeverConsumed = !this.dropNeverConsumed;
            default -> {
            }
        }
        rebuildVisible();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= this.listBox.x() && mouseX < this.listBox.x() + this.listBox.width()
                && mouseY >= this.listTop && mouseY < this.listBottom) {
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
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private static boolean over(double mouseX, double mouseY, Box box) {
        return mouseX >= box.x() && mouseX < box.x() + box.width()
                && mouseY >= box.y() && mouseY < box.y() + box.height();
    }

    /** Immutable rectangle used for the hand-drawn controls. */
    private record Box(int x, int y, int width, int height) {
        private static final Box EMPTY = new Box(0, 0, 0, 0);
    }
}
