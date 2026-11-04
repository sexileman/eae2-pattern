package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import io.github.sexileman.eae2pattern.pattern.StackType;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Requirement #6: show what would be generated and let the player drop entries.
 *
 * <p>Every row draws the recipe itself - the ingredients going in on the left, an arrow, the result coming out -
 * instead of just the product's icon. That matters as soon as one machine can make the same item in several ways:
 * a GregTech bending machine turns two iron ingots into a double iron plate, and it also turns two iron plates into
 * one. Only the input icons tell those two rows apart, and the amounts printed on the icons tell apart the recipes
 * that differ solely by count.</p>
 *
 * <p>The list is drawn by hand instead of using a vanilla selection widget so rows can mix checkboxes, item
 * rendering and wrapped text without fighting a generic list's assumptions, and so it can page instead of scroll: a
 * broad category easily holds hundreds of entries and a scrollbar makes the selection state hard to see.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 66;
    private static final int FOOTER_HEIGHT = 46;
    private static final int PAGE_BUTTON_WIDTH = 18;
    private static final int PAGE_BUTTON_HEIGHT = 14;
    private static final int SEARCH_HEIGHT = 14;

    private static final int ICON = 16;
    private static final int ICON_STEP = 17;
    private static final int MAX_INPUT_ICONS = 3;

    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_DIM = 0x909090;
    private static final int COLOR_WARN = 0xE06060;
    private static final int COLOR_OK = 0x60C060;
    private static final int COLOR_ROW_ALT = 0x30FFFFFF;

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
    private EditBox searchBox;

    private ItemSearchQuery query = ItemSearchQuery.empty();
    private boolean onlySelected;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int page;

    @Nullable
    private Component lastResult;

    public PreviewScreen(RecipePlan plan) {
        super(Component.translatable("eae2_pattern.preview.title", plan.machineName()));
        this.plan = plan;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(440, this.width - 40);
        this.panelHeight = Math.min(320, this.height - 40);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.listTop = this.panelTop + HEADER_HEIGHT;
        this.listBottom = this.panelTop + this.panelHeight - FOOTER_HEIGHT;
        this.page = 0;

        int toolY = this.listBottom + 3;
        int toolX = this.panelLeft + 8;

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_all"), b -> {
            this.selectVisible(true);
        }).bounds(toolX, toolY, 52, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_none"), b -> {
            this.selectVisible(false);
        }).bounds(toolX + 54, toolY, 52, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.invert"), b -> {
            this.invertVisible();
        }).bounds(toolX + 108, toolY, 52, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.dedupe"), b -> {
            this.dropDuplicates();
        }).bounds(toolX + 162, toolY, 60, 18).build());

        Button onlySelected = Button.builder(this.onlySelectedLabel(), b -> this.toggleOnlySelected())
                .bounds(toolX + 224, toolY, 64, 18).build();
        this.addRenderableWidget(onlySelected);
        this.onlySelectedButton = onlySelected;

        int actionY = this.listBottom + 24;
        int cancelX = this.panelLeft + this.panelWidth - 8 - 52;
        Button generate = Button.builder(Component.translatable("eae2_pattern.preview.generate"), b -> this.generate())
                .bounds(cancelX - 2 - 64, actionY, 64, 18).build();
        this.addRenderableWidget(generate);
        this.generateButton = generate;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(cancelX, actionY, 52, 18).build());

        int pageButtonY = this.panelTop + 4;
        int nextX = this.panelLeft + this.panelWidth - 8 - PAGE_BUTTON_WIDTH;
        int previousX = nextX - PAGE_BUTTON_WIDTH - 2;

        Button previous = Button.builder(Component.literal("<"), b -> this.turnPage(-1))
                .bounds(previousX, pageButtonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT).build();
        this.addRenderableWidget(previous);
        this.previousPageButton = previous;

        Button next = Button.builder(Component.literal(">"), b -> this.turnPage(1))
                .bounds(nextX, pageButtonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT).build();
        this.addRenderableWidget(next);
        this.nextPageButton = next;

        EditBox search = new EditBox(this.font, this.panelLeft + 8, this.panelTop + 18,
                this.panelWidth - 16, SEARCH_HEIGHT, Component.translatable("eae2_pattern.preview.search"));
        search.setMaxLength(96);
        search.setHint(Component.translatable("eae2_pattern.preview.search_hint"));
        search.setValue(this.query.raw());
        search.setResponder(text -> {
            this.query = ItemSearchQuery.parse(text);
            this.rebuildVisible();
            this.page = 0;
            this.refreshButtons();
        });
        this.addRenderableWidget(search);
        this.searchBox = search;

        this.rebuildVisible();
        this.refreshButtons();
        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
    }

    private Component onlySelectedLabel() {
        return Component.translatable(this.onlySelected
                ? "eae2_pattern.preview.only_selected_on"
                : "eae2_pattern.preview.only_selected");
    }

    private void rebuildVisible() {
        this.visible.clear();
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (this.onlySelected && !entry.isSelected()) {
                continue;
            }
            if (this.query.matches(entry)) {
                this.visible.add(entry);
            }
        }
        this.page = Mth.clamp(this.page, 0, this.pageCount() - 1);
    }

    private void selectVisible(boolean selected) {
        for (RecipePlan.Entry entry : this.visible) {
            entry.setSelected(selected);
        }
        this.afterSelectionChanged();
    }

    private void invertVisible() {
        for (RecipePlan.Entry entry : this.visible) {
            entry.setSelected(!entry.isSelected());
        }
        this.afterSelectionChanged();
    }

    /**
     * Keeps the first selected recipe for every distinct output and drops the other ways of making it.
     *
     * <p>This is the "I do not care whether it is made from ingots or from plates" button: it collapses the
     * GregTech-style variant explosion down to one pattern per product without touching unrelated rows.</p>
     */
    private void dropDuplicates() {
        Set<String> kept = new HashSet<>();
        int dropped = 0;
        for (RecipePlan.Entry entry : this.visible) {
            if (!entry.isSelected()) {
                continue;
            }
            if (!kept.add(entry.outputKey())) {
                entry.setSelected(false);
                dropped++;
            }
        }
        this.lastResult = Component.translatable("eae2_pattern.preview.deduped", dropped);
        this.afterSelectionChanged();
    }

    private void toggleOnlySelected() {
        this.onlySelected = !this.onlySelected;
        if (this.onlySelectedButton != null) {
            this.onlySelectedButton.setMessage(this.onlySelectedLabel());
        }
        this.page = 0;
        this.rebuildVisible();
        this.refreshButtons();
    }

    private void afterSelectionChanged() {
        if (this.onlySelected) {
            this.rebuildVisible();
        }
        this.refreshButtons();
    }

    private int rowsPerPage() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_HEIGHT);
    }

    private int pageCount() {
        int rows = this.rowsPerPage();
        return Math.max(1, (this.visible.size() + rows - 1) / rows);
    }

    private void turnPage(int delta) {
        this.page = Mth.clamp(this.page + delta, 0, this.pageCount() - 1);
        this.refreshButtons();
    }

    private void refreshButtons() {
        if (this.generateButton != null) {
            this.generateButton.active = this.plan.selectedCount() > 0;
        }
        if (this.previousPageButton != null) {
            this.previousPageButton.active = this.page > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.active = this.page < this.pageCount() - 1;
        }
    }

    private void generate() {
        List<PatternSpec> specs = this.plan.selectedSpecs();
        if (specs.isEmpty()) {
            return;
        }
        PatternRequestSender.send(specs);
        this.onClose();
    }

    @Override
    public void onGenerationFinished(GenerationReport report) {
        this.lastResult = ClientGenerationFeedback.describe(report);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int left = this.panelLeft;
        int top = this.panelTop;
        int right = this.panelLeft + this.panelWidth;
        graphics.fill(left - 1, top - 1, right + 1, top + this.panelHeight + 1, 0xFF101010);
        graphics.fill(left, top, right, top + this.panelHeight, 0xFF202020);

        String title = this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth - 112);
        graphics.drawString(this.font, title, left + 8, top + 6, COLOR_TEXT, false);

        Component pageText = Component.translatable("eae2_pattern.preview.page", this.page + 1, this.pageCount());
        graphics.drawString(this.font, pageText, right - 8 - 2 * PAGE_BUTTON_WIDTH - 6 - this.font.width(pageText),
                top + 7, COLOR_DIM, false);

        Component status = this.query.isEmpty()
                ? Component.translatable("eae2_pattern.preview.selected",
                        this.plan.selectedCount(), this.plan.entries().size())
                : Component.translatable("eae2_pattern.preview.selected_filtered",
                        this.plan.selectedCount(), this.plan.entries().size(), this.visible.size());
        graphics.drawString(this.font, status, left + 8, top + 36, COLOR_DIM, false);

        Component networkLine = this.networkLine();
        graphics.drawString(this.font, networkLine, left + 8, top + 48, this.networkLineColor(), false);

        if (this.plan.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.empty"),
                    left + 8, this.listTop + 4, COLOR_DIM, false);
        } else if (this.visible.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.no_match"),
                    left + 8, this.listTop + 4, COLOR_DIM, false);
        } else {
            this.renderRows(graphics);
            this.renderRowTooltip(graphics, mouseX, mouseY);
        }

        if (this.lastResult != null) {
            int maxWidth = this.panelLeft + this.panelWidth - 8 - 126 - (left + 8) - 4;
            String text = this.font.plainSubstrByWidth(this.lastResult.getString(), Math.max(20, maxWidth));
            graphics.drawString(this.font, text, left + 8, this.listBottom + 29, COLOR_DIM, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics) {
        int left = this.panelLeft;
        int right = this.panelLeft + this.panelWidth;

        graphics.enableScissor(left + 1, this.listTop, right - 1, this.listBottom);
        int rows = this.rowsPerPage();
        int first = this.page * rows;
        int last = Math.min(this.visible.size(), first + rows);

        for (int i = first; i < last; i++) {
            RecipePlan.Entry entry = this.visible.get(i);
            int rowY = this.listTop + (i - first) * ROW_HEIGHT;
            if ((i - first) % 2 == 1) {
                graphics.fill(left + 1, rowY, right - 1, rowY + ROW_HEIGHT, COLOR_ROW_ALT);
            }
            this.renderRow(graphics, entry, rowY);
        }
        graphics.disableScissor();
    }

    private void renderRow(GuiGraphics graphics, RecipePlan.Entry entry, int rowY) {
        int left = this.panelLeft;
        int right = this.panelLeft + this.panelWidth;
        int iconY = rowY + (ROW_HEIGHT - ICON) / 2;

        int boxX = left + 6;
        int boxY = rowY + (ROW_HEIGHT - 10) / 2 - 1;
        graphics.fill(boxX, boxY, boxX + 10, boxY + 10, 0xFF808080);
        graphics.fill(boxX + 1, boxY + 1, boxX + 9, boxY + 9, 0xFF101010);
        if (entry.isSelected()) {
            graphics.fill(boxX + 2, boxY + 2, boxX + 8, boxY + 8, COLOR_OK);
        }

        // Ingredients, then an arrow, then the product: the reason this screen is worth looking at.
        List<StackSpec> inputs = entry.inputs();
        int drawn = Math.min(inputs.size(), MAX_INPUT_ICONS);
        if (inputs.size() > MAX_INPUT_ICONS) {
            drawn = MAX_INPUT_ICONS - 1;
        }
        int x = this.inputsX();
        for (int i = 0; i < drawn; i++) {
            this.renderStackIcon(graphics, inputs.get(i), x, iconY);
            x += ICON_STEP;
        }
        if (inputs.size() > MAX_INPUT_ICONS) {
            graphics.drawString(this.font, "+" + (inputs.size() - drawn), x + 1, iconY + 4, COLOR_DIM, false);
        }

        graphics.drawString(this.font, "\u2192", this.arrowX(), iconY + 4, COLOR_DIM, false);

        List<StackSpec> outputs = entry.outputs();
        if (!outputs.isEmpty()) {
            this.renderStackIcon(graphics, outputs.get(0), this.outputX(), iconY);
        }

        int labelWidth = Math.max(20, (right - 10 - 52) - this.labelX());
        String text = this.font.plainSubstrByWidth(entry.label().getString(), labelWidth);
        graphics.drawString(this.font, text, this.labelX(), rowY + 8,
                entry.isSelected() ? COLOR_TEXT : COLOR_DIM, false);

        Component kindTag = kindName(entry.spec().kind());
        int tagWidth = this.font.width(kindTag);
        graphics.drawString(this.font, kindTag, right - 10 - tagWidth, rowY + 8, COLOR_DIM, false);
    }

    private int inputsX() {
        return this.panelLeft + 19;
    }

    private int arrowX() {
        return this.panelLeft + 84;
    }

    private int outputX() {
        return this.panelLeft + 94;
    }

    private int labelX() {
        return this.panelLeft + 116;
    }

    private void renderStackIcon(GuiGraphics graphics, StackSpec spec, int x, int y) {
        if (spec.type() == StackType.ITEM) {
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
                this.renderAmount(graphics, spec.amount(), x, y);
                return;
            }
        }
        int tint = fluidTint(spec);
        graphics.fill(x, y, x + ICON, y + ICON, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + ICON - 1, y + ICON - 1, tint);
        this.renderAmount(graphics, spec.amount(), x, y);
    }

    /** Vanilla draws its stack counts at half scale; we do the same so four digit GregTech amounts still fit. */
    private void renderAmount(GuiGraphics graphics, long amount, int x, int y) {
        if (amount <= 1) {
            return;
        }
        String text = formatAmount(amount);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 200.0F);
        pose.scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(this.font, text, 2 * ICON - this.font.width(text), 18, 0xFFFFFF, true);
        pose.popPose();
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) {
            return Long.toString(amount);
        }
        if (amount < 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", amount / 1_000.0D);
        }
        if (amount < 1_000_000L) {
            return (amount / 1_000L) + "k";
        }
        if (amount < 100_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0D);
        }
        return (amount / 1_000_000L) + "M";
    }

    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < this.panelLeft || mouseX > this.panelLeft + this.panelWidth
                || mouseY < this.listTop || mouseY >= this.listBottom) {
            return;
        }
        int index = this.page * this.rowsPerPage() + (int) ((mouseY - this.listTop) / ROW_HEIGHT);
        if (index < 0 || index >= this.visible.size()) {
            return;
        }
        graphics.renderComponentTooltip(this.font, this.describe(this.visible.get(index)), mouseX, mouseY);
    }

    /** Everything the row cannot fit: every ingredient, every product and where the recipe came from. */
    private List<Component> describe(RecipePlan.Entry entry) {
        List<Component> lines = new ArrayList<>();
        for (StackSpec out : entry.outputs()) {
            lines.add(Component.literal(out.amount() + "\u00d7 ").withStyle(ChatFormatting.WHITE)
                    .append(nameOf(out).copy().withStyle(ChatFormatting.WHITE)));
        }
        if (!entry.inputs().isEmpty()) {
            lines.add(Component.empty());
            for (StackSpec in : entry.inputs()) {
                lines.add(Component.literal("  " + in.amount() + "\u00d7 ").withStyle(ChatFormatting.GRAY)
                        .append(nameOf(in).copy().withStyle(ChatFormatting.GRAY)));
            }
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("eae2_pattern.preview.tooltip.kind", kindName(entry.spec().kind()))
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(entry.spec().recipeId().toString()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static Component nameOf(StackSpec spec) {
        if (spec.type() == StackType.ITEM) {
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                return stack.getHoverName();
            }
        } else {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
            if (fluid != null) {
                return new FluidStack(fluid, 1).getDisplayName();
            }
        }
        return Component.literal(spec.id().toString());
    }

    private static int fluidTint(StackSpec spec) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
        if (fluid != null) {
            try {
                int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
                if ((tint >>> 24) != 0) {
                    return tint | 0xFF000000;
                }
            } catch (RuntimeException ignored) {
                // A fluid without a client extension simply keeps the default tint.
            }
        }
        return 0xFF3F76E4;
    }

    private Component networkLine() {
        if (!ClientNetworkState.hasStatus()) {
            return Component.translatable("eae2_pattern.preview.blanks_unknown");
        }
        if (!ClientNetworkState.hasNetwork()) {
            return Component.translatable("eae2_pattern.preview.no_network");
        }
        return Component.translatable("eae2_pattern.preview.blanks", ClientNetworkState.blankPatterns());
    }

    private int networkLineColor() {
        if (!ClientNetworkState.hasStatus()) {
            return COLOR_DIM;
        }
        if (!ClientNetworkState.hasNetwork()) {
            return COLOR_WARN;
        }
        long available = ClientNetworkState.blankPatterns();
        long needed = this.plan.selectedCount();
        return needed > available ? COLOR_WARN : COLOR_OK;
    }

    private static Component kindName(PatternKind kind) {
        return Component.translatable("eae2_pattern.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.panelLeft && mouseX <= this.panelLeft + this.panelWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = (int) ((mouseY - this.listTop) / ROW_HEIGHT);
            int index = this.page * this.rowsPerPage() + row;
            if (index >= 0 && index < this.visible.size()) {
                this.visible.get(index).toggle();
                this.afterSelectionChanged();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= this.panelLeft && mouseX <= this.panelLeft + this.panelWidth
                && mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.turnPage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While the search box has focus the arrow keys belong to text editing, not to paging.
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.turnPage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.turnPage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
}
