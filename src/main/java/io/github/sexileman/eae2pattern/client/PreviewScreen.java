package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.util.Set;

/**
 * Requirement #6: show what would be generated, and let the player narrow it down before spending anything.
 *
 * <p>The window is split in two: a rail of filters on the left and the recipe list on the right. Every row draws
 * the recipe itself - what goes in, an arrow, what comes out - because one machine routinely makes the same item in
 * several ways: a GregTech bending machine turns two iron ingots into a double iron plate, and it also turns two
 * iron plates into one. Only the ingredients tell those rows apart.</p>
 *
 * <p>The list is drawn by hand instead of using a vanilla selection widget so rows can mix checkboxes, item
 * rendering, amounts and badges, and so it can page instead of scroll: a broad category easily holds hundreds of
 * entries and a scrollbar makes the selection state hard to see.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    private static final int ROW_HEIGHT = 22;
    private static final int RAIL_WIDTH = 150;
    private static final int ICON = 16;
    private static final int ICON_STEP = 17;
    private static final int MAX_INPUT_ICONS = 3;
    private static final int MAX_OUTPUT_ICONS = 2;

    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_DIM = 0x909090;
    private static final int COLOR_WARN = 0xE06060;
    private static final int COLOR_OK = 0x60C060;
    private static final int COLOR_ROW_ALT = 0x30FFFFFF;
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_FRAME = 0xFF3A3A3A;
    private static final int COLOR_PANEL = 0xFF151515;
    private static final int COLOR_CHANCE = 0xFFE0A040;
    private static final int COLOR_UNCONSUMED = 0xFF5AA0E0;

    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};

    private final RecipePlan plan;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();

    @Nullable
    private Button generateButton;
    @Nullable
    private Button previousPageButton;
    @Nullable
    private Button nextPageButton;
    @Nullable
    private Button onlySelectedButton;
    @Nullable
    private Button multiplierButton;
    @Nullable
    private EditBox productsBox;
    @Nullable
    private EditBox ingredientsBox;

    private final List<Button> conditionButtons = new ArrayList<>();

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
    private int railLeft;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int footerTop;
    private int page;

    @Nullable
    private Component lastResult;

    public PreviewScreen(RecipePlan plan) {
        super(Component.translatable("eae2_pattern.preview.title", plan.machineName()));
        this.plan = plan;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(520, this.width - 30);
        this.panelHeight = Math.min(300, this.height - 30);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.railLeft = this.panelLeft + 8;
        this.listLeft = this.panelLeft + RAIL_WIDTH + 14;
        this.listRight = this.panelLeft + this.panelWidth - 8;
        this.listTop = this.panelTop + 30;
        this.footerTop = this.panelTop + this.panelHeight - 62;
        this.listBottom = this.footerTop - 4;
        this.page = 0;

        this.previousPageButton = Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(this.panelLeft + this.panelWidth - 52, this.panelTop + 4, 14, 14).build();
        this.addRenderableWidget(this.previousPageButton);
        this.nextPageButton = Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(this.panelLeft + this.panelWidth - 34, this.panelTop + 4, 14, 14).build();
        this.addRenderableWidget(this.nextPageButton);

        this.productsBox = new EditBox(this.font, this.railLeft, this.panelTop + 44, RAIL_WIDTH - 2, 14,
                Component.translatable("eae2_pattern.preview.filter.products"));
        this.productsBox.setMaxLength(96);
        this.productsBox.setHint(Component.translatable("eae2_pattern.preview.filter.hint"));
        this.productsBox.setResponder(text -> {
            this.productsQuery = ItemSearchQuery.parse(text);
            rebuildVisible();
        });
        this.addRenderableWidget(this.productsBox);

        this.ingredientsBox = new EditBox(this.font, this.railLeft, this.panelTop + 72, RAIL_WIDTH - 2, 14,
                Component.translatable("eae2_pattern.preview.filter.ingredients"));
        this.ingredientsBox.setMaxLength(96);
        this.ingredientsBox.setHint(Component.translatable("eae2_pattern.preview.filter.hint"));
        this.ingredientsBox.setResponder(text -> {
            this.ingredientsQuery = ItemSearchQuery.parse(text);
            rebuildVisible();
        });
        this.addRenderableWidget(this.ingredientsBox);

        int chipWidth = (RAIL_WIDTH - 6) / 2;
        int chipTop = this.panelTop + 96;
        this.conditionButtons.clear();
        addCondition(this.railLeft, chipTop, chipWidth, "unconsumed", () -> onlyNeverConsumed, v -> onlyNeverConsumed = v);
        addCondition(this.railLeft + chipWidth + 4, chipTop, chipWidth, "chance_input",
                () -> onlyProbabilisticInput, v -> onlyProbabilisticInput = v);
        addCondition(this.railLeft, chipTop + 16, chipWidth, "chance_output",
                () -> onlyProbabilisticOutput, v -> onlyProbabilisticOutput = v);
        addCondition(this.railLeft + chipWidth + 4, chipTop + 16, chipWidth, "chance_byproduct",
                () -> onlyProbabilisticByproduct, v -> onlyProbabilisticByproduct = v);
        addCondition(this.railLeft, chipTop + 32, chipWidth, "drop_chance",
                () -> dropChanceOutputs, v -> dropChanceOutputs = v);
        addCondition(this.railLeft + chipWidth + 4, chipTop + 32, chipWidth, "drop_unconsumed",
                () -> dropNeverConsumed, v -> dropNeverConsumed = v);

        this.multiplierButton = Button.builder(multiplierLabel(), b -> {
            this.multiplier = nextMultiplier(this.multiplier);
            b.setMessage(multiplierLabel());
            refreshButtons();
        }).bounds(this.railLeft, chipTop + 52, RAIL_WIDTH - 2, 16).build();
        this.addRenderableWidget(this.multiplierButton);

        int toolY = this.footerTop;
        int toolX = this.listLeft;
        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_all"), b -> selectVisible(true))
                .bounds(toolX, toolY, 46, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_none"), b -> selectVisible(false))
                .bounds(toolX + 48, toolY, 54, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.invert"), b -> invertVisible())
                .bounds(toolX + 104, toolY, 46, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.dedupe"), b -> dropDuplicates())
                .bounds(toolX + 152, toolY, 52, 18).build());
        this.onlySelectedButton = Button.builder(onlySelectedLabel(), b -> {
            this.onlySelected = !this.onlySelected;
            b.setMessage(onlySelectedLabel());
            rebuildVisible();
        }).bounds(toolX + 206, toolY, 64, 18).build();
        this.addRenderableWidget(this.onlySelectedButton);

        int actionY = toolY + 20;
        this.generateButton = Button.builder(Component.translatable("eae2_pattern.preview.generate"), b -> generate())
                .bounds(this.listRight - 108, actionY, 56, 18).build();
        this.addRenderableWidget(this.generateButton);
        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.cancel"), b -> onClose())
                .bounds(this.listRight - 48, actionY, 48, 18).build());

        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
        rebuildVisible();
        refreshButtons();
    }

    private void addCondition(int x, int y, int width, String key, java.util.function.BooleanSupplier getter,
                              java.util.function.Consumer<Boolean> setter) {
        Button button = Button.builder(conditionLabel(key, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            b.setMessage(conditionLabel(key, getter.getAsBoolean()));
            rebuildVisible();
        }).bounds(x, y, width, 14).build();
        this.conditionButtons.add(button);
        this.addRenderableWidget(button);
    }

    private Component conditionLabel(String key, boolean active) {
        return Component.literal((active ? "[x] " : "[ ] ") + Component.translatable("eae2_pattern.preview.filter." + key).getString());
    }

    private Component multiplierLabel() {
        return Component.translatable("eae2_pattern.preview.multiplier", this.multiplier);
    }

    private Component onlySelectedLabel() {
        return Component.translatable(this.onlySelected
                ? "eae2_pattern.preview.only_selected_on"
                : "eae2_pattern.preview.only_selected");
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
        refreshButtons();
    }

    private int rowsPerPage() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (this.visible.size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void turnPage(int delta) {
        this.page = Mth.clamp(this.page + delta, 0, pageCount() - 1);
        refreshButtons();
    }

    private void refreshButtons() {
        if (this.previousPageButton != null) {
            this.previousPageButton.active = this.page > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.active = this.page < pageCount() - 1;
        }
        if (this.generateButton != null) {
            this.generateButton.active = !buildSpecs().isEmpty();
        }
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
        refreshButtons();
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
        // The chat lines are printed by ClientGenerationFeedback; the screen only needs the summary line.
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(this.panelLeft - 1, this.panelTop - 1, this.panelLeft + this.panelWidth + 1,
                this.panelTop + this.panelHeight + 1, 0xFF000000);
        graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight, COLOR_PANEL);

        int titleWidth = this.panelWidth - 130;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), titleWidth),
                this.panelLeft + 8, this.panelTop + 7, 0xFFFFFF, false);

        String pageLabel = (this.page + 1) + " / " + pageCount();
        graphics.drawString(this.font, pageLabel, this.panelLeft + this.panelWidth - 56 - this.font.width(pageLabel) + 2,
                this.panelTop + 8, COLOR_DIM, false);

        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.filter.products"),
                this.railLeft, this.panelTop + 34, COLOR_DIM, false);
        graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.filter.ingredients"),
                this.railLeft, this.panelTop + 62, COLOR_DIM, false);

        graphics.fill(this.listLeft - 6, this.listTop - 4, this.listLeft - 5, this.listBottom, COLOR_FRAME);

        renderStatusLine(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderRows(graphics, mouseX, mouseY);
        renderResultLine(graphics);
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderResultLine(GuiGraphics graphics) {
        if (this.lastResult == null) {
            return;
        }
        int width = this.listRight - this.listLeft;
        String text = this.font.plainSubstrByWidth(this.lastResult.getString(), width);
        graphics.drawString(this.font, text, this.listLeft, this.footerTop + 40, 0xFFFFFF, false);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);
        int rows = rowsPerPage();
        int start = this.page * rows;
        for (int row = 0; row < rows; row++) {
            int index = start + row;
            if (index >= this.visible.size()) {
                break;
            }
            int y = this.listTop + row * ROW_HEIGHT;
            boolean hovered = mouseX >= this.listLeft && mouseX < this.listRight
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            renderRow(graphics, this.visible.get(index), index, y, hovered);
        }
        graphics.disableScissor();
        if (this.visible.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.no_match"),
                    this.listLeft + 8, this.listTop + 8, COLOR_DIM, false);
        }
    }

    private void renderRow(GuiGraphics graphics, RecipePlan.Entry entry, int index, int y, boolean hovered) {
        if (hovered) {
            graphics.fill(this.listLeft, y, this.listRight, y + ROW_HEIGHT, COLOR_ROW_HOVER);
        } else if ((index & 1) == 1) {
            graphics.fill(this.listLeft, y, this.listRight, y + ROW_HEIGHT, COLOR_ROW_ALT);
        }

        int checkX = this.listLeft + 3;
        int checkY = y + 6;
        graphics.fill(checkX, checkY, checkX + 10, checkY + 10, 0xFF000000);
        graphics.fill(checkX + 1, checkY + 1, checkX + 9, checkY + 9, entry.isSelected() ? 0xFF60C060 : 0xFF2A2A2A);
        if (entry.isSelected()) {
            graphics.fill(checkX + 2, checkY + 2, checkX + 8, checkY + 8, 0xFF2A2A2A);
        }

        int iconY = y + 3;
        int x = this.listLeft + 18;
        List<Ingredient> inputs = entry.inputs();
        int shown = Math.min(inputs.size(), MAX_INPUT_ICONS);
        for (int i = 0; i < shown; i++) {
            drawIngredient(graphics, inputs.get(i), x + i * ICON_STEP, iconY);
        }
        if (inputs.size() > shown) {
            graphics.drawString(this.font, "+" + (inputs.size() - shown), x + shown * ICON_STEP, iconY + 4,
                    COLOR_DIM, false);
        }

        int arrowX = this.listLeft + 74;
        graphics.drawString(this.font, "\u2192", arrowX, y + 7, COLOR_DIM, false);

        List<Ingredient> outputs = entry.outputs();
        int outShown = Math.min(outputs.size(), MAX_OUTPUT_ICONS);
        for (int i = 0; i < outShown; i++) {
            drawIngredient(graphics, outputs.get(i), arrowX + 12 + i * ICON_STEP, iconY);
        }
        if (outputs.size() > outShown) {
            graphics.drawString(this.font, "+" + (outputs.size() - outShown),
                    arrowX + 12 + outShown * ICON_STEP, iconY + 4, COLOR_DIM, false);
        }

        int badgeX = this.listRight - 4;
        if (entry.hasNeverConsumedInput()) {
            graphics.fill(badgeX - 4, y + 10, badgeX, y + 14, COLOR_UNCONSUMED);
            badgeX -= 7;
        }
        if (entry.hasProbabilisticInput()) {
            graphics.fill(badgeX - 4, y + 10, badgeX, y + 14, COLOR_CHANCE);
            badgeX -= 7;
        }
        if (entry.hasProbabilisticOutput()) {
            graphics.fill(badgeX - 4, y + 10, badgeX, y + 14, COLOR_WARN);
            badgeX -= 7;
        }

        int labelX = arrowX + 12 + MAX_OUTPUT_ICONS * ICON_STEP + 4;
        int labelWidth = Math.max(20, badgeX - 6 - labelX);
        String label = this.font.plainSubstrByWidth(entry.label().getString(), labelWidth);
        graphics.drawString(this.font, label, labelX, y + 7, entry.isSelected() ? COLOR_TEXT : COLOR_DIM, false);
    }

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
        if (!ingredient.guaranteedOutput() || ingredient.probabilistic()) {
            graphics.fill(x, y + ICON - 2, x + ICON, y + ICON, ingredient.neverConsumed() ? COLOR_UNCONSUMED : COLOR_CHANCE);
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
        graphics.drawString(this.font, text, -this.font.width(text), -this.font.lineHeight, 0xFFFFFF, true);
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

    /** One line under the title: how much is selected, and what the network can take. */
    private void renderStatusLine(GuiGraphics graphics) {
        int selected = this.plan.selectedCount();
        Component status = Component.translatable("eae2_pattern.preview.status", selected, this.visible.size(),
                this.plan.entries().size());
        graphics.drawString(this.font, status, this.panelLeft + 8, this.panelTop + 18,
                selected == 0 ? COLOR_DIM : COLOR_TEXT, false);

        boolean hasNetwork = ClientNetworkState.hasNetwork();
        long blanks = ClientNetworkState.blankPatterns();
        Component right;
        int color;
        if (!hasNetwork) {
            right = Component.translatable("eae2_pattern.preview.no_network");
            color = COLOR_WARN;
        } else if (selected > blanks) {
            right = Component.translatable("eae2_pattern.preview.not_enough_blanks", blanks);
            color = COLOR_WARN;
        } else {
            right = Component.translatable("eae2_pattern.preview.blanks", blanks);
            color = blankColor(blanks);
        }
        int rightX = this.panelLeft + this.panelWidth - 8 - this.font.width(right);
        graphics.drawString(this.font, right, rightX, this.panelTop + 18, color, false);
    }

    private static int blankColor(long blanks) {
        return blanks > 0 ? COLOR_OK : COLOR_WARN;
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < this.listLeft || mouseX >= this.listRight || mouseY < this.listTop || mouseY >= this.listBottom) {
            return;
        }
        int index = this.page * rowsPerPage() + (mouseY - this.listTop) / ROW_HEIGHT;
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
        return Component.translatable("eae2_pattern.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom) {
            int index = this.page * rowsPerPage() + (int) ((mouseY - this.listTop) / ROW_HEIGHT);
            if (index >= 0 && index < this.visible.size()) {
                this.visible.get(index).toggle();
                afterSelectionChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom) {
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
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }
}
