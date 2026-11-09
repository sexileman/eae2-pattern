package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.State;
import io.github.sexileman.eae2pattern.cycle.CycleLimits;

import net.minecraft.nbt.CompoundTag;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Menu-bound loading state. No manual refresh: this is a plan snapshot, not live inventory. */
public final class CycleDiagramSession {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private final int menu;
    private final BiConsumer<Integer, Integer> send;
    private int request;
    private int ticks;
    private int attempts;
    private CycleDiagramSnapshot snapshot = CycleDiagramSnapshot.state(State.LOADING);

    public CycleDiagramSession(int menu, BiConsumer<Integer, Integer> send) {
        this.menu = menu;
        this.send = send;
        query();
    }

    public CycleDiagramSnapshot snapshot() {
        return snapshot;
    }

    private void query() {
        request = SEQUENCE.updateAndGet(value -> (value + 1) & Integer.MAX_VALUE);
        ticks = 0;
        attempts++;
        send.accept(menu, request);
    }

    public boolean accept(int menu, int request, CompoundTag data) {
        if (this.menu != menu || this.request != request) return false;
        try {
            snapshot = CycleDiagramSnapshot.decode(data);
        } catch (RuntimeException failure) {
            snapshot = CycleDiagramSnapshot.state(State.UNSUPPORTED);
            io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                    "[EAE2/Cycles] event=diagram_decode_failed", failure);
        }
        return true;
    }

    public boolean tick() {
        if (snapshot.state() != State.LOADING || ++ticks < CycleLimits.QUERY_INTERVAL_TICKS)
            return false;
        if (attempts < CycleLimits.QUERY_ATTEMPTS) {
            query();
            return false;
        }
        snapshot = CycleDiagramSnapshot.state(State.TIMEOUT);
        return true;
    }
}
