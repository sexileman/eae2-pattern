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
    private static final List<Tab> TABS = List.of(Tab.values());
    private final CraftConfirmScreen parent;
    private final CycleDiagramSession session;
    private Tab tab = Tab.PATHS;
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
        rows = CyclePlanViewModel.rows(session.snapshot(), tab);
        scroll = 0;
        measuredWidth = -1;
    }

    private void layout() {
        w = Math.min(560, parent.width - 32);
        h = Math.min(370, parent.height - 20);
        x = (parent.width - w) / 2;
        y = (parent.height - h) / 2;
        if (measuredWidth != w) measureRows();
    }

    private int columns() {
        return Math.max(1, (w - 36) / 24);
    }

    private int height(Row row) {
        if (row.materialKind() != MaterialKind.NONE) return 58;
        if (row.inputs().isEmpty() && row.outputs().isEmpty()) return 24;
        if (!row.grid()) return row.outputs().isEmpty() ? 58 : 68;
        return 20
                + ((row.inputs().size() + columns() - 1) / columns()) * 24
                + (row.outputs().isEmpty()
                        ? 0
                        : 26 + ((row.outputs().size() + columns() - 1) / columns()) * 24);
    }

    private void measureRows() {
        offsets = new int[rows.size() + 1];
        for (int i = 0; i < rows.size(); i++) offsets[i + 1] = offsets[i] + height(rows.get(i));
        total = offsets[rows.size()];
        measuredWidth = w;
    }

    private int firstVisibleRow() {
        int index = Arrays.binarySearch(offsets, scroll);
        return Math.max(0, index >= 0 ? index : -index - 2);
    }

    private int bodyTop() {
        return y + 101;
    }

    private int bodyBottom() {
        return y + h - 28;
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
        g.drawCenteredString(
                Minecraft.getInstance().font, label, bx + bw / 2, by + 5, WorkbenchTheme.TEXT);
    }

    private void render(GuiGraphics g, int mx, int my) {
        layout();
        var font = Minecraft.getInstance().font;
        var data = session.snapshot();
        scroll = Math.min(scroll, maxScroll());
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        WorkbenchTheme.panel(g, x, y, w, h);
        g.drawString(font, tr("title"), x + 10, y + 11, WorkbenchTheme.TEXT, false);
        control(g, x + w - 54, y + 7, 44, tr("back"), false);
        g.drawString(
                font,
                tr("status." + data.state().id()),
                x + 10,
                y + 33,
                WorkbenchTheme.ACCENT,
                false);
        var target = data.target();
        if (target != null)
            g.drawString(
                    font,
                    font.plainSubstrByWidth(
                            target.what().getDisplayName().getString()
                                    + " × "
                                    + target.what()
                                            .formatAmount(target.amount(), AmountFormat.FULL),
                            w - 100),
                    x + 10,
                    y + 48,
                    WorkbenchTheme.TEXT,
                    false);
        int tw = (w - 16 - 4 * TABS.size()) / TABS.size();
        for (int i = 0; i < TABS.size(); i++)
            control(g, x + 10 + i * (tw + 4), y + 70, tw, TABS.get(i).title(), tab == TABS.get(i));
        GenericStack hover = null;
        long hoverTimes = 1;
        Row hoverNeed = null;
        g.enableScissor(x + 8, bodyTop(), x + w - 12, bodyBottom());
        for (int rowIndex = firstVisibleRow(); rowIndex < rows.size(); rowIndex++) {
            var row = rows.get(rowIndex);
            int ry = bodyTop() + offsets[rowIndex] - scroll;
            int rh = offsets[rowIndex + 1] - offsets[rowIndex];
            if (ry >= bodyBottom()) break;
            if (ry + rh > bodyTop() && ry < bodyBottom()) {
                if (row.materialKind() != MaterialKind.NONE) {
                    var stack = row.inputs().get(0);
                    icon(g, stack, x + 12, ry + 5, 1);
                    String label =
                            row.title().getString()
                                    + " · "
                                    + tr(row.materialKind() == MaterialKind.CIRCULATING
                                                    ? "circulating"
                                                    : "external")
                                            .getString();
                    g.drawString(
                            font,
                            font.plainSubstrByWidth(label, w - 56),
                            x + 38,
                            ry + 2,
                            WorkbenchTheme.TEXT,
                            false);
                    g.drawString(
                            font,
                            font.plainSubstrByWidth(
                                    tr(
                                                    "need_have",
                                                    stack.what()
                                                            .formatAmount(
                                                                    stack.amount(),
                                                                    AmountFormat.FULL),
                                                    stack.what()
                                                            .formatAmount(
                                                                    row.available(),
                                                                    AmountFormat.FULL))
                                            .getString(),
                                    w - 56),
                            x + 38,
                            ry + 17,
                            WorkbenchTheme.DIM,
                            false);
                    g.drawString(
                            font,
                            row.missing() > 0
                                    ? tr(
                                            "shortage",
                                            stack.what()
                                                    .formatAmount(row.missing(), AmountFormat.FULL))
                                    : tr("sufficient"),
                            x + 38,
                            ry + 32,
                            row.missing() > 0 ? WorkbenchTheme.WARNING : WorkbenchTheme.ACCENT,
                            false);
                    if (mx >= x + 12 && mx < x + 32 && my >= ry + 5 && my < ry + 25) {
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
                        int inY = ry + 17;
                        if (!row.outputs().isEmpty()) {
                            g.drawString(
                                    font, tr("inputs"), x + 12, inY, WorkbenchTheme.ACCENT, false);
                            inY += 12;
                        }
                        var hit = drawGrid(g, row.inputs(), x + 12, inY, row.times(), mx, my);
                        if (hit != null) {
                            hover = hit;
                            hoverTimes = row.times();
                        }
                        int outY =
                                inY + 1 + ((row.inputs().size() + columns() - 1) / columns()) * 24;
                        if (!row.outputs().isEmpty()) {
                            g.drawString(
                                    font,
                                    tr("outputs"),
                                    x + 12,
                                    outY,
                                    WorkbenchTheme.ACCENT,
                                    false);
                            hit =
                                    drawGrid(
                                            g,
                                            row.outputs(),
                                            x + 12,
                                            outY + 12,
                                            row.times(),
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
                g.fill(x + 10, ry + rh - 4, x + w - 18, ry + rh - 3, WorkbenchTheme.LINE);
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
        g.drawString(
                font,
                font.plainSubstrByWidth(
                        tr(tab == Tab.MATERIALS ? "stock_snapshot" : "snapshot").getString(),
                        w - 20),
                x + 10,
                y + h - 17,
                WorkbenchTheme.DIM,
                false);
        if (hover != null && my >= bodyTop() && my < bodyBottom()) {
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

    private static long scaled(long value, long times) {
        try {
            return Math.multiplyExact(value, times);
        } catch (ArithmeticException invalid) {
            return Long.MAX_VALUE;
        }
    }

    private GenericStack drawGrid(
            GuiGraphics g, List<GenericStack> slots, int sx, int sy, long times, int mx, int my) {
        GenericStack hover = null;
        for (int i = 0; i < slots.size(); i++) {
            int dx = sx + (i % columns()) * 24, dy = sy + (i / columns()) * 24;
            icon(g, slots.get(i), dx, dy, times);
            if (mx >= dx && mx < dx + 20 && my >= dy && my < dy + 20) hover = slots.get(i);
        }
        return hover;
    }

    private GenericStack drawPath(
            GuiGraphics g, List<GenericStack> nodes, int sx, int sy, int mx, int my) {
        int limit = Math.min(nodes.size(), Math.max(1, (w - 40) / 52));
        GenericStack hover = null;
        for (int i = 0; i < limit; i++) {
            int dx = sx + i * 52;
            icon(g, nodes.get(i), dx, sy, 1);
            var font = Minecraft.getInstance().font;
            g.drawString(
                    font,
                    font.plainSubstrByWidth(nodes.get(i).what().getDisplayName().getString(), 46),
                    dx,
                    sy + 23,
                    WorkbenchTheme.DIM,
                    false);
            if (i + 1 < limit)
                g.drawString(font, "→", dx + 29, sy + 5, WorkbenchTheme.ACCENT, false);
            if (mx >= dx && mx < dx + 20 && my >= sy && my < sy + 20) hover = nodes.get(i);
        }
        if (limit < nodes.size())
            g.drawString(
                    Minecraft.getInstance().font,
                    "…",
                    sx + limit * 52,
                    sy + 5,
                    WorkbenchTheme.DIM,
                    false);
        return hover;
    }

    private GenericStack drawStrip(
            GuiGraphics g, List<GenericStack> slots, int sx, int sy, long times, int mx, int my) {
        int limit = Math.min(slots.size(), Math.max(1, (w / 2 - 40) / 24));
        GenericStack hover = null;
        for (int i = 0; i < limit; i++) {
            int dx = sx + i * 24;
            icon(g, slots.get(i), dx, sy, times);
            if (mx >= dx && mx < dx + 20 && my >= sy && my < sy + 20) hover = slots.get(i);
        }
        if (slots.size() > limit)
            g.drawString(
                    Minecraft.getInstance().font,
                    "+" + (slots.size() - limit),
                    sx + limit * 24,
                    sy + 6,
                    WorkbenchTheme.DIM,
                    false);
        return hover;
    }

    private void icon(GuiGraphics g, GenericStack stack, int sx, int sy, long times) {
        WorkbenchTheme.slot(g, sx, sy, IngredientPresentation.REGULAR);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), g, sx + 1, sy + 1, stack.what());
        var label =
                StackAmountLabel.format(
                        scaled(stack.amount(), times), stack.what() instanceof AEFluidKey);
        if (scaled(stack.amount(), times) > 1 || stack.what() instanceof AEFluidKey) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.pose().scale(.7f, .7f, 1);
            g.drawString(
                    Minecraft.getInstance().font,
                    label,
                    (int) ((sx + 18) / .7f) - Minecraft.getInstance().font.width(label),
                    (int) ((sy + 12) / .7f),
                    0xFFFFFFFF,
                    true);
            g.pose().popPose();
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

    @SubscribeEvent
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
        if (my >= a.y + 70 && my < a.y + 88) {
            int tw = (a.w - 16 - 4 * TABS.size()) / TABS.size();
            int tab = (int) (mx - a.x - 10) / (tw + 4);
            if (tab >= 0 && tab < TABS.size()) {
                a.tab = TABS.get(tab);
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
