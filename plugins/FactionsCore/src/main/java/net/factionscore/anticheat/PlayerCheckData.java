package net.factionscore.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/** Rolling per-player state consumed by every check; kept small and allocation-light since it's touched every click/move/break. */
public final class PlayerCheckData {

    private static final int MAX_SAMPLES = 64;

    public final Deque<Long> clickTimestamps = new ArrayDeque<>();
    public final Deque<double[]> aimDeltas = new ArrayDeque<>(); // {yawDelta, pitchDelta}, only while targeting a combat target
    public final Deque<Long> suspiciousOreBreaks = new ArrayDeque<>();
    public final AtomicInteger totalFlags = new AtomicInteger();
    public volatile long lastWarnAt;
    public volatile long lastCombatAt;

    public void recordClick(long now) {
        clickTimestamps.addLast(now);
        trim(clickTimestamps, MAX_SAMPLES);
    }

    public void recordAimDelta(double yawDelta, double pitchDelta) {
        aimDeltas.addLast(new double[]{yawDelta, pitchDelta});
        trim(aimDeltas, MAX_SAMPLES);
    }

    public void recordSuspiciousOreBreak(long now) {
        suspiciousOreBreaks.addLast(now);
        trim(suspiciousOreBreaks, MAX_SAMPLES);
    }

    private static <T> void trim(Deque<T> deque, int max) {
        while (deque.size() > max) {
            deque.removeFirst();
        }
    }
}
