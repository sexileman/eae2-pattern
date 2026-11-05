package io.github.sexileman.eae2pattern.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.value.IStringValue;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.factory.ClientGUI;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The preview screen, assembled from ModularUI widgets instead of hand drawn rectangles.
 *
 * <p>ModularUI is the widget library GTCEu uses for its machine screens, so the result belongs to the
 * same visual family as the rest of the pack. Everything is laid out in the logical pixels of a 480x270
 * canvas, which is what GUI scale 4 gives on a 1920x1080 window.</p>
 */
public final class PreviewGui implements GenerationListener {

    private static final int PANEL_W = 468;
    private static final int PANEL_H = 258;

    private static final int HEADER_H = 26;
    private static final int CARD_TITLE_H = 13;
    private static final int ROW_H = 12;
    private static final int ICON = 10;
    private static final int ICON_STEP = 11;
    private static final int FIELD_H = 12;

    private static final int COLOR_TEXT = 0xFFE9EBEF;
    private static final int COLOR_DIM = 0xFF9AA0AA;
    private static final int COLOR_MUTED = 0xFF6E7482;
    private static final int COLOR_ACCENT = 0xFF4C9AFF;
    private static final int COLOR_OK = 0xFF5FC97A;
    private static final int COLOR_WARN = 0xFFE0A040;
    private static final int COLOR_BAD = 0xFFE06A6A;
    private static final int COLOR_ON_ACCENT = 0xFF0B0E13;

