package com.example.screencaptureapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UploadPacingTest {
    @Test
    public void quickRequestWaitsForRemainingInterval() {
        assertEquals(950L, ScreenCaptureService.remainingUploadDelayMs(1000L, 1050L));
    }

    @Test
    public void slowRequestDoesNotAddDelay() {
        assertEquals(0L, ScreenCaptureService.remainingUploadDelayMs(1000L, 2200L));
    }

    @Test
    public void wallClockJumpDoesNotChangeMonotonicDelay() {
        long wallClockStartMs = 1000L;
        long wallClockEndMs = 10000L;
        long elapsedRealtimeStartMs = 5000L;
        long elapsedRealtimeEndMs = 5050L;

        assertEquals(9000L, wallClockEndMs - wallClockStartMs);
        assertEquals(950L, ScreenCaptureService.remainingUploadDelayMs(
                elapsedRealtimeStartMs, elapsedRealtimeEndMs));
    }
}
