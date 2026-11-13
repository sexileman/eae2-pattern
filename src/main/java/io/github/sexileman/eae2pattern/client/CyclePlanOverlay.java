package io.github.sexileman.eae2pattern.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;

import io.github.sexileman.eae2pattern.client.CyclePlanViewModel.Row;
import io.github.sexileman.eae2pattern.client.CyclePlanViewModel.Tab;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.MaterialKind;
import io.github.sexileman.eae2pattern.net.C2SCycleDiagramPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.net.S2CCycleDiagramPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Content renderer for the native AE2 sub-screen; it does not own the server menu. */
public final class CyclePlanOverlay {
    private List<Tab> tabs = List.of(Tab.MATERIALS, Tab.PATHS, Tab.RECIPES);
    private final CyclePlanScreen parent;
    private final CycleDiagramSession session;
    private Tab tab = Tab.MATERIALS;
    private int scroll;
    private boolean dragging;
    private boolean exporting;
    private Component exportStatus;
    private java.nio.file.Path exportDirectory;

    private boolean canExportPaths() {
        return rows.stream().anyMatch(row -> !row.grid() && !row.inputs().isEmpty());
    }

    private void exportPaths() {
        if (exporting) return;
        var document = CyclePathExport.capture(session.snapshot());
        if (document.paths().isEmpty()) return;
        exporting = true;
        exportStatus = null;
        exportDirectory = null;
        CyclePathExport.write(
                document,
                (directory, error) -> {
                    exporting = false;
                    exportDirectory = error == null ? directory : null;
                    exportStatus = tr(error == null ? "export_done" : "export_failed");
                });
    }

    private List<Row> rows = List.of();
    private int x, y, w, h, total;
    private final List<AeToolScreen.IngredientArea> jeiIngredients = new ArrayList<>();
    private boolean clippingIngredients;

    java.util.Optional<AeToolScreen.IngredientArea> jeiIngredientAt(double mx, double my) {
        return jeiIngredients.stream()
                .filter(
                        area -> {
                            var bounds = area.bounds();
                            return mx >= bounds.getX()
                                    && mx < bounds.getX() + bounds.getWidth()
                                    && my >= bounds.getY()
                                    && my < bounds.getY() + bounds.getHeight();
                        })
                .findFirst();
    }

    private void registerIngredient(GenericStack stack, int left, int top, int width, int height) {
        int right = left + width, bottom = top + height;
        if (clippingIngredients) {
            left = Math.max(left, x + 8);
            right = Math.min(right, x + w - 12);
            top = Math.max(top, bodyTop());
            bottom = Math.min(bottom, bodyBottom());
        }
        if (right <= left || bottom <= top) return;
        io.github.sexileman.eae2pattern.pattern.StackSpec spec;
        if (stack.what() instanceof appeng.api.stacks.AEItemKey item)
            spec = io.github.sexileman.eae2pattern.pattern.StackSpec.item(item.toStack());
        else if (stack.what() instanceof AEFluidKey fluid)
            spec =
                    new io.github.sexileman.eae2pattern.pattern.StackSpec(
                            io.github.sexileman.eae2pattern.pattern.StackType.FLUID,
                            fluid.getId(),
                            fluid.getTag(),
                            Math.max(1, stack.amount()));
        else return;
        jeiIngredients.add(
                new AeToolScreen.IngredientArea(
                        spec,
                        new net.minecraft.client.renderer.Rect2i(
                                left, top, right - left, bottom - top)));
    }

    private int measuredWidth = -1;
    private int[] offsets = new int[] {0};

    CyclePlanOverlay(CyclePlanScreen parent) {
        this.parent = parent;
        session =
                new CycleDiagramSession(
                        parent.getMenu().containerId,
                        (menu, request) ->
                                PacketHandler.sendToServer(
                                        new C2SCycleDiagramPacket(menu, request)));
        rebuild();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("eae2_pattern.diagram." + key, args);
    }

    public static void accept(S2CCycleDiagramPacket packet) {
        if (Minecraft.getInstance().screen instanceof CyclePlanScreen screen) screen.accept(packet);
    }

    void acceptPacket(S2CCycleDiagramPacket packet) {
        if (session.accept(packet.menu(), packet.request(), packet.data())) rebuild();
    }

    private void rebuild() {
        tabs = CyclePlanViewModel.tabs(session.snapshot());
        if (!tabs.contains(tab)) tab = Tab.MATERIALS;
        rows = CyclePlanViewModel.rows(session.snapshot(), tab);
        scroll = 0;
        measuredWidth = -1;
    }

