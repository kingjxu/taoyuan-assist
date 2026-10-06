package com.example.screencaptureapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class HeroDecisionCycleTrackerTest {
    @Test
    public void groupsVisibleFramesUntilTwoAbsentFrames() {
        AtomicInteger sequence = new AtomicInteger();
        HeroDecisionCycleTracker tracker =
                new HeroDecisionCycleTracker(() -> "cycle-" + sequence.incrementAndGet());

        HeroDecisionCycleTracker.Observation first = tracker.observe(true, 100L);
        HeroDecisionCycleTracker.Observation repeated = tracker.observe(true, 120L);
        assertEquals("cycle-1", first.cycleId);
        assertEquals(100L, first.detectedAtMs);
        assertTrue(first.startFrame);
        assertEquals(first.cycleId, repeated.cycleId);
        assertFalse(repeated.startFrame);

        assertNull(tracker.observe(false, 140L));
        HeroDecisionCycleTracker.Observation afterTransientGap = tracker.observe(true, 160L);
        assertEquals("cycle-1", afterTransientGap.cycleId);

        assertNull(tracker.observe(false, 180L));
        assertNull(tracker.observe(false, 200L));
        HeroDecisionCycleTracker.Observation next = tracker.observe(true, 220L);
        assertEquals("cycle-2", next.cycleId);
        assertEquals(220L, next.detectedAtMs);
        assertTrue(next.startFrame);
    }
}
