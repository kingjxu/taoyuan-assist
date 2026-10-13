package com.example.screencaptureapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeroRequestGateTest {
    @Test
    public void suppressesRepeatedHeroFramesAfterSuccess() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation first = gate.observe(true);

        assertTrue(gate.shouldUpload(first, 100L));
        gate.markSuccessful(first, 200L);

        HeroRequestGate.Observation repeated = gate.observe(true);
        assertFalse(gate.shouldUpload(repeated, 5_000L));
    }

    @Test
    public void oneNonHeroFrameRearmsTheNextHeroOpportunity() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation first = gate.observe(true);
        gate.markSuccessful(first, 200L);

        assertTrue(gate.shouldUpload(gate.observe(false), 300L));
        HeroRequestGate.Observation next = gate.observe(true);

        assertTrue(gate.shouldUpload(next, 400L));
    }

    @Test
    public void failedSafetyProbeAllowsImmediateRetry() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation first = gate.observe(true);
        gate.markSuccessful(first, 200L);
        HeroRequestGate.Observation safetyProbe = gate.observe(true);

        assertTrue(gate.shouldUpload(safetyProbe, 6_200L));
        gate.markFailed(safetyProbe);

        assertTrue(gate.shouldUpload(gate.observe(true), 6_201L));
    }

    @Test
    public void staleSuccessDoesNotSuppressANewerOpportunity() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation old = gate.observe(true);
        gate.observe(false);
        HeroRequestGate.Observation current = gate.observe(true);

        gate.markSuccessful(old, 200L);

        assertFalse(gate.shouldUpload(old, 300L));
        assertTrue(gate.shouldUpload(current, 300L));
    }

    @Test
    public void staleSuccessCannotOverwriteTheCurrentSuccessfulGeneration() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation old = gate.observe(true);
        gate.observe(false);
        HeroRequestGate.Observation current = gate.observe(true);
        gate.markSuccessful(current, 300L);

        gate.markSuccessful(old, 400L);

        assertFalse(gate.shouldUpload(gate.observe(true), 6_299L));
        assertTrue(gate.shouldUpload(gate.observe(true), 6_300L));
    }

    @Test
    public void staleFailureCannotClearTheCurrentSuccessfulGeneration() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation old = gate.observe(true);
        gate.observe(false);
        HeroRequestGate.Observation current = gate.observe(true);
        gate.markSuccessful(current, 300L);

        gate.markFailed(old);

        assertFalse(gate.shouldUpload(gate.observe(true), 6_299L));
    }

    @Test
    public void dropsAnOldQueuedHeroFrameAfterANonHeroFrameWasObserved() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation oldHero = gate.observe(true);

        HeroRequestGate.Observation currentNonHero = gate.observe(false);

        assertFalse(gate.shouldUpload(oldHero, 300L));
        assertTrue(gate.shouldUpload(currentNonHero, 300L));
    }

    @Test
    public void safetyRetryIsAllowedAfterTimeout() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation first = gate.observe(true);
        gate.markSuccessful(first, 200L);

        assertFalse(gate.shouldUpload(gate.observe(true), 6_199L));
        assertTrue(gate.shouldUpload(gate.observe(true), 6_200L));
    }

    @Test
    public void resetMakesTheNextHeroFrameEligible() {
        HeroRequestGate gate = new HeroRequestGate(6_000L);
        HeroRequestGate.Observation first = gate.observe(true);
        gate.markSuccessful(first, 200L);

        gate.reset();

        assertTrue(gate.shouldUpload(gate.observe(true), 300L));
    }
}
