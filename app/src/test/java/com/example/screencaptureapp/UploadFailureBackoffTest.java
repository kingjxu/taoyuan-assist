package com.example.screencaptureapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UploadFailureBackoffTest {
    @Test
    public void backoffStartsSmallAndIsBounded() {
        assertEquals(0L, ScreenCaptureService.uploadFailureBackoffMs(0));
        assertEquals(250L, ScreenCaptureService.uploadFailureBackoffMs(1));
        assertEquals(500L, ScreenCaptureService.uploadFailureBackoffMs(2));
        assertEquals(1000L, ScreenCaptureService.uploadFailureBackoffMs(3));
        assertEquals(2000L, ScreenCaptureService.uploadFailureBackoffMs(4));
        assertEquals(2000L, ScreenCaptureService.uploadFailureBackoffMs(20));
    }
}
