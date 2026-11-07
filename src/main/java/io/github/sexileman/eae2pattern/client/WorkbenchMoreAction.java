package io.github.sexileman.eae2pattern.client;

/** Actual workbench menu actions, also used by layout regression checks. */
public enum WorkbenchMoreAction {
    SELECTED_ONLY, INVERT, PIN, CHECK_STORED, SKIP_SUMMARY, COLLAPSE, CLEAR_SEARCH, RESET_SETTINGS;
    public String labelKey(boolean selectedOnly,boolean pinned) {
        return switch(this) {
            case SELECTED_ONLY -> selectedOnly?"show_all":"selected";
            case INVERT -> "invert";
            case PIN -> pinned?"unpin":"pin";
            case CHECK_STORED -> "check_stored";
            case SKIP_SUMMARY -> "skip_summary";
            case COLLAPSE -> "collapse";
            case CLEAR_SEARCH -> "clear_search";
            case RESET_SETTINGS -> "reset_settings";
        };
    }
}
