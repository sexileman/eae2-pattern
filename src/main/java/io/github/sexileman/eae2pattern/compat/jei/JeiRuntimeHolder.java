package io.github.sexileman.eae2pattern.compat.jei;

import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

/**
 * Holds JEI's runtime handle.
 *
 * <p>JEI hands out its runtime once, after the plugin list is finalised. Everything the mod needs from JEI
 * (recipe lookups, layout building) hangs off that handle, and it becomes null again when JEI reloads, so it is
 * kept in one place instead of being passed around.</p>
 */
public final class JeiRuntimeHolder {

    @Nullable
    private static volatile IJeiRuntime runtime;

    private JeiRuntimeHolder() {
    }

    public static void set(@Nullable IJeiRuntime newRuntime) {
        io.github.sexileman.eae2pattern.client.RecipePlan.clearSearchCaches();
        runtime = newRuntime;
    }

    @Nullable
    public static IJeiRuntime get() {
        return runtime;
    }
}
