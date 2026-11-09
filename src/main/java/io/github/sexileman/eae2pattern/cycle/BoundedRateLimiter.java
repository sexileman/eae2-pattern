package io.github.sexileman.eae2pattern.cycle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-threaded request limiter. nanoTime values only have meaning relative to a prior sample.
 */
public final class BoundedRateLimiter<K> {
    private final long interval;
    private final int capacity;
    private final Map<K, Long> last = new LinkedHashMap<>();

    public BoundedRateLimiter(long interval, int capacity) {
        if (interval < 0 || capacity < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.interval = interval;
        this.capacity = capacity;
    }

    public boolean acquire(K key, long now) {
        Long previous = last.get(key);
        if (previous != null && now - previous < interval) return false;
        last.remove(key);
        last.put(key, now);
        while (last.size() > capacity) last.remove(last.keySet().iterator().next());
        return true;
    }

    public void remove(K key) {
        last.remove(key);
    }

    public void clear() {
        last.clear();
    }
}
