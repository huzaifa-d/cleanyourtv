package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.Test;

public class MemoryCounterTest {
    static final class Tasks implements Executor {
        final Queue<Runnable> pending = new ArrayDeque<>();
        @Override public void execute(Runnable task) { pending.add(task); }
        void next() { pending.remove().run(); }
        void drain() { while (!pending.isEmpty()) next(); }
    }
    static final class Timer implements MemoryCounter.Scheduler {
        Runnable task;
        long delay;
        @Override public void after(long delay, Runnable task) { this.delay = delay; this.task = task; }
        @Override public void cancel() { task = null; }
        void fire() { Runnable next = task; task = null; if (next != null) next.run(); }
    }
    private final Tasks worker = new Tasks(), main = new Tasks();
    private final Timer timer = new Timer();
    private final List<MemoryReading> displayed = new ArrayList<>();
    private boolean throwRead;
    private final MemoryCounter counter = new MemoryCounter(() -> {
        if (throwRead) throw new IllegalStateException();
        return new MemoryReading(100L, 500L, 1000L);
    }, timer, worker, main, displayed::add);

    @Test public void disabledDoesNotSample() {
        counter.setActive(true);
        assertTrue(worker.pending.isEmpty());
        counter.setEnabled(true);
        assertEquals(1, worker.pending.size());
    }
    @Test public void disablingDiscardsPendingReadAndStopsScheduling() {
        counter.setEnabled(true);
        counter.setActive(true);
        assertEquals(1, worker.pending.size());
        counter.setEnabled(false);
        worker.drain();
        main.drain();
        assertTrue(displayed.isEmpty());
        assertNull(timer.task);
    }
    @Test public void resumesWithoutOverlappingOldRead() {
        counter.setEnabled(true);
        counter.setActive(true);
        counter.setActive(false);
        counter.setActive(true);
        assertEquals(1, worker.pending.size());
        worker.next();
        main.drain();
        assertTrue(displayed.isEmpty());
        assertEquals(1, worker.pending.size());
        worker.next();
        main.drain();
        assertEquals(1, displayed.size());
        assertEquals(5000, timer.delay);
        assertTrue(worker.pending.isEmpty());
        timer.fire();
        assertEquals(1, worker.pending.size());
    }
    @Test public void closeDropsLateResultsAndCancelsTimer() {
        counter.setEnabled(true);
        counter.setActive(true);
        counter.close();
        worker.drain();
        main.drain();
        counter.setActive(true);
        assertTrue(displayed.isEmpty());
        assertNull(timer.task);
        assertTrue(worker.pending.isEmpty());
    }
    @Test public void unavailableReadStillWaitsBeforeNextSample() {
        throwRead = true;
        counter.setEnabled(true);
        counter.setActive(true);
        worker.drain();
        main.drain();
        assertEquals(1, displayed.size());
        assertNull(displayed.get(0));
        assertEquals(5000, timer.delay);
    }
    @Test public void repeatedEligibilityDoesNotReadAgain() {
        counter.setEnabled(true);
        counter.setActive(true);
        worker.drain();
        main.drain();
        counter.setActive(true);
        counter.setEnabled(true);
        assertTrue(worker.pending.isEmpty());
        assertNotNull(timer.task);
    }
}