    private void layout() {
        w = parent.getXSize();
        h = parent.getYSize();
        x = parent.getGuiLeft();
        y = parent.getGuiTop();
        if (measuredWidth != w) measureRows();
    }

    private int columns() {
        return Math.max(1, (w - 36) / 18);
    }

    private int materialColumns() {
        return Math.max(1, Math.min(5, (w - 28) / 64));
    }

    private int materialHeight() {
        return 23;
    }

    private int pathColumns() {
        return Math.max(1, (w - 28) / 80);
    }

    private int recipeColumns() {
        return Math.max(1, (w / 2 - 28) / 18);
    }

    private int height(Row row) {
        if (row.materialKind() != MaterialKind.NONE) return 28;
        if (row.inputs().isEmpty() && row.outputs().isEmpty()) return 24;
        if (!row.grid())
            return row.outputs().isEmpty()
                    ? 18 + ((row.inputs().size() + pathColumns() - 1) / pathColumns()) * 40
                    : 52;
        if (!row.outputs().isEmpty()) {
            int cols = recipeColumns();
            return 18
                    + Math.max(
                                    (row.inputs().size() + cols - 1) / cols,
                                    (row.outputs().size() + cols - 1) / cols)
                            * 18;
        }
        return 18 + ((row.inputs().size() + columns() - 1) / columns()) * 18;
    }

    private void measureRows() {
        offsets = new int[rows.size() + 1];
        for (int i = 0; i < rows.size(); i++) offsets[i + 1] = offsets[i] + height(rows.get(i));
        if (tab == Tab.MATERIALS && rows.size() > 1) {
            for (int i = 1; i < rows.size(); i++) {
                offsets[i] = 18 + ((i - 1) / materialColumns()) * materialHeight();
            }
            offsets[rows.size()] =
                    18
                            + ((rows.size() - 1 + materialColumns() - 1) / materialColumns())
                                    * materialHeight();
        }
        total = offsets[rows.size()];
        measuredWidth = w;
    }

    private int firstVisibleRow() {
        if (tab == Tab.MATERIALS) return 0;
        int index = Arrays.binarySearch(offsets, scroll);
        return Math.max(0, index >= 0 ? index : -index - 2);
    }

    private int bodyTop() {
        return y + 76;
    }

    private int bodyBottom() {
        return y + h - (tab == Tab.PATHS ? 28 : 20);
    }

    private int maxScroll() {
        return Math.max(0, total - Math.max(1, bodyBottom() - bodyTop()));
    }

    private void control(GuiGraphics g, int bx, int by, int bw, Component label, boolean selected) {
        WorkbenchTheme.control(
                g,
                bx,
                by,
                bw,
                18,
                selected ? WorkbenchTheme.PRIMARY : WorkbenchTheme.SURFACE,
                WorkbenchTheme.LINE);
        var font = Minecraft.getInstance().font;
        g.drawString(
                font,
                label,
                bx + (bw - font.width(label)) / 2,
                by + (18 - font.lineHeight) / 2,
                WorkbenchTheme.TEXT,
                false);
    }

