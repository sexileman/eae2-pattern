package io.github.sexileman.eae2pattern.client;

import appeng.api.stacks.GenericStack;

import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.MaterialKind;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Presentation rows reuse decoded recipe stacks; stages never duplicate item NBT. */
public final class CyclePlanViewModel {
    public enum Tab {
        PATHS("0"),
        RECIPES("1"),
        PHASES("2"),
        MATERIALS("3");
        private final String languageId;

        Tab(String languageId) {
            this.languageId = languageId;
        }

        public Component title() {
            return tr("tab." + languageId);
        }
    }

    public record Row(
            Component title,
            List<GenericStack> inputs,
            List<GenericStack> outputs,
            long times,
            boolean grid,
            long available,
            long missing,
            MaterialKind materialKind) {
        Row(
                Component title,
                List<GenericStack> inputs,
                List<GenericStack> outputs,
                long times,
                boolean grid) {
            this(title, inputs, outputs, times, grid, 0, 0, MaterialKind.NONE);
        }
    }

    private CyclePlanViewModel() {}

    private static Component tr(String key, Object... args) {
        return Component.translatable("eae2_pattern.diagram." + key, args);
    }

    public static List<Tab> tabs(CycleDiagramSnapshot snapshot) {
        return snapshot.phases().isEmpty()
                ? List.of(Tab.MATERIALS, Tab.PATHS, Tab.RECIPES)
                : List.of(Tab.MATERIALS, Tab.PATHS, Tab.RECIPES, Tab.PHASES);
    }

    public static List<Row> rows(CycleDiagramSnapshot data, Tab tab) {
        var result = new ArrayList<Row>();
        switch (tab) {
            case PATHS -> {
                if (!data.materialPath().isEmpty())
                    result.add(
                            new Row(tr("material_path"), data.materialPath(), List.of(), 1, false));
                for (int i = 0; i < data.paths().size(); i++)
                    if (!data.paths().get(i).equals(data.materialPath()))
                        result.add(
                                new Row(
                                        tr("path", i + 1),
                                        data.paths().get(i),
                                        List.of(),
                                        1,
                                        false));
                if (!data.reserved().isEmpty())
                    result.add(new Row(tr("reserved"), data.reserved(), List.of(), 1, true));
            }
            case RECIPES -> {
                for (int i = 0; i < data.recipes().size(); i++) {
                    var recipe = data.recipes().get(i);
                    result.add(
                            new Row(
                                    tr("recipe", i + 1),
                                    recipe.inputs(),
                                    recipe.outputs(),
                                    1,
                                    true));
                }
            }
            case PHASES -> {
                for (int i = 0; i < data.phases().size(); i++) {
                    var phase = data.phases().get(i);
                    var recipe = data.recipes().get(phase.recipe());
                    result.add(
                            new Row(
                                    tr("phase", i + 1, phase.recipe() + 1, phase.times()),
                                    recipe.inputs(),
                                    recipe.outputs(),
                                    phase.times(),
                                    false));
                }
            }
            case MATERIALS -> {
                result.add(
                        new Row(
                                tr("materials." + data.materialReason().id()),
                                List.of(),
                                List.of(),
                                1,
                                false));
                for (var material : data.materials())
                    result.add(
                            new Row(
                                    material.required().what().getDisplayName(),
                                    List.of(material.required()),
                                    List.of(),
                                    1,
                                    false,
                                    material.available(),
                                    material.missing(),
                                    material.kind()));
            }
        }
        return List.copyOf(result);
    }
}
