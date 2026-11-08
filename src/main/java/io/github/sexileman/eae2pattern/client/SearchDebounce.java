package io.github.sexileman.eae2pattern.client;

/** Trailing debounce measured with a monotonic clock, independent of tick rate. */
public final class SearchDebounce {
    public static final long DELAY_NANOS = 200_000_000L;
    private long changedAt;
    private boolean pending;
    public void changed() { changed(System.nanoTime()); }
    public void changed(long now) { changedAt = now; pending = true; }
    public boolean pending() { return pending; }
    public boolean poll() { return poll(System.nanoTime()); }
    public boolean poll(long now) {
        if (!pending || now - changedAt < DELAY_NANOS) return false;
        pending = false;
        return true;
    }
}