    void render(GuiGraphics g, int mx, int my) {
        layout();
        jeiIngredients.clear();
        clippingIngredients = false;
        var font = Minecraft.getInstance().font;
        var data = session.snapshot();
        scroll = Math.min(scroll, maxScroll());
        g.pose().pushPose();
        // Native confirmation rendering is suspended; keep standard GUI/JEI tooltip depths.
        g.drawString(font, tr("title"), x + 10, y + 11, WorkbenchTheme.TEXT, false);
        var target = data.target();
        if (target != null) {
            icon(g, target, x + 10, y + 27, 1, false);
            g.drawString(
                    font,
                    font.plainSubstrByWidth(
                            target.what().getDisplayName().getString()
                                    + " × "
                                    + targetAmount(target),
                            w - 52),
                    x + 36,
                    y + 29,
                    WorkbenchTheme.TEXT,
                    false);
        }
        drawStatus(g, tr("status." + data.state().id()), x + 36, y + 41, w - 52);
        int tw = (w - 16 - 4 * tabs.size()) / tabs.size();
        for (int i = 0; i < tabs.size(); i++)
            control(g, x + 10 + i * (tw + 4), y + 52, tw, tabs.get(i).title(), tab == tabs.get(i));
        GenericStack hover = null;
        long hoverTimes = 1;
        Row hoverNeed = null;
        clippingIngredients = true;
        g.enableScissor(x + 8, bodyTop(), x + w - 12, bodyBottom());
        if (tab == Tab.MATERIALS && rows.size() > 1) drawMaterialGrid(g);

        for (int rowIndex = firstVisibleRow(); rowIndex < rows.size(); rowIndex++) {
            var row = rows.get(rowIndex);
            int ry = bodyTop() + offsets[rowIndex] - scroll;
            int rh =
                    row.materialKind() != MaterialKind.NONE
                            ? materialHeight()
                            : (tab == Tab.MATERIALS
                                    ? 18
                                    : offsets[rowIndex + 1] - offsets[rowIndex]);
            if (ry >= bodyBottom()) break;
            if (ry + rh > bodyTop() && ry < bodyBottom()) {
                if (row.materialKind() != MaterialKind.NONE) {
                    var stack = row.inputs().get(0);
                    int cellWidth = (w - 28) / materialColumns();
                    int cx = x + 10 + ((rowIndex - 1) % materialColumns()) * cellWidth;
                    boolean over = mx >= cx && mx < cx + cellWidth && my >= ry && my < ry + rh;
                    drawMaterialCell(g, row, cx, ry, cellWidth, rh, over);
                    if (over) {
                        hover = stack;
                        hoverTimes = 1;
                        hoverNeed = row;
                    }
                } else {
                    g.drawString(
                            font,
                            font.plainSubstrByWidth(row.title().getString(), w - 30),
                            x + 12,
                            ry + 2,
                            WorkbenchTheme.TEXT,
                            false);
                    if (row.grid()) {
                        int gridY = ry + 14;
                        int cols = row.outputs().isEmpty() ? columns() : recipeColumns();
                        var hit =
                                drawRecipeGrid(
                                        g, row.inputs(), x + 12, gridY, row.times(), cols, mx, my);
                        if (hit != null) {
                            hover = hit;
                            hoverTimes = row.times();
                        }
                        if (!row.outputs().isEmpty()) {
                            int outX = x + w / 2 + 10;
                            g.drawString(
                                    font, "→", x + w / 2 - 8, gridY + 5, WorkbenchTheme.DIM, false);
                            hit =
                                    drawRecipeGrid(
                                            g,
                                            row.outputs(),
                                            outX,
                                            gridY,
                                            row.times(),
                                            cols,
                                            mx,
                                            my);
                            if (hit != null) {
                                hover = hit;
                                hoverTimes = row.times();
                            }
                        }
                    } else {
                        var hit =
                                row.outputs().isEmpty()
                                        ? drawPath(g, row.inputs(), x + 12, ry + 18, mx, my)
                                        : drawStrip(
                                                g,
                                                row.inputs(),
                                                x + 12,
                                                ry + 18,
                                                row.times(),
                                                mx,
                                                my);
                        if (hit != null) {
                            hover = hit;
                            hoverTimes = row.times();
                        }
                        if (!row.outputs().isEmpty()) {
                            g.drawString(
                                    font,
                                    Component.literal("→"),
                                    x + w / 2 - 8,
                                    ry + 23,
                                    WorkbenchTheme.ACCENT,
                                    false);
                            hit =
                                    drawStrip(
                                            g,
                                            row.outputs(),
                                            x + w / 2 + 10,
                                            ry + 18,
                                            row.times(),
                                            mx,
                                            my);
                            if (hit != null) {
                                hover = hit;
                                hoverTimes = row.times();
                            }
                        }
                    }
                }
                if (tab != Tab.MATERIALS)
                    g.fill(x + 10, ry + rh - 2, x + w - 18, ry + rh - 1, WorkbenchTheme.LINE);
            }
        }
        if (rows.isEmpty())
            g.drawString(
                    font,
                    tr(tab == Tab.PHASES ? "no_phases" : "empty"),
                    x + 12,
                    bodyTop() + 8,
                    WorkbenchTheme.DIM,
                    false);
        g.disableScissor();
        clippingIngredients = false;
        if (maxScroll() > 0) {
            int bh = bodyBottom() - bodyTop(), thumb = Math.max(16, bh * bh / Math.max(1, total));
            int sy = bodyTop() + (bh - thumb) * scroll / maxScroll();
            g.fill(x + w - 10, bodyTop(), x + w - 6, bodyBottom(), WorkbenchTheme.LINE);
            g.fill(x + w - 10, sy, x + w - 6, sy + thumb, WorkbenchTheme.ACCENT);
        }
        if (tab != Tab.MATERIALS) {
            g.drawString(
                    font,
                    font.plainSubstrByWidth(
                            (tab == Tab.PATHS && exportStatus != null
                                            ? exportStatus
                                            : tr("snapshot"))
                                    .getString(),
                            tab == Tab.PATHS ? w - 100 : w - 20),
                    x + 10,
                    y + h - 13,
                    WorkbenchTheme.DIM,
                    false);
        }
        if (tab == Tab.PATHS && canExportPaths()) {
            control(
                    g,
                    x + w - 76,
                    y + h - 23,
                    64,
                    tr(exporting ? "export_busy" : "export_paths"),
                    false);
            if (exportDirectory != null
                    && mx >= x + 10
                    && mx < x + w - 82
                    && my >= y + h - 23
                    && my < y + h - 3)
                Tooltips.show(
                        g,
                        font,
                        parent.width,
                        List.of(
                                exportStatus,
                                Component.literal(exportDirectory.toAbsolutePath().toString())),
                        mx,
                        my);
        }
        if (target != null && mx >= x + 10 && mx < x + w - 12 && my >= y + 27 && my < y + 41) {
            hover = target;
            hoverTimes = 1;
        }
        if (hover != null
                && ((my >= bodyTop() && my < bodyBottom()) || (my >= y + 27 && my < y + 41))) {
            var lines = new ArrayList<Component>(AEKeyRendering.getTooltip(hover.what()));
            lines.add(
                    tr(
                            "quantity",
                            hover.what()
                                    .formatAmount(
                                            scaled(hover.amount(), hoverTimes),
                                            AmountFormat.FULL)));
            if (hoverNeed != null) {
                lines.add(
                        tr(
                                hoverNeed.materialKind() == MaterialKind.CIRCULATING
                                        ? "circulating"
                                        : "external"));
                lines.add(
                        tr(
                                "available",
                                hover.what()
                                        .formatAmount(hoverNeed.available(), AmountFormat.FULL)));
                lines.add(
                        tr(
                                "shortage",
                                hover.what().formatAmount(hoverNeed.missing(), AmountFormat.FULL)));
            }
            g.renderComponentTooltip(font, lines, mx, my);
        }
        g.pose().popPose();
    }

