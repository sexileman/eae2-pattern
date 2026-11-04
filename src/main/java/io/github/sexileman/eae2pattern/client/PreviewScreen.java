package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Requirement #6: show what would be generated and let the player drop entries.
 *
 * <p>The list is drawn by hand instead of using a vanilla selection widget so the layout can be tuned for
 * recipe rows (icon + checkbox + label + kind tag) without fighting a generic list's assumptions, and so it can
 * page instead of scroll: a broad category easily holds hundreds of entries and a scrollbar makes the selection
 * state hard to see at a glance.</p>
 */
public final class PreviewScreen extends Screen implements GenerationListener {

    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 34;
    private static final int PAGE_BUTTON_WIDTH = 18;
    private static final int PAGE_BUTTON_HEIGHT = 14;

    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_DIM = 0x909090;
    private static final int COLOR_WARN = 0xE06060;
    private static final int COLOR_OK = 0x60C060;
    private static final int COLOR_ROW_ALT = 0x30FFFFFF;

    private final RecipePlan plan;

    @Nullable
    private Button generateButton;
    @Nullable
    private Button previousPageButton;
    @Nullable
    private Button nextPageButton;

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
        this.panelWidth = Math.min(400, this.width - 40);
        this.panelHeight = Math.min(260, this.height - 40);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.listTop = this.panelTop + HEADER_HEIGHT;
        this.listBottom = this.panelTop + this.panelHeight - FOOTER_HEIGHT;
        this.page = 0;

        int buttonY = this.panelTop + this.panelHeight - 26;
        int buttonX = this.panelLeft + 8;

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_all"), b -> {
            this.plan.selectAll(true);
            this.refreshButtons();
        }).bounds(buttonX, buttonY, 52, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.preview.select_none"), b -> {
            this.plan.selectAll(false);
            this.refreshButtons();
        }).bounds(buttonX + 56, buttonY, 52, 18).build());

        Button generate = Button.builder(Component.translatable("eae2_pattern.preview.generate"), b -> this.generate())
                .bounds(this.panelLeft + this.panelWidth - 132, buttonY, 60, 18).build();
        this.addRenderableWidget(generate);
        this.generateButton = generate;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(this.panelLeft + this.panelWidth - 68, buttonY, 60, 18).build());

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

        this.refreshButtons();
        ClientGenerationFeedback.setListener(this);
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
    }

    private int rowsPerPage() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_HEIGHT);
    }

    private int pageCount() {
        int rows = this.rowsPerPage();
        return Math.max(1, (this.plan.entries().size() + rows - 1) / rows);
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

        Component status = Component.translatable("eae2_pattern.preview.selected",
                this.plan.selectedCount(), this.plan.entries().size());
        graphics.drawString(this.font, status, left + 8, top + 18, COLOR_DIM, false);

        Component networkLine = this.networkLine();
        int networkColor = this.networkLineColor();
        graphics.drawString(this.font, networkLine, left + 8, top + 30, networkColor, false);

        if (this.plan.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eae2_pattern.preview.empty"),
                    left + 8, this.listTop + 4, COLOR_DIM, false);
        } else {
            this.renderRows(graphics);
        }

        if (this.lastResult != null) {
            graphics.drawString(this.font, this.lastResult, left + 8, this.listBottom + 2, COLOR_DIM, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics) {
        int left = this.panelLeft;
        int right = this.panelLeft + this.panelWidth;
        List<RecipePlan.Entry> entries = this.plan.entries();

        graphics.enableScissor(left + 1, this.listTop, right - 1, this.listBottom);
        int rows = this.rowsPerPage();
        int first = this.page * rows;
        int last = Math.min(entries.size(), first + rows);

        for (int i = first; i < last; i++) {
            RecipePlan.Entry entry = entries.get(i);
            int rowY = this.listTop + (i - first) * ROW_HEIGHT;
            if (i % 2 == 1) {
                graphics.fill(left + 1, rowY, right - 1, rowY + ROW_HEIGHT, COLOR_ROW_ALT);
            }

            int boxX = left + 6;
            int boxY = rowY + 6;
            graphics.fill(boxX, boxY, boxX + 9, boxY + 9, 0xFF808080);
            graphics.fill(boxX + 1, boxY + 1, boxX + 8, boxY + 8, 0xFF101010);
            if (entry.isSelected()) {
                graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7, 0xFF60C060);
            }

            ItemStack icon = entry.icon();
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, left + 20, rowY + 2);
            }

            Component label = entry.label();
            String text = this.font.plainSubstrByWidth(label.getString(), this.panelWidth - 130);
            graphics.drawString(this.font, text, left + 40, rowY + 6, entry.isSelected() ? COLOR_TEXT : COLOR_DIM, false);

            Component kindTag = kindName(entry.spec().kind());
            int tagWidth = this.font.width(kindTag);
            graphics.drawString(this.font, kindTag, right - 10 - tagWidth, rowY + 6, COLOR_DIM, false);
        }
        graphics.disableScissor();
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
        return Component.translatable("eae2_pattern.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.panelLeft && mouseX <= this.panelLeft + this.panelWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = (int) ((mouseY - this.listTop) / ROW_HEIGHT);
            int index = this.page * this.rowsPerPage() + row;
            if (index >= 0 && index < this.plan.entries().size()) {
                this.plan.entries().get(index).toggle();
                this.refreshButtons();
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
