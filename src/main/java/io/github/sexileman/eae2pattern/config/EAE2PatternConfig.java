package io.github.sexileman.eae2pattern.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server-side configuration. All generation happens on the server, so the rules that decide what ends up in a
 * pattern must live there too; otherwise a modified client could bypass the filters.
 */
public final class EAE2PatternConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_STACKS;
    public static final ForgeConfigSpec.BooleanValue STRIP_CONTAINER_ITEMS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SUBSTITUTES;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FLUID_SUBSTITUTES;
    public static final ForgeConfigSpec.IntValue MAX_PATTERNS_PER_REQUEST;
    public static final ForgeConfigSpec.IntValue MAX_REQUEST_BYTES;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ENCODING_TERMINAL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Which ingredients must never appear inside a generated pattern.",
                "Entries are item/fluid ids. A trailing * acts as a wildcard, e.g. \"minecraft:*\" or \"mekanism:*_dust\".",
                "GT non-consumed ingredients are controlled by the encoding screen."
        ).push("filters");
        IGNORED_STACKS = builder
                .comment("Exact ids or wildcard patterns to drop from every pattern input.")
                .defineListAllowEmpty("ignored_stacks", List.of(), o -> o instanceof String);
        STRIP_CONTAINER_ITEMS = builder
                .comment(
                        "Legacy option retained for configuration compatibility. No longer infers processing",
                        "consumption from crafting remainders. Filled containers stay valid inputs; native",
                        "AE2 crafting patterns retain their configured fluid substitution behavior."
                )
                .define("strip_container_items", true);
        builder.pop();

        builder.comment("How encoded patterns are produced.").push("encoding");
        ALLOW_SUBSTITUTES = builder
                .comment("Allow AE2 to accept equivalent items (same item, different NBT/recipe remainder) in crafting patterns.")
                .define("allow_substitutes", true);
        ALLOW_FLUID_SUBSTITUTES = builder
                .comment("Allow AE2 to accept fluid substitutes in crafting patterns.")
                .define("allow_fluid_substitutes", false);
        REQUIRE_ENCODING_TERMINAL = builder
                .comment(
                        "Only allow generation while the player is linked to an AE2 network through a wireless",
                        "terminal or is standing next to a connected AE2 block."
                )
                .define("require_network_source", true);
        builder.pop();

        builder.comment("Safety limits, mostly to keep a mis-click from encoding ten thousand patterns.").push("limits");
        MAX_PATTERNS_PER_REQUEST = builder
                .comment(
                        "Upper bound on patterns produced by a single request.",
                        "Machines in large packs are not small: a single GTCEu bender exposes ~800 recipes, so the",
                        "default has to be generous. Raise it further if your pack has bigger machines, or lower it",
                        "if you would rather be protected from mis-clicks."
                )
                .defineInRange("max_patterns_per_request", 8192, 1, 65536);
        MAX_REQUEST_BYTES = builder
                .comment("Upper bound on the serialized size of one request packet batch, in bytes.")
                .defineInRange("max_request_bytes", 1_000_000, 8192, 8_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private EAE2PatternConfig() {
    }
}