    /** One shared pixel per grid line, including empty cells in the final row. */
    private void drawMaterialGrid(GuiGraphics g) {
        int cols = materialColumns();
        int cellWidth = (w - 28) / cols;
        int count = (rows.size() - 1 + cols - 1) / cols;
        int left = x + 10, top = bodyTop() + 18 - scroll;
        g.fill(
                left - 1,
                top - 1,
                left + cols * cellWidth,
                top + count * materialHeight(),
                0xFF808080);
        for (int row = 0; row < count; row++) {
            int cy = top + row * materialHeight();
            if (cy + materialHeight() <= bodyTop() || cy >= bodyBottom()) continue;
            for (int col = 0; col < cols; col++) {
                int cx = left + col * cellWidth;
                g.fill(cx, cy, cx + cellWidth - 1, cy + materialHeight() - 1, 0xFFD9D9D9);
            }
        }
    }

    /** Match AE2 AbstractTableRenderer's 68x23 cell, 16px icon and half-size text. */
    private void drawMaterialCell(
            GuiGraphics g, Row row, int cx, int cy, int width, int height, boolean hovered) {
        var mc = Minecraft.getInstance();
        var stack = row.inputs().get(0);
        registerIngredient(stack, cx, cy, width, height);
        int iconX = width - 20;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        AEKeyRendering.drawInGui(mc, g, iconX, 3, stack.what());
        var descriptions = new ArrayList<Component>();
        descriptions.add(
                appeng.core.localization.GuiText.FromStorage.text(
                        stack.what().formatAmount(row.available(), AmountFormat.SLOT)));
        descriptions.add(
                tr("quantity", stack.what().formatAmount(stack.amount(), AmountFormat.SLOT)));
        if (row.missing() > 0)
            descriptions.add(
                    appeng.core.localization.GuiText.Missing.text(
                            stack.what().formatAmount(row.missing(), AmountFormat.SLOT)));
        float lineHeight = mc.font.lineHeight * .5f;
        float lineY =
                Math.round((22 - (descriptions.size() * lineHeight + descriptions.size() - 1)) / 2);
        int color =
                parent.getStyle()
                        .getColor(appeng.client.gui.style.PaletteColor.DEFAULT_TEXT_COLOR)
                        .toARGB();
        g.pose().pushPose();
        g.pose().scale(.5f, .5f, 1);
        for (var line : descriptions) {
            var text =
                    net.minecraft.locale.Language.getInstance()
                            .getVisualOrder(
                                    mc.font.substrByWidth(line, Math.max(1, (iconX - 4) * 2)));
            g.drawString(
                    mc.font,
                    text,
                    (iconX - 2) * 2 - mc.font.width(text),
                    (int) (lineY * 2),
                    color,
                    false);
            lineY += lineHeight + 1;
        }
        g.pose().popPose();
        if (row.missing() > 0) g.fill(0, 0, width - 1, height - 1, 0x1AFF0000);
        if (hovered) g.fill(0, 0, width - 1, height - 1, 0x20FFFFFF);
        g.pose().popPose();
    }