    private static final int CARD_FILL = 0x9E12151B;
    private static final int CARD_BORDER = 0x38FFFFFF;
    private static final int ROW_SELECTED = 0x8C2F4E77;
    private static final int ROW_HOVER = 0x2EFFFFFF;
    private static final int CHIP_ON = 0xCC4C9AFF;
    private static final int CHIP_OFF = 0x22FFFFFF;
    private static final int HAIRLINE = 0x30FFFFFF;

    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};

    private static final String[] FLAGS = {
            "unconsumed", "chance_input", "drop_unconsumed",
            "chance_output", "chance_byproduct", "drop_chance"
    };

    private final RecipePlan plan;
    private final Screen parent;
    private final ModularPanel<?> panel;
    private final ParentWidget<?> host;
    private final List<IWidget> dynamic = new ArrayList<>();

    private final Set<String> flags = new LinkedHashSet<>();
    private final ItemSearchQuery[] queries = {ItemSearchQuery.parse(""), ItemSearchQuery.parse("")};
    private final TextFieldWidget[] fields = new TextFieldWidget[2];

    private List<RecipePlan.Entry> visible;
    private int page;
    private int multiplier = 1;
    private Component notice;
    private int noticeColor = COLOR_OK;
    private RecipePlan.Entry focused;

    private PreviewGui(RecipePlan plan, Screen parent) {
        this.plan = plan;
        this.parent = parent;
        this.panel = ModularPanel.defaultPanel("eae2_pattern_preview", PANEL_W, PANEL_H);
        this.host = this.panel;
        this.visible = List.copyOf(plan.entries());
        this.focused = this.visible.isEmpty() ? null : this.visible.get(0);
    }

    public static void open(RecipePlan plan, Screen parent) {
        PreviewGui gui = new PreviewGui(plan, parent);
        ClientGenerationFeedback.setListener(gui);
        ClientGUI.open(new ModularScreen(EAE2Pattern.MOD_ID, gui.panel));
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0));
        gui.rebuild();
    }

    @Override
    public void onGenerationFinished(GenerationReport report) {
        this.notice = ClientGenerationFeedback.describe(report);
        this.noticeColor = report.isSuccess() ? COLOR_OK : COLOR_BAD;
        this.rebuild();
    }

    // ---------------------------------------------------------------- assembly

    private void rebuild() {
        for (IWidget widget : this.dynamic) {
            this.host.remove(widget);
        }
        this.dynamic.clear();

        this.buildHeader();
        this.buildFilterCard();
        this.buildListCard();
        this.buildDetailCard();
        this.buildActionCard();
        this.buildNotice();
    }

    private <T extends IWidget> T add(T widget) {
        this.host.child(widget);
        this.dynamic.add(widget);
        return widget;
    }

    private void buildHeader() {
        this.add(new TextWidget<>(Component.translatable("eae2_pattern.preview.title", this.plan.machineName()))
                .pos(8, 4).size(PANEL_W - 70, 10).color(COLOR_TEXT));
        this.add(new TextWidget<>(Component.translatable("eae2_pattern.preview.subtitle",
                this.plan.selectedCount(), this.visible.size(), this.plan.entries().size()))
                .pos(8, 15).size(PANEL_W - 70, 8).color(COLOR_DIM));
        this.add(new ButtonWidget<>().pos(PANEL_W - 16, 4).size(11, 11)
                .background(rounded(0x22FFFFFF))
                .onMouseTapped((context, button) -> {
                    this.close();
                    return true;
                })
                .child(new TextWidget<>("x").size(11, 11).alignment(Alignment.Center).color(COLOR_DIM)));
    }

    private ParentWidget<?> card(int x, int y, int width, int height, String titleKey, String trailing) {
        ParentWidget<?> card = this.add(new ParentWidget<>().pos(x, y).size(width, height)
                .background(frame(CARD_FILL, CARD_BORDER)));
        card.child(new TextWidget<>(Component.translatable(titleKey))
                .pos(5, 3).size(width - 10, 8).color(COLOR_DIM));
        if (trailing != null && !trailing.isEmpty()) {
            card.child(new TextWidget<>(trailing).pos(5, 3).size(width - 10, 8)
                    .alignment(Alignment.TopRight).color(COLOR_MUTED));
        }
        card.child(new Widget<>().pos(4, CARD_TITLE_H).size(width - 8, 1)
                .background(new Rectangle().color(HAIRLINE)));
        return card;
    }

    // ---------------------------------------------------------------- filters

    private void buildFilterCard() {
        int cardW = 130;
        int cardH = PANEL_H - HEADER_H - 8;
        ParentWidget<?> card = this.card(4, HEADER_H, cardW, cardH, "eae2_pattern.preview.module.filter",
                Component.translatable("eae2_pattern.preview.module.filter_count").getString());

        if (this.fields[0] == null) {
            this.fields[0] = this.field(CARD_TITLE_H + 5, cardW, 0, "ingredients_hint");
            this.fields[1] = this.field(CARD_TITLE_H + 5 + FIELD_H + 3, cardW, 1, "products_hint");
        }
        for (TextFieldWidget field : this.fields) {
            card.child(field);
        }

        int y = CARD_TITLE_H + 5 + (FIELD_H + 3) * 2 + 2;
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.section.conditions"))
                .pos(6, y).size(cardW - 12, 8).color(COLOR_MUTED));

        y += 10;
        for (int i = 0; i < FLAGS.length; i++) {
            String key = FLAGS[i];
            boolean on = this.flags.contains(key);
            int chipX = 6 + (i >= 3 ? 58 : 0);
            int chipY = y + (i % 3) * 12;
            card.child(new ButtonWidget<>()
                    .pos(chipX, chipY).size(54, 10)
                    .background(rounded(on ? CHIP_ON : CHIP_OFF))
                    .onMouseTapped((context, button) -> {
                        if (this.flags.contains(key)) {
                            this.flags.remove(key);
                        } else {
                            this.flags.add(key);
                        }
                        this.refresh();
                        return true;
                    })
                    .child(new TextWidget<>(Component.translatable("eae2_pattern.preview.filter." + key))
                            .size(54, 10).alignment(Alignment.Center)
                            .color(on ? COLOR_ON_ACCENT : COLOR_DIM)));
        }

        int legendY = y + 3 * 12 + 6;
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.legend.title"))
                .pos(6, legendY).size(cardW - 12, 8).color(COLOR_MUTED));
        for (int i = 0; i < 4; i++) {
            card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.legend." + i))
                    .pos(6, legendY + 10 + i * 10).size(cardW - 12, 8).color(COLOR_DIM));
        }
    }

    private TextFieldWidget field(int y, int cardW, int index, String hintKey) {
        TextFieldWidget field = new TextFieldWidget()
                .pos(6, y).size(cardW - 12, FIELD_H)
                .setMaxLength(96)
                .value(new StringValue(this.queries[index].raw(), value -> {
                    this.queries[index] = ItemSearchQuery.parse(value);
                    this.page = 0;
                    this.refresh();
                }));
        field.tooltipBuilder(tooltip -> tooltip.add(Component.translatable("eae2_pattern.preview.filter." + hintKey)));
        return field;
    }

    // ---------------------------------------------------------------- list

    private void buildListCard() {
        int cardX = 4 + 130 + 6;
        int cardW = 190;
        int cardH = PANEL_H - HEADER_H - 8;
        ParentWidget<?> card = this.card(cardX, HEADER_H, cardW, cardH, "eae2_pattern.preview.module.list",
                this.visible.size() + " / " + this.plan.entries().size());

        int headerY = CARD_TITLE_H + 2;
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.column.input"))
                .pos(16, headerY).size(34, 8).color(COLOR_MUTED));
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.column.output"))
                .pos(58, headerY).size(40, 8).color(COLOR_MUTED));
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.column.recipe"))
                .pos(102, headerY).size(60, 8).color(COLOR_MUTED));

        int footerHeight = 13;
        int rowAreaTop = headerY + 9;
        int rows = Math.max(1, (cardH - footerHeight - 3 - rowAreaTop) / ROW_H);
        int pages = Math.max(1, (this.visible.size() + rows - 1) / rows);
        this.page = Math.max(0, Math.min(this.page, pages - 1));

        int from = this.page * rows;
        int to = Math.min(this.visible.size(), from + rows);
        List<RecipePlan.Entry> pageRows = this.visible.subList(from, to);

        for (int i = 0; i < pageRows.size(); i++) {
            card.child(this.row(pageRows.get(i), i, rowAreaTop, cardW));
        }
        if (pageRows.isEmpty()) {
            card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.no_match"))
                    .pos(0, rowAreaTop + 24).size(cardW, 8).alignment(Alignment.Center).color(COLOR_MUTED));
        }

        int footerY = cardH - footerHeight;
        card.child(new ButtonWidget<>().pos(4, footerY + 2).size(12, 11)
                .background(rounded(0x22FFFFFF))
                .onMouseTapped((context, button) -> {
                    this.page = Math.max(0, this.page - 1);
                    this.refresh();
                    return true;
                })
                .child(new TextWidget<>("<").size(12, 11).alignment(Alignment.Center).color(COLOR_DIM)));
        card.child(new TextWidget<>((this.page + 1) + " / " + pages)
                .pos(cardW - 78, footerY + 3).size(50, 8).alignment(Alignment.Center).color(COLOR_DIM));
        card.child(new ButtonWidget<>().pos(cardW - 24, footerY + 2).size(12, 11)
                .background(rounded(0x22FFFFFF))
                .onMouseTapped((context, button) -> {
                    this.page = Math.min(pages - 1, this.page + 1);
                    this.refresh();
                    return true;
                })
                .child(new TextWidget<>(">").size(12, 11).alignment(Alignment.Center).color(COLOR_DIM)));

        for (String tool : new String[] {"select_all", "invert", "dedupe", "only_selected"}) {
            int index = List.of("select_all", "invert", "dedupe", "only_selected").indexOf(tool);
            card.child(new ButtonWidget<>()
                    .pos(20 + index * 30, footerY + 1).size(28, 11)
                    .background(rounded(0x1AFFFFFF))
                    .onMouseTapped((context, button) -> {
                        this.applyTool(tool);
                        return true;
                    })
                    .child(new TextWidget<>(Component.translatable("eae2_pattern.preview.tool." + tool))
                            .size(28, 11).alignment(Alignment.Center).color(COLOR_DIM)));
        }
    }

    private IWidget row(RecipePlan.Entry entry, int index, int top, int cardW) {
        int y = top + index * ROW_H;
        boolean selected = entry.isSelected();
        ParentWidget<?> content = new ParentWidget<>().size(cardW - 8, ROW_H);

        int x = 12;
        int shown = Math.min(3, entry.inputs().size());
        for (int i = 0; i < shown; i++) {
            content.child(new Widget<>().pos(x, 1).size(ICON, ICON)
                    .background(new ItemDrawable(entry.inputs().get(i).spec().toItemStack())));
            x += ICON_STEP;
        }
        content.child(new TextWidget<>(">").pos(x + 1, 2).size(6, 8).color(COLOR_MUTED));
        x += 9;

        int outputs = Math.min(2, entry.outputs().size());
        for (int i = 0; i < outputs; i++) {
            content.child(new Widget<>().pos(x, 1).size(ICON, ICON)
                    .background(new ItemDrawable(entry.outputs().get(i).spec().toItemStack())));
            x += ICON_STEP;
        }

        content.child(new TextWidget<>(entry.label()).pos(x + 4, 2).size(cardW - 8 - x - 34, 8)
                .color(selected ? COLOR_TEXT : COLOR_DIM));

        String badge = badgeText(entry);
        if (!badge.isEmpty()) {
            content.child(new TextWidget<>(badge).pos(cardW - 42, 2).size(38, 8)
                    .alignment(Alignment.Center).color(badgeColor(entry)));
        }

        return new ButtonWidget<>()
                .pos(4, y).size(cardW - 8, ROW_H)
                .background(rounded(selected ? ROW_SELECTED : 0x00000000))
                .hoverBackground(rounded(ROW_HOVER))
                .onMouseTapped((context, button) -> {
                    entry.toggle();
                    this.focused = entry;
                    this.refresh();
                    return true;
                })
                .child(content);
    }

    // ---------------------------------------------------------------- detail

    private void buildDetailCard() {
        int cardW = 128;
        int cardX = PANEL_W - 4 - cardW;
        int cardH = 132;
        RecipePlan.Entry entry = this.focused;
        ParentWidget<?> card = this.card(cardX, HEADER_H, cardW, cardH, "eae2_pattern.preview.module.detail",
                entry == null ? "" : "1");

        if (entry == null) {
            card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.detail.none"))
                    .pos(0, 50).size(cardW, 8).alignment(Alignment.Center).color(COLOR_MUTED));
            return;
        }

        int cell = 16;
        int gridLeft = (cardW - cell * 3) / 2;
        List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < Math.min(9, entry.inputs().size()); i++) {
            stacks.add(entry.inputs().get(i).spec().toItemStack());
        }
        for (int i = 0; i < Math.min(9 - stacks.size(), entry.outputs().size()); i++) {
            stacks.add(entry.outputs().get(i).spec().toItemStack());
        }
        for (int i = 0; i < 9; i++) {
            int cx = gridLeft + (i % 3) * cell;
            int cy = CARD_TITLE_H + 4 + (i / 3) * cell;
            card.child(new Widget<>().pos(cx, cy).size(cell - 1, cell - 1)
                    .background(new Rectangle().color(0x1AFFFFFF).cornerRadius(1)));
            if (i < stacks.size()) {
                card.child(new Widget<>().pos(cx + 1, cy + 2).size(ICON, ICON)
                        .background(new ItemDrawable(stacks.get(i))));
            }
        }

        int nameY = CARD_TITLE_H + 4 + cell * 3 + 3;
        card.child(new TextWidget<>(entry.label()).pos(5, nameY).size(cardW - 10, 8).color(COLOR_TEXT));
        card.child(new Widget<>().pos(4, nameY + 10).size(cardW - 8, 1)
                .background(new Rectangle().color(HAIRLINE)));

        int rowY = nameY + 13;
        this.detailRow(card, 5, rowY, cardW - 10, "io",
                entry.inputs().size() + " / " + entry.outputs().size());
        this.detailRow(card, 5, rowY + 11, cardW - 10, "kind",
                Component.translatable(kindKey(entry.kind())).getString());
        this.detailRow(card, 5, rowY + 22, cardW - 10, "id", entry.recipeId().toString());
    }

    private void detailRow(ParentWidget<?> card, int x, int y, int width, String label, String value) {
        card.child(new TextWidget<>(Component.translatable("eae2_pattern.preview.detail.label." + label))
                .pos(x, y).size(38, 8).color(COLOR_MUTED));
        card.child(new TextWidget<>(value).pos(x + 40, y).size(width - 40, 8)
                .alignment(Alignment.TopRight).color(COLOR_DIM));
    }

    // ---------------------------------------------------------------- actions

    private void buildActionCard() {
        int cardW = 128;
        int cardX = PANEL_W - 4 - cardW;
        int cardH = PANEL_H - HEADER_H - 8 - 132 - 6;
        int cardY = HEADER_H + 132 + 6;
        ParentWidget<?> card = this.card(cardX, cardY, cardW, cardH, "eae2_pattern.preview.module.action", "");

        card.child(new TextWidget<>("x" + this.multiplier).pos(4, 17).size(18, 8).color(COLOR_DIM));
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            int value = MULTIPLIERS[i];
            boolean on = value == this.multiplier;
            card.child(new ButtonWidget<>()
                    .pos(22 + i * 15, 16).size(13, 10)
                    .background(rounded(on ? CHIP_ON : CHIP_OFF))
                    .onMouseTapped((context, button) -> {
                        this.multiplier = value;
                        this.refresh();
                        return true;
                    })
                    .child(new TextWidget<>(String.valueOf(value)).size(13, 10)
                            .alignment(Alignment.Center).color(on ? COLOR_ON_ACCENT : COLOR_DIM)));
        }

        List<PatternSpec> specs = this.selectedSpecs();
        card.child(new ButtonWidget<>()
                .pos(4, 30).size(cardW - 8, 14)
                .background(rounded(specs.isEmpty() ? 0x24FFFFFF : COLOR_ACCENT))
                .onMouseTapped((context, button) -> {
                    if (!specs.isEmpty()) {
                        PatternRequestSender.send(specs);
                        this.close();
                    }
                    return true;
                })
                .child(new TextWidget<>(specs.isEmpty()
                        ? Component.translatable("eae2_pattern.preview.generate")
                        : Component.translatable("eae2_pattern.preview.generate_count", specs.size()))
                        .size(cardW - 8, 14).alignment(Alignment.Center)
                        .color(specs.isEmpty() ? COLOR_MUTED : COLOR_ON_ACCENT)));

        card.child(new ButtonWidget<>()
                .pos(4, 47).size(cardW - 8, 12)
                .background(frame(0x14FFFFFF, 0x40FFFFFF))
                .onMouseTapped((context, button) -> {
                    this.close();
                    return true;
                })
                .child(new TextWidget<>(Component.translatable("eae2_pattern.preview.cancel"))
                        .size(cardW - 8, 12).alignment(Alignment.Center).color(COLOR_DIM)));
    }

    private void buildNotice() {
        if (this.notice == null) {
            return;
        }
        String text = this.notice.getString();
        int width = Math.min(PANEL_W - 260, Math.max(64, text.length() * 5 + 12));
        this.add(new Widget<>().pos(PANEL_W - 24 - width, 3).size(width, 12)
                .background(frame(0xC8000000, this.noticeColor)));
        this.add(new TextWidget<>(this.notice).pos(PANEL_W - 24 - width, 5).size(width, 8)
                .alignment(Alignment.Center).color(this.noticeColor));
    }

    // ---------------------------------------------------------------- state

    private void refresh() {
        RecipeFilters filters = new RecipeFilters(
                this.queries[1],
                this.queries[0],
                this.flags.contains("unconsumed"),
                this.flags.contains("chance_input"),
                this.flags.contains("chance_output"),
                this.flags.contains("chance_byproduct"),
                false);
        List<RecipePlan.Entry> result = new ArrayList<>();
        for (RecipePlan.Entry entry : this.plan.entries()) {
            if (!filters.rejects(entry)) {
                result.add(entry);
            }
        }
        this.visible = result;
        if (this.focused == null || !this.visible.contains(this.focused)) {
            this.focused = this.visible.isEmpty() ? null : this.visible.get(0);
        }
        this.rebuild();
    }

    private void applyTool(String tool) {
        switch (tool) {
            case "select_all" -> {
                for (RecipePlan.Entry entry : this.visible) {
                    entry.setSelected(true);
                }
            }
            case "invert" -> {
                for (RecipePlan.Entry entry : this.visible) {
                    entry.toggle();
                }
            }
            case "dedupe" -> {
                Set<String> seen = new LinkedHashSet<>();
                for (RecipePlan.Entry entry : this.visible) {
                    if (!seen.add(entry.outputKey())) {
                        entry.setSelected(false);
                    }
                }
            }
            case "only_selected" -> this.visible.removeIf(entry -> !entry.isSelected());
            default -> {
            }
        }
        this.refresh();
    }

    private List<PatternSpec> selectedSpecs() {
        return this.plan.selectedSpecs(this.visible, this.multiplier,
                this.flags.contains("drop_chance"), this.flags.contains("drop_unconsumed"));
    }

    private void close() {
        ClientGenerationFeedback.setListener(null);
        ClientGUI.close();
        if (this.parent != null) {
            net.minecraft.client.Minecraft.getInstance().setScreen(this.parent);
        }
    }

    private static String badgeText(RecipePlan.Entry entry) {
        if (entry.hasProbabilisticByproduct()) {
            return "BY%";
        }
        if (entry.hasProbabilisticOutput()) {
            return "OUT%";
        }
        if (entry.hasProbabilisticInput()) {
            return "IN%";
        }
        if (entry.hasNeverConsumedInput()) {
            return "NC";
        }
        return "";
    }

    private static int badgeColor(RecipePlan.Entry entry) {
        if (entry.hasProbabilisticByproduct() || entry.hasProbabilisticInput()) {
            return COLOR_WARN;
        }
        if (entry.hasProbabilisticOutput()) {
            return COLOR_BAD;
        }
        return COLOR_ACCENT;
    }

    private static String kindKey(PatternKind kind) {
        return "eae2_pattern.preview.kind." + kind.name().toLowerCase(Locale.ROOT);
    }

    private static IDrawable rounded(int fill) {
        return new Rectangle().color(fill).cornerRadius(2);
    }

    private static IDrawable frame(int fill, int border) {
        return new FramedRect(fill, border);
    }

    /** A rounded fill with a one pixel outline, drawn as two nested rectangles. */
    private record FramedRect(int fill, int border) implements IDrawable {
        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
            new Rectangle().color(this.border).cornerRadius(2).draw(context, x, y, width, height, theme);
            new Rectangle().color(this.fill).cornerRadius(2).draw(context, x + 1, y + 1, width - 2, height - 2,
                    theme);
        }
    }

    /** Bridges a text field to the filter state so typing filters the list immediately. */
    private static final class StringValue implements IStringValue<String> {
        private String value;
        private final java.util.function.Consumer<String> onChange;

        private StringValue(String initial, java.util.function.Consumer<String> onChange) {
            this.value = initial == null ? "" : initial;
            this.onChange = onChange;
        }

        @Override
        public String getStringValue() {
            return this.value;
        }

        @Override
        public void setStringValue(String value) {
            this.value = value == null ? "" : value;
            this.onChange.accept(this.value);
        }

        @Override
        public String getValue() {
            return this.value;
        }

        @Override
        public void setValue(String value) {
            this.setStringValue(value);
        }

        @Override
        public Class<String> getValueType() {
            return String.class;
        }
    }
}
