package io.github.sexileman.eae2pattern.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Local client preferences, independent of server encoding rules. */
public final class WorkbenchPreferences {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DROP_TOOLS;
    public static final ForgeConfigSpec.IntValue OUTPUT_POLICY, MULTIPLIER, GROUPING;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        DROP_TOOLS = b.define("exclude_non_consumed", true);
        OUTPUT_POLICY = b.defineInRange("output_policy", 0, 0, 2);
        MULTIPLIER = b.defineInRange("processing_multiplier", 1, 1, 65536);
        GROUPING = b.defineInRange("grouping", 0, 0, 3);
        SPEC = b.build();
    }
    private WorkbenchPreferences() {}
    public static void save(boolean dropTools, int outputPolicy, int multiplier, int grouping) {
        DROP_TOOLS.set(dropTools); OUTPUT_POLICY.set(outputPolicy); GROUPING.set(grouping);
        if (multiplier > 0) MULTIPLIER.set(multiplier);
        SPEC.save();
    }
}