    private static String targetAmount(GenericStack target) {
        return target.what().formatAmount(target.amount(), AmountFormat.FULL);
    }

    /** Auxiliary text alone uses smaller, whole framebuffer pixels; controls stay native-sized. */
    private void drawStatus(GuiGraphics g, Component text, int sx, int sy, int room) {
        var mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        float scale = (float) (Math.max(1, Math.floor(guiScale * .75)) / guiScale);
        g.pose().pushPose();
        g.pose()
                .translate(
                        Math.round(sx * guiScale) / guiScale,
                        Math.round(sy * guiScale) / guiScale,
                        0);
        g.pose().scale(scale, scale, 1);
        g.drawString(
                mc.font,
                net.minecraft.locale.Language.getInstance()
                        .getVisualOrder(
                                mc.font.substrByWidth(text, Math.max(1, (int) (room / scale)))),
                0,
                0,
                WorkbenchTheme.DIM,
                false);
        g.pose().popPose();
    }

    private static long scaled(long value, long times) {
        try {
            return Math.multiplyExact(value, times);
        } catch (ArithmeticException invalid) {
            return Long.MAX_VALUE;
        }
    }

    private GenericStack drawRecipeGrid(
            GuiGraphics g,
            List<GenericStack> slots,
            int sx,
            int sy,
            long times,
            int columnCount,
            int mx,
            int my) {
        GenericStack hover = null;
        for (int i = 0; i < slots.size(); i++) {
            int dx = sx + (i % columnCount) * 18, dy = sy + (i / columnCount) * 18;
            drawRecipeSlot(g, slots.get(i), dx, dy, times);
            if (mx >= dx && mx < dx + 18 && my >= dy && my < dy + 18) hover = slots.get(i);
        }
        return hover;
    }

