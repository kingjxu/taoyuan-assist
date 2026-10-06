package com.example.screencaptureapp;

import java.util.UUID;
import java.util.function.Supplier;

/** Groups consecutive Hero action frames without changing capture or upload behavior. */
final class HeroDecisionCycleTracker {
    private static final int ABSENT_FRAMES_TO_END = 2;

    private final Supplier<String> idSupplier;
    private String activeCycleId;
    private long detectedAtMs;
    private int absentFrames;

    HeroDecisionCycleTracker() {
        this(() -> UUID.randomUUID().toString());
    }

    HeroDecisionCycleTracker(Supplier<String> idSupplier) {
        this.idSupplier = idSupplier;
    }

    synchronized Observation observe(boolean heroTurnVisible, long captureElapsedMs) {
        if (heroTurnVisible) {
            absentFrames = 0;
            boolean startsCycle = activeCycleId == null;
            if (startsCycle) {
                activeCycleId = idSupplier.get();
                detectedAtMs = captureElapsedMs;
            }
            return new Observation(activeCycleId, detectedAtMs, startsCycle);
        }
        if (activeCycleId != null && ++absentFrames >= ABSENT_FRAMES_TO_END) {
            reset();
        }
        return null;
    }

    synchronized void reset() {
        activeCycleId = null;
        detectedAtMs = 0L;
        absentFrames = 0;
    }

    static final class Observation {
        final String cycleId;
        final long detectedAtMs;
        final boolean startFrame;

        Observation(String cycleId, long detectedAtMs, boolean startFrame) {
            this.cycleId = cycleId;
            this.detectedAtMs = detectedAtMs;
            this.startFrame = startFrame;
        }
    }
}
