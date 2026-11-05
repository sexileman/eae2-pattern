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
 * <p>Three columns: a filter rail on the left (two search boxes, then seven conditions), the recipe list on the
 * right, and a footer with the bulk tools, the multiplier and the two actions. Every rectangle is computed once in
 * {@link #layout()} from the real screen size, and both drawing and hit testing read that same set of rectangles -
 * an earlier version placed them independently, which made clicks land on the wrong control once the panel shrank.</p>
 *
 * <p>Every row draws the recipe itself - what goes in, an arrow, what comes out - because one machine routinely makes
 * the same item in several ways: a GregTech bending machine turns two iron ingots into a double iron plate, and it
 * also turns two iron plates into one. Only the ingredients tell those rows apart.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    private static final int PAD = 12;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 46;
    private static final int COLUMN_H = 12;
    private static final int ROW_H = 20;
    private static final int STEP = 22;
    private static final int ICON = 16;
    private static final int ICON_STEP = 17;
    private static final int CHECKBOX = 10;

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
    private static final int GHOST_HOVER = 0x22FFFFFF;

    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};

    /** Order of the six condition check boxes, matching the keys in the language files. */
    private static final String[] CONDITION_KEYS = {
            "unconsumed", "chance_input", "chance_output", "chance_byproduct",
            "drop_chance", "drop_unconsumed",
    };

    private static final String[] TOOL_KEYS = {"select_all", "select_none", "invert", "dedupe", "only_selected"};
    private static final int[] TOOL_WIDTHS = {38, 46, 38, 46, 54};

    private final RecipePlan plan;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();

    /** The specs that the generate button would send right now; rebuilt only when something changes. */
    private List<PatternSpec> pending = List.of();

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
    private int sidebarWidth;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int footerTop;
    private int captionSearchY;
    private int productsY;
    private int ingredientsY;
    private int matchY;
    private int captionCondY;
    private boolean showCaptions;
    private boolean showMatch;
    private int page;

    private Box previousPage = Box.EMPTY;
    private Box nextPage = Box.EMPTY;
    private final List<Box> toggles = new ArrayList<>();
    private final List<Box> tools = new ArrayList<>();
    private Box multiplierButton = Box.EMPTY;
    private Box generateButton = Box.EMPTY;
    private Box cancelButton = Box.EMPTY;
    private Box listBox = Box.EMPTY;
    private Box scrollBar = Box.EMPTY;
    @Nullable
    private Component notice;

    private int inputIconX;
    private int outputIconX;
    private int labelX;
    private int availableInputIcons;
    private int availableOutputIcons;

    public PreviewScreen(RecipePlan plan) {
        super(Component.translatable("eae2_pattern.preview.title", plan.machineName()));
        this.plan = plan;
    }

    @Override
    protected void init() {
        layout();
        this.productsBox = createField(this.productsY,
                Component.translatable("eae2_pattern.preview.filter.products_hint"), text -> {
                    this.productsQuery = ItemSearchQuery.parse(text);
                    refresh();
                });
        this.ingredientsBox = createField(this.ingredientsY,
                Component.translatable("eae2_pattern.preview.filter.ingredients_hint"), text -> {
                    this.ingredientsQuery = ItemSearchQuery.parse(text);
                    refresh();
                });
        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
        refresh();
    }

    // ---------------------------------------------------------------- layout

    /**
     * Computes every rectangle this screen uses. Called once per resize, before anything is drawn or clicked.
     */
    private void layout() {
        this.panelWidth = Mth.clamp(this.width - 16, 300, 620);
        this.panelHeight = Mth.clamp(this.height - 12, 172, 380);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int headerBottom = this.panelTop + HEADER_H;
        this.footerTop = this.panelTop + this.panelHeight - FOOTER_H;
        int contentTop = headerBottom + 5;
        int contentBottom = this.footerTop - 5;

        this.sidebarWidth = Mth.clamp(Math.round(this.panelWidth * 0.34F), 136, 208);
        this.sidebarLeft = this.panelLeft + PAD;
        this.listLeft = this.sidebarLeft + this.sidebarWidth + PAD;
        this.listRight = this.panelLeft + this.panelWidth - PAD - 7;
        this.listTop = contentTop + COLUMN_H;
        this.listBottom = contentBottom;
        this.listBox = new Box(this.listLeft - 3, contentTop - 3,
                this.listRight - this.listLeft + 10, this.listBottom - contentTop + 6);

        this.nextPage = new Box(this.panelLeft + this.panelWidth - PAD - 18, this.panelTop + 7, 18, 14);
        this.previousPage = new Box(this.nextPage.x() - 22, this.panelTop + 7, 18, 14);

        // The sidebar has to survive small logical resolutions (GUI scale 4 on a 1280x720 window is 320x180),
        // so the labels are dropped before the controls are, and the check box rows shrink rather than spill
        // over the footer. Anything that would not fit used to overlap the footer buttons and eat their clicks.
        int avail = contentBottom - contentTop;
        this.showCaptions = avail >= 116;
        this.showMatch = avail >= 80;

        int y = contentTop;
        this.captionSearchY = y;
        if (this.showCaptions) {
            y += 11;
        }
        this.productsY = y;
        y += 20;
        this.ingredientsY = y;
        y += 20;
        this.matchY = y;
        if (this.showMatch) {
            y += 11;
        }
        this.captionCondY = y;
        if (this.showCaptions) {
            y += 11;
        }

        int rows = (CONDITION_KEYS.length + 1) / 2;
        int rowHeight = Mth.clamp((contentBottom - y) / rows, 12, 18);
        this.toggles.clear();
        int half = this.sidebarWidth / 2;
        for (int i = 0; i < CONDITION_KEYS.length; i++) {
            int column = i % 2;
            int row = i / 2;
            this.toggles.add(new Box(this.sidebarLeft + column * half, y + row * rowHeight,
                    half - 3, rowHeight - 1));
        }

        int toolY = this.footerTop + 5;
        this.tools.clear();
        int x = this.sidebarLeft;
        for (int width : TOOL_WIDTHS) {
            this.tools.add(new Box(x, toolY, width, 16));
            x += width + 4;
        }
        int actionY = this.footerTop + 25;
        this.generateButton = new Box(this.panelLeft + this.panelWidth - PAD - 66, actionY, 66, 16);
        this.cancelButton = new Box(this.generateButton.x() - 52, actionY, 46, 16);
        this.multiplierButton = new Box(this.sidebarLeft, actionY, 54, 16);

        this.inputIconX = this.listLeft + CHECKBOX + 9;
        int labelRoom = this.listRight - this.listLeft;
        this.availableInputIcons = labelRoom >= 250 ? 3 : (labelRoom >= 190 ? 2 : 1);
        this.availableOutputIcons = labelRoom >= 250 ? 2 : 1;
        this.outputIconX = this.inputIconX + this.availableInputIcons * ICON_STEP + 13;
        this.labelX = this.outputIconX + this.availableOutputIcons * ICON_STEP + 3;
    }

    private void refresh() {
        RecipeFilters filters = new RecipeFilters(this.productsQuery, this.ingredientsQuery,
                this.onlyNeverConsumed, this.onlyProbabilisticInput, this.onlyProbabilisticOutput,
                this.onlyProbabilisticByproduct, this.onlySelected);
        this.visible.clear();
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (!filters.rejects(entry)) {
                this.visible.add(entry);
            }
        }
        this.page = Mth.clamp(this.page, 0, pageCount() - 1);
        recomputePending();
    }

    private void recomputePending() {
        this.pending = this.plan.selectedSpecs(this.multiplier, this.dropChanceOutputs,
                this.dropNeverConsumed);
    }

    private int rowsPerPage() {
        return Math.max(1, (this.listBottom - this.listTop + 2) / STEP);
    }

    private int pageCount() {
        return Math.max(1, (this.visible.size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void turnPage(int delta) {
        this.page = Mth.clamp(this.page + delta, 0, pageCount() - 1);
    }

    // ---------------------------------------------------------------- controls

    private EditBox createField(int y, Component hint, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(this.font, this.sidebarLeft + 5, y + 2, this.sidebarWidth - 10, 13,
                Component.empty());
        box.setBordered(false);
        box.setMaxLength(64);
        box.setTextColor(TEXT);
        box.setTextColorUneditable(TEXT_MUTED);
        box.setHint(hint);
        box.setResponder(responder);
        this.addRenderableWidget(box);
        return box;
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
        for (RecipePlan.Entry entry : this.visible) {
            if (entry.isSelected() && !seen.add(entry.outputKey())) {
                entry.setSelected(false);
            }
        }
        afterSelectionChanged();
    }

    private void afterSelectionChanged() {
        if (this.onlySelected) {
            refresh();
        } else {
            recomputePending();
        }
    }

    private void generate() {
        List<PatternSpec> specs = this.pending;
        if (specs.isEmpty()) {
            this.notice = Component.translatable("eae2_pattern.preview.nothing_selected");
            return;
        }
        PatternRequestSender.send(specs);
        this.notice = Component.translatable("eae2_pattern.preview.sent", specs.size());
    }

    private void cycleMultiplier() {
        this.multiplier = this.multiplier >= MULTIPLIERS[MULTIPLIERS.length - 1]
                ? MULTIPLIERS[0] : nextMultiplier(this.multiplier);
        recomputePending();
    }

    private static int nextMultiplier(int current) {
        for (int value : MULTIPLIERS) {
            if (value > current) {
                return value;
            }
        }
        return MULTIPLIERS[0];
    }

    /** Clicking an icon in a row filters by that item, which also works when an IME swallows typed characters. */
    private void filterByIcon(RecipePlan.Entry entry, int index, boolean output) {
        List<Ingredient> list = output ? entry.outputs() : entry.inputs();
        if (index < 0 || index >= list.size()) {
            return;
        }
        String name = nameOf(list.get(index).spec()).getString();
        EditBox target = output ? this.productsBox : this.ingredientsBox;
        if (target == null) {
            return;
        }
        target.setValue(name);
        target.setFocused(true);
    }

    @Override
    public void onGenerationFinished(GenerationReport report) {
        this.notice = ClientGenerationFeedback.describe(report);
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

        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.panelLeft + PAD;
        int room = this.previousPage.x() - left - 8;
        graphics.drawString(this.font, UiKit.fit(this.font, this.title.getString(), room),
                left, this.panelTop + 6, TEXT, false);

        int selected = this.plan.selectedCount();
        Component status = Component.translatable("eae2_pattern.preview.status", selected,
                this.visible.size(), this.plan.entries().size());
        graphics.drawString(this.font, UiKit.fit(this.font, status.getString(), room),
                left, this.panelTop + 18, TEXT_DIM, false);

        drawPageButton(graphics, this.previousPage, "<", over(mouseX, mouseY, this.previousPage), this.page > 0);
        drawPageButton(graphics, this.nextPage, ">", over(mouseX, mouseY, this.nextPage),
                this.page < pageCount() - 1);

        String pageLabel = (this.page + 1) + " / " + pageCount();
        graphics.drawString(this.font, pageLabel,
                this.previousPage.x() - 6 - this.font.width(pageLabel), this.panelTop + 11, TEXT_DIM, false);

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
        String fitted = UiKit.fit(this.font, right.getString(), this.panelWidth - 60);
        graphics.drawString(this.font, fitted, this.panelLeft + this.panelWidth - PAD - this.font.width(fitted),
                this.panelTop + 18, color, false);

        graphics.fill(this.panelLeft + PAD, this.panelTop + HEADER_H - 1,
                this.panelLeft + this.panelWidth - PAD, this.panelTop + HEADER_H, DIVIDER);
    }

    private void drawPageButton(GuiGraphics graphics, Box box, String glyph, boolean hovered, boolean enabled) {
        UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 4,
                enabled ? (hovered ? CARD_HOVER : CARD) : 0x33232529);
        graphics.drawString(this.font, glyph, box.x() + box.width() / 2 - 2, box.y() + 3,
                enabled ? TEXT : TEXT_MUTED, false);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.showCaptions) {
            UiKit.caption(graphics, this.font,
                    Component.translatable("eae2_pattern.preview.section.search"),
                    this.sidebarLeft, this.captionSearchY, TEXT_MUTED);
        }

        drawField(graphics, this.productsBox, this.productsY);
        drawField(graphics, this.ingredientsBox, this.ingredientsY);

        int matched = this.visible.size();
        if (this.showMatch) {
            Component match = Component.translatable("eae2_pattern.preview.match", matched,
                    this.plan.entries().size());
            graphics.drawString(this.font, UiKit.fit(this.font, match.getString(), this.sidebarWidth),
                    this.sidebarLeft, this.matchY, matched == 0 ? WARN : TEXT_MUTED, false);
        }

        if (this.showCaptions) {
            UiKit.caption(graphics, this.font,
                    Component.translatable("eae2_pattern.preview.section.conditions"),
                    this.sidebarLeft, this.captionCondY, TEXT_MUTED);
        }

        boolean[] values = conditionValues();
        for (int i = 0; i < this.toggles.size(); i++) {
            Box box = this.toggles.get(i);
            boolean hovered = over(mouseX, mouseY, box);
            if (hovered) {
                UiKit.roundedRect(graphics, box.x() - 2, box.y(), box.width() + 4, box.height(), 4, GHOST_HOVER);
            }
            int boxY = box.y() + (box.height() - CHECKBOX) / 2;
            UiKit.checkbox(graphics, box.x(), boxY, values[i], ACCENT, 0x55151519, 0xFF4A4D55);
            Component label = Component.translatable("eae2_pattern.preview.filter." + CONDITION_KEYS[i]);
            String fitted = UiKit.fit(this.font, label.getString(), box.width() - CHECKBOX - 5);
            graphics.drawString(this.font, fitted, box.x() + CHECKBOX + 4, boxY + 1,
                    values[i] ? TEXT : TEXT_DIM, false);
        }
    }

    private boolean[] conditionValues() {
        return new boolean[]{this.onlyNeverConsumed, this.onlyProbabilisticInput,
                this.onlyProbabilisticOutput, this.onlyProbabilisticByproduct,
                this.dropChanceOutputs, this.dropNeverConsumed};
    }

    private void dropCondition(int index, boolean value) {
        switch (index) {
            case 0 -> this.onlyNeverConsumed = value;
            case 1 -> this.onlyProbabilisticInput = value;
            case 2 -> this.onlyProbabilisticOutput = value;
            case 3 -> this.onlyProbabilisticByproduct = value;
            case 4 -> this.dropChanceOutputs = value;
            case 5 -> this.dropNeverConsumed = value;
            default -> {
            }
        }
        refresh();
    }

    private void drawField(GuiGraphics graphics, @Nullable EditBox box, int y) {
        boolean focused = box != null && box.isFocused();
        UiKit.roundedFrame(graphics, this.sidebarLeft, y, this.sidebarWidth, 17, 4, FIELD,
                focused ? ACCENT : FIELD_BORDER);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        UiKit.roundedRect(graphics, this.listBox.x(), this.listBox.y(), this.listBox.width(),
                this.listBox.height(), 6, 0x22FFFFFF);

        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.input"),
                this.inputIconX, this.listTop - COLUMN_H + 2, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.output"),
                this.outputIconX, this.listTop - COLUMN_H + 2, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.column.recipe"),
                this.labelX, this.listTop - COLUMN_H + 2, TEXT_MUTED, false);

        graphics.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);
        int rows = rowsPerPage();
        int start = this.page * rows;
        for (int row = 0; row < rows; row++) {
            int index = start + row;
            if (index >= this.visible.size()) {
                break;
            }
            int y = this.listTop + row * STEP;
            boolean hovered = mouseX >= this.listLeft && mouseX < this.listRight
                    && mouseY >= y && mouseY < y + ROW_H;
            renderRow(graphics, this.visible.get(index), y, hovered);
        }
        graphics.disableScissor();

        if (this.visible.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.no_match"),
                    this.listLeft + 8, this.listTop + 6, TEXT_MUTED, false);
        }

        renderScrollBar(graphics);
    }

    private void renderScrollBar(GuiGraphics graphics) {
        this.scrollBar = new Box(this.listRight + 2, this.listTop, 3, this.listBottom - this.listTop);
        UiKit.roundedRect(graphics, this.scrollBar.x(), this.scrollBar.y(), this.scrollBar.width(),
                this.scrollBar.height(), 2, 0x22FFFFFF);
        int pages = pageCount();
        if (pages <= 1) {
            return;
        }
        int trackHeight = this.scrollBar.height();
        int thumbHeight = Math.max(14, trackHeight / pages);
        int travel = trackHeight - thumbHeight;
        int thumbY = this.scrollBar.y() + travel * this.page / (pages - 1);
        UiKit.roundedRect(graphics, this.scrollBar.x(), thumbY, this.scrollBar.width(), thumbHeight, 2, ACCENT);
    }

    private void renderRow(GuiGraphics graphics, RecipePlan.Entry entry, int y, boolean hovered) {
        int x = this.listLeft;
        int width = this.listRight - this.listLeft;
        boolean selected = entry.isSelected();
        int fill = selected ? CARD_SELECTED : (hovered ? CARD_HOVER : 0x00000000);
        if (fill != 0) {
            UiKit.roundedRect(graphics, x, y, width, ROW_H, 4, fill);
        }
        if (selected) {
            UiKit.roundedRect(graphics, x, y + 3, 3, ROW_H - 6, 1, ACCENT);
        }

        UiKit.checkbox(graphics, x + 4, y + (ROW_H - CHECKBOX) / 2, selected, ACCENT, 0x55151519, 0xFF4A4D55);

        int iconY = y + (ROW_H - ICON) / 2;
        List<Ingredient> inputs = entry.inputs();
        int shownInputs = Math.min(inputs.size(), this.availableInputIcons);
        for (int i = 0; i < shownInputs; i++) {
            drawIngredient(graphics, inputs.get(i), this.inputIconX + i * ICON_STEP, iconY);
        }
        if (inputs.size() > shownInputs) {
            graphics.drawString(this.font, "+" + (inputs.size() - shownInputs),
                    this.inputIconX + shownInputs * ICON_STEP, iconY + 5, TEXT_MUTED, false);
        }

        graphics.drawString(this.font, "\u2192", this.outputIconX - 11, iconY + 5, TEXT_MUTED, false);

        List<Ingredient> outputs = entry.outputs();
        int shownOutputs = Math.min(outputs.size(), this.availableOutputIcons);
        for (int i = 0; i < shownOutputs; i++) {
            drawIngredient(graphics, outputs.get(i), this.outputIconX + i * ICON_STEP, iconY);
        }

        int badges = (entry.hasProbabilisticOutput() ? 1 : 0) + (entry.hasProbabilisticInput() ? 1 : 0)
                + (entry.hasNeverConsumedInput() ? 1 : 0);
        int badgeWidth = badges * 16;
        int labelWidth = Math.max(12, this.listRight - this.labelX - badgeWidth - 6);
        String label = UiKit.fit(this.font, entry.label().getString(), labelWidth);
        graphics.drawString(this.font, label, this.labelX, iconY + 5, selected ? TEXT : TEXT_DIM, false);

        int badgeX = this.listRight - 4;
        if (entry.hasProbabilisticOutput()) {
            badgeX = drawBadge(graphics, badgeX, y + 6, BAD,
                    Component.translatable("eae2_pattern.preview.badge.chance_output").getString());
        }
        if (entry.hasProbabilisticInput()) {
            badgeX = drawBadge(graphics, badgeX, y + 6, WARN,
                    Component.translatable("eae2_pattern.preview.badge.chance_input").getString());
        }
        if (entry.hasNeverConsumedInput()) {
            drawBadge(graphics, badgeX, y + 6, ACCENT,
                    Component.translatable("eae2_pattern.preview.badge.unconsumed").getString());
        }
    }

    /** Small tinted chip; returns the x the next chip should end at. */
    private int drawBadge(GuiGraphics graphics, int rightEdge, int y, int color, String text) {
        int width = this.font.width(text) + 5;
        int x = rightEdge - width;
        UiKit.roundedRect(graphics, x, y, width, 9, 2, (color & 0x00FFFFFF) | 0x40000000);
        graphics.drawString(this.font, text, x + 2, y + 1, color, false);
        return x - 3;
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(this.panelLeft + PAD, this.footerTop, this.panelLeft + this.panelWidth - PAD,
                this.footerTop + 1, DIVIDER);

        for (int i = 0; i < this.tools.size(); i++) {
            Box box = this.tools.get(i);
            boolean hovered = over(mouseX, mouseY, box);
            String text = Component.translatable("eae2_pattern.preview." + TOOL_KEYS[i]).getString();
            if (hovered) {
                UiKit.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), 4, GHOST_HOVER);
            }
            graphics.drawString(this.font, text, box.x() + (box.width() - this.font.width(text)) / 2,
                    box.y() + 4, TEXT_DIM, false);
        }

        Box multi = this.multiplierButton;
        boolean multiHovered = over(mouseX, mouseY, multi);
        boolean scaled = this.multiplier > 1;
        UiKit.roundedRect(graphics, multi.x(), multi.y(), multi.width(), multi.height(), 4,
                scaled ? ACCENT_SOFT : (multiHovered ? CARD_HOVER : CARD));
        String label = Component.translatable("eae2_pattern.preview.multiplier", this.multiplier).getString();
        graphics.drawString(this.font, label, multi.x() + (multi.width() - this.font.width(label)) / 2,
                multi.y() + 4, scaled ? TEXT : TEXT_DIM, false);

        drawActionButton(graphics, this.generateButton,
                Component.translatable("eae2_pattern.preview.generate").getString(),
                !this.pending.isEmpty(),
                over(mouseX, mouseY, this.generateButton), true);
        drawActionButton(graphics, this.cancelButton,
                Component.translatable("eae2_pattern.preview.cancel").getString(), true,
                over(mouseX, mouseY, this.cancelButton), false);

        if (this.notice != null) {
            int noticeX = multi.x() + multi.width() + 8;
            int room = this.cancelButton.x() - noticeX - 6;
            if (room > 24) {
                graphics.drawString(this.font, UiKit.fit(this.font, this.notice.getString(), room),
                        noticeX, multi.y() + 4, TEXT_MUTED, false);
            }
        }
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
                box.y() + 4, textColor, false);
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
        if (over(mouseX, mouseY, this.multiplierButton)) {
            graphics.renderTooltip(this.font,
                    Component.translatable("eae2_pattern.preview.tooltip.multiplier"),
                    mouseX, mouseY);
            return;
        }
        if (mouseX < this.listLeft || mouseX >= this.listRight || mouseY < this.listTop
                || mouseY >= this.listBottom) {
            return;
        }
        int index = this.page * rowsPerPage() + (mouseY - this.listTop) / STEP;
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
        if (over(mouseX, mouseY, this.multiplierButton)) {
            cycleMultiplier();
            return true;
        }
        for (int i = 0; i < this.tools.size(); i++) {
            if (over(mouseX, mouseY, this.tools.get(i))) {
                switch (i) {
                    case 0 -> selectVisible(true);
                    case 1 -> selectVisible(false);
                    case 2 -> invertVisible();
                    case 3 -> dropDuplicates();
                    case 4 -> {
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
        if (mouseX < this.listLeft || mouseX >= this.listRight || mouseY < this.listTop
                || mouseY >= this.listBottom) {
            return false;
        }
        int row = (int) ((mouseY - this.listTop) / STEP);
        int index = this.page * rowsPerPage() + row;
        if (index < 0 || index >= this.visible.size()) {
            return false;
        }
        RecipePlan.Entry entry = this.visible.get(index);
        int rowY = this.listTop + row * STEP;
        if (mouseY < rowY || mouseY >= rowY + ROW_H) {
            return false;
        }

        int inputIndex = (int) Math.floor((mouseX - this.inputIconX) / (double) ICON_STEP);
        if (inputIndex >= 0 && inputIndex < Math.min(entry.inputs().size(), this.availableInputIcons)
                && mouseX < this.inputIconX + Math.min(entry.inputs().size(), this.availableInputIcons) * ICON_STEP)
        {
            filterByIcon(entry, inputIndex, false);
            return true;
        }
        int outputIndex = (int) Math.floor((mouseX - this.outputIconX) / (double) ICON_STEP);
        if (outputIndex >= 0 && outputIndex < Math.min(entry.outputs().size(), this.availableOutputIcons)
                && mouseX < this.outputIconX + Math.min(entry.outputs().size(), this.availableOutputIcons) * ICON_STEP)
        {
            filterByIcon(entry, outputIndex, true);
            return true;
        }

        entry.toggle();
        afterSelectionChanged();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (over(mouseX, mouseY, this.listBox)) {
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