    /** JEI-style compact slots: quantity overlays stay inside the slot, never between rows. */
    private void drawRecipeSlot(GuiGraphics g, GenericStack stack, int sx, int sy, long times) {
        icon(g, stack, sx, sy, times, false);
        long amount = scaled(stack.amount(), times);
        boolean fluid = stack.what() instanceof AEFluidKey;
        if (!fluid && amount == 1) return;
        var font = Minecraft.getInstance().font;
        String label = StackAmountLabel.format(amount, fluid);
        float scale = fluid || font.width(label) > 16 ? .5f : 1f;
        g.pose().pushPose();
        g.pose().translate(sx + 17, sy + 17, 200);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, label, -font.width(label), -font.lineHeight, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private GenericStack drawPath(
            GuiGraphics g, List<GenericStack> nodes, int sx, int sy, int mx, int my) {
        int cols = Math.max(1, Math.min(nodes.size(), pathColumns()));
        int pitch = Math.min(80, Math.max(1, (w - 28) / cols));
        GenericStack hover = null;
        int color = WorkbenchTheme.DIM;
        // Alternate row direction so a wrapped path remains one continuous chain.
        for (int i = 0; i < nodes.size(); i++) {
            int row = i / cols;
            boolean reverse = (row & 1) != 0;
            int col = reverse ? cols - 1 - i % cols : i % cols;
            int dx = sx + col * pitch, dy = sy + row * 40;
            int iconX = dx + (pitch - 18) / 2;
            if (i + 1 < nodes.size()) {
                boolean turn = (i + 1) % cols == 0;
                int direction = reverse ? -1 : 1;
                if (turn) {
                    int edge = reverse ? dx + 2 : dx + pitch - 3;
                    int from = reverse ? iconX - 2 : iconX + 20;
                    g.hLine(Math.min(from, edge), Math.max(from, edge), dy + 8, color);
                    g.vLine(edge, dy + 8, dy + 48, color);
                    g.hLine(Math.min(from, edge), Math.max(from, edge), dy + 48, color);
                    pathArrow(g, from, dy + 48, -direction, color);
                } else {
                    int from = reverse ? iconX - 2 : iconX + 20;
                    int to = reverse ? iconX - pitch + 20 : iconX + pitch - 2;
                    g.hLine(Math.min(from, to), Math.max(from, to), dy + 8, color);
                    pathArrow(g, to, dy + 8, direction, color);
                }
            }
            icon(g, nodes.get(i), iconX, dy, 1, false);
            var label =
                    Component.literal(Integer.toString(i + 1) + ". ")
                            .append(nodes.get(i).what().getDisplayName());
            drawStatus(g, label, dx + 6, dy + 22, pitch - 12);
            if (mx >= dx + 6 && mx < dx + pitch - 6 && my >= dy && my < dy + 33)
                hover = nodes.get(i);
        }
        return hover;
    }

    private void pathArrow(GuiGraphics g, int tip, int y, int direction, int color) {
        for (int offset = 1; offset <= 3; offset++) {
            int px = tip - direction * offset;
            g.fill(px, y - offset, px + 1, y - offset + 1, color);
            g.fill(px, y + offset, px + 1, y + offset + 1, color);
        }
    }

    private GenericStack drawStrip(
            GuiGraphics g, List<GenericStack> slots, int sx, int sy, long times, int mx, int my) {
        int limit = Math.min(slots.size(), Math.max(1, (w / 2 - 40) / 36));
        GenericStack hover = null;
        for (int i = 0; i < limit; i++) {
            int dx = sx + i * 36;
            icon(g, slots.get(i), dx, sy, times);
            if (mx >= dx && mx < dx + 20 && my >= sy && my < sy + 20) hover = slots.get(i);
        }
        if (slots.size() > limit)
            g.drawString(
                    Minecraft.getInstance().font,
                    "+" + (slots.size() - limit),
                    sx + limit * 36,
                    sy + 6,
                    WorkbenchTheme.DIM,
                    false);
        return hover;
    }

    private void icon(GuiGraphics g, GenericStack stack, int sx, int sy, long times) {
        icon(g, stack, sx, sy, times, true);
    }

    private void icon(
            GuiGraphics g, GenericStack stack, int sx, int sy, long times, boolean showAmount) {
        registerIngredient(stack, sx, sy, 18, 18);
        WorkbenchTheme.slot(g, sx, sy, IngredientPresentation.REGULAR);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), g, sx + 1, sy + 1, stack.what());
        long amount = scaled(stack.amount(), times);
        if (showAmount && (amount > 1 || stack.what() instanceof AEFluidKey)) {
            var font = Minecraft.getInstance().font;
            String label =
                    font.plainSubstrByWidth(
                            StackAmountLabel.format(amount, stack.what() instanceof AEFluidKey),
                            30);
            g.drawString(font, label, sx, sy + 20, WorkbenchTheme.TEXT, false);
        }
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drag(double my) {
        int bh = bodyBottom() - bodyTop(), thumb = Math.max(16, bh * bh / Math.max(1, total));
        scroll =
                (int)
                        Math.max(
                                0,
                                Math.min(
                                        maxScroll(),
                                        (my - bodyTop() - thumb / 2)
                                                * maxScroll()
                                                / Math.max(1, bh - thumb)));
    }

    boolean mouseClicked(double mx, double my, int button) {
        layout();
        // The header belongs to AE2's native return tab. Own only our content and page tabs.
        if (!inside(mx, my) || my < y + 52) return false;
        if (button != 0) return true;
        if (tab == Tab.PATHS
                && mx >= x + w - 76
                && mx < x + w - 12
                && my >= y + h - 23
                && my < y + h - 5) {
            if (canExportPaths()) exportPaths();
            return true;
        }

        if (my >= y + 52 && my < y + 70) {
            int tw = (w - 16 - 4 * tabs.size()) / tabs.size();
            int index = (int) (mx - x - 10) / (tw + 4);
            if (mx >= x + 10 && index >= 0 && index < tabs.size()) {
                tab = tabs.get(index);
                rebuild();
            }
        } else if (mx >= x + w - 13 && my >= bodyTop() && my < bodyBottom()) {
            dragging = true;
            drag(my);
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!inside(mx, my)) return false;
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - delta * 32));
        return true;
    }

    boolean mouseDragged(double my) {
        if (!dragging) return false;
        drag(my);
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }

    void tick() {
        if (session.tick()) rebuild();
    }
}
