package io.github.sexileman.eae2pattern.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.crafting.CraftConfirmScreen;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.client.CyclePlanViewModel.Row;
import io.github.sexileman.eae2pattern.client.CyclePlanViewModel.Tab;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.MaterialKind;
import io.github.sexileman.eae2pattern.net.C2SCycleDiagramPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.net.S2CCycleDiagramPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only panel drawn over the original menu; opening/closing never replaces the container. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT)
public final class CyclePlanOverlay {
    private static CyclePlanOverlay active;
    private List<Tab> tabs = List.of(Tab.MATERIALS, Tab.PATHS, Tab.RECIPES);
    private final CraftConfirmScreen parent;
    private final CycleDiagramSession session;
    private Tab tab = Tab.MATERIALS;
    private int scroll;
    private boolean dragging;
    private List<Row> rows = List.of();
    private int x, y, w, h, total;
    private int measuredWidth = -1;
    private int[] offsets = new int[] {0};

    private CyclePlanOverlay(CraftConfirmScreen parent) {
        this.parent = parent;
        session =
                new CycleDiagramSession(
                        parent.getMenu().containerId,
                        (menu, request) ->
                                PacketHandler.sendToServer(
                                        new C2SCycleDiagramPacket(menu, request)));
        rebuild();
    }

    public static boolean visible(CraftConfirmScreen parent) {
        return active != null && active.parent == parent;
    }

    public static java.util.Optional<net.minecraft.client.renderer.Rect2i> jeiBounds(
            net.minecraft.client.gui.screens.Screen screen) {
        var view = active;
        if (view == null || view.parent != screen) return java.util.Optional.empty();
        view.layout();
        return java.util.Optional.of(
                new net.minecraft.client.renderer.Rect2i(view.x, view.y, view.w, view.h));
    }

    public static void open(CraftConfirmScreen parent) {
        active = new CyclePlanOverlay(parent);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("eae2_pattern.diagram." + key, args);
    }

    public static void accept(S2CCycleDiagramPacket packet) {
        var view = active;
        if (view == null || Minecraft.getInstance().screen != view.parent) return;
        if (view.session.accept(packet.menu(), packet.request(), packet.data())) view.rebuild();
    }

    private void rebuild() {
        tabs = CyclePlanViewModel.tabs(session.snapshot());
        if (!tabs.contains(tab)) tab = Tab.MATERIALS;
        rows = CyclePlanViewModel.rows(session.snapshot(), tab);
        scroll = 0;
        measuredWidth = -1;
    }

    private void layout() {
        var bounds = CyclePanelLayout.fit(parent.width, parent.height);
        w = bounds.width();
        h = bounds.height();
        x = bounds.x();
        y = bounds.y();
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
                    ? 18 + ((row.inputs().size() + pathColumns() - 1) / pathColumns()) * 32
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
        return y + h - 20;
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

    private void render(GuiGraphics g, int mx, int my) {
        layout();
        var font = Minecraft.getInstance().font;
        var data = session.snapshot();
        scroll = Math.min(scroll, maxScroll());
        g.pose().pushPose();
        // Above the hidden confirmation controls, below standard JEI/tooltips (z=400).
        g.pose().translate(0, 0, 100);
        WorkbenchTheme.nativePanel(g, x, y, w, h);
        g.drawString(font, tr("title"), x + 10, y + 11, WorkbenchTheme.TEXT, false);
        control(g, x + w - 54, y + 7, 44, tr("back"), false);
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
        if (maxScroll() > 0) {
            int bh = bodyBottom() - bodyTop(), thumb = Math.max(16, bh * bh / Math.max(1, total));
            int sy = bodyTop() + (bh - thumb) * scroll / maxScroll();
            g.fill(x + w - 10, bodyTop(), x + w - 6, bodyBottom(), WorkbenchTheme.LINE);
            g.fill(x + w - 10, sy, x + w - 6, sy + thumb, WorkbenchTheme.ACCENT);
        }
        if (tab != Tab.MATERIALS) {
            g.drawString(
                    font,
                    font.plainSubstrByWidth(tr("snapshot").getString(), w - 20),
                    x + 10,
                    y + h - 13,
                    WorkbenchTheme.DIM,
                    false);
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
        int start = sx;
        GenericStack hover = null;
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < nodes.size(); i++) {
            int dx = start + (i % cols) * pitch, dy = sy + (i / cols) * 32;
            int iconX = dx + (pitch - 32) / 2;
            icon(g, nodes.get(i), iconX, dy, 1, false);
            drawStatus(g, nodes.get(i).what().getDisplayName(), dx, dy + 20, pitch - 14);
            if (i + 1 < nodes.size())
                g.drawString(
                        font,
                        (i + 1) % cols == 0 ? "↓" : "→",
                        dx + pitch - 13,
                        dy + 5,
                        WorkbenchTheme.DIM,
                        false);
            if (mx >= dx && mx < dx + pitch - 14 && my >= dy && my < dy + 30) hover = nodes.get(i);
        }
        return hover;
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

    // Match a normal screen's order: panel content first, JEI overlays/tooltips afterwards.
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void render(ScreenEvent.Render.Post event) {
        if (active != null && event.getScreen() == active.parent)
            active.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        var a = active;
        if (a == null
                || event.getScreen() != a.parent
                || !a.inside(event.getMouseX(), event.getMouseY())) return;
        event.setCanceled(true);
        if (event.getButton() != 0) return;
        double mx = event.getMouseX(), my = event.getMouseY();
        if (my >= a.y + 7 && my < a.y + 25 && mx >= a.x + a.w - 54) {
            active = null;
            return;
        }
        if (my >= a.y + 52 && my < a.y + 70) {
            int tw = (a.w - 16 - 4 * a.tabs.size()) / a.tabs.size();
            int tab = (int) (mx - a.x - 10) / (tw + 4);
            if (tab >= 0 && tab < a.tabs.size()) {
                a.tab = a.tabs.get(tab);
                a.rebuild();
            }
            return;
        }
        if (mx >= a.x + a.w - 13 && my >= a.bodyTop() && my < a.bodyBottom()) {
            a.dragging = true;
            a.drag(my);
        }
    }

    @SubscribeEvent
    public static void wheel(ScreenEvent.MouseScrolled.Pre event) {
        var a = active;
        if (a != null
                && event.getScreen() == a.parent
                && a.inside(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            a.scroll =
                    (int)
                            Math.max(
                                    0,
                                    Math.min(
                                            a.maxScroll(), a.scroll - event.getScrollDelta() * 32));
        }
    }

    @SubscribeEvent
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        var a = active;
        if (a != null && event.getScreen() == a.parent && a.dragging) {
            event.setCanceled(true);
            a.drag(event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (active != null && event.getScreen() == active.parent && active.dragging) {
            active.dragging = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        var a = active;
        if (a == null || event.getScreen() != a.parent) return;
        if (event.getKeyCode() == 256) {
            active = null;
            event.setCanceled(true);
            return;
        }
        var mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * a.parent.width / mc.getWindow().getScreenWidth(),
                my = mc.mouseHandler.ypos() * a.parent.height / mc.getWindow().getScreenHeight();
        // Do not let an inspection key activate a hidden native control under the panel.
        if (a.inside(mx, my)
                || event.getKeyCode() == 257
                || event.getKeyCode() == 32
                || event.getKeyCode() == 258) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) return;
        var view = active;
        if (Minecraft.getInstance().screen != view.parent) {
            active = null;
            return;
        }
        if (view.session.tick()) view.rebuild();
    }
}
