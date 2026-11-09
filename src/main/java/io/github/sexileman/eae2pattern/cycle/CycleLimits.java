package io.github.sexileman.eae2pattern.cycle;

/** Shared safety contracts. Changing these limits must preserve save and wire compatibility. */
public final class CycleLimits {
    public static final int CAPTURE_DEPTH = 64;
    public static final int CAPTURE_REJECTIONS = 64;
    public static final int LOOP_RECIPES = 2;
    public static final int MAX_RECIPES = 3;
    public static final int MAX_SLOTS = 64;
    public static final int MAX_STAGES = 512;
    public static final long MAX_OPERATIONS = 1_000_000;
    public static final int PROVIDERS_PER_PASS = 256;
    public static final int INFO_BATCHES = 16;
    public static final int DIAGRAM_BYTES = 262144;
    public static final int DIAGRAM_PATHS = 8;
    public static final int DIAGRAM_PATH_NODES = 9;
    public static final int CANDIDATE_RECIPES = 4;
    public static final int DIAGRAM_RECIPES = DIAGRAM_PATHS * CANDIDATE_RECIPES;
    public static final int MATERIAL_TYPES = MAX_RECIPES * MAX_SLOTS;
    public static final int QUERY_INTERVAL_TICKS = 20;
    public static final int QUERY_ATTEMPTS = 15;
    public static final long QUERY_COOLDOWN_NANOS = 500_000_000L;
    public static final int QUERY_PLAYERS = 128;
    public static final int LOG_KEY_CHARACTERS = 512;
    public static final int LOG_STACKS = 16;

    private CycleLimits() {}
}
