package io.github.sexileman.eae2pattern.cycle;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

/** Bounded passes resume after busy providers instead of starving the tail of a stable iterable. */
public final class ProviderCursor<T> {
    private Iterable<T> source;
    private Iterator<T> cursor;

    public List<T> next(Iterable<T> providers, int budget) {
        if (budget < 1) throw new IllegalArgumentException("Provider budget must be positive");
        if (source != providers || cursor == null) {
            source = providers;
            cursor = providers.iterator();
        }
        var result = new ArrayList<T>();
        try {
            while (result.size() < budget && cursor.hasNext()) result.add(cursor.next());
            if (!cursor.hasNext()) clear();
        } catch (ConcurrentModificationException changed) {
            // Native providers can change between CPU ticks. Restart against the current view next
            // time.
            clear();
        }
        return result;
    }

    /**
     * Release network references while a successful batch waits for machines to return its outputs.
     */
    public void clear() {
        source = null;
        cursor = null;
    }
}
