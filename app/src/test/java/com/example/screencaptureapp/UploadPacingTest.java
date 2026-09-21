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
    public void clockRollbackKeepsRateLimit() {
        assertEquals(1000L, ScreenCaptureService.remainingUploadDelayMs(1000L, 900L));
    }
}
