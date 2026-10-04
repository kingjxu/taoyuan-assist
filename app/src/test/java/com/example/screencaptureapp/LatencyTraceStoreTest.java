package com.example.screencaptureapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LatencyTraceStoreTest {
    @Test
    public void parsesCompleteServerTiming() {
        assertArrayEquals(
                new long[]{2L, 8L, 120L, 10L, 45L, 166L},
                LatencyTraceStore.parseServerTiming("v1,2,8,120,10,45,166")
        );
    }

    @Test
    public void rejectsMalformedOrPartialServerTiming() {
        assertNull(LatencyTraceStore.parseServerTiming(null));
        assertNull(LatencyTraceStore.parseServerTiming("v1,2,8,120,10,45"));
        assertNull(LatencyTraceStore.parseServerTiming("v1,2,8,-1,10,45,166"));
        assertNull(LatencyTraceStore.parseServerTiming("v2,2,8,120,10,45,166"));
    }

    @Test
    public void serializesCurrentAndCompletedTraceHeadersInProtocolOrder() {
        LatencyTraceStore.TraceRecord trace = new LatencyTraceStore.TraceRecord("1700000000000-12", 100L);
        trace.pendingSinceMs = 90L;
        trace.readyMs = 112L;
        trace.enqueueMs = 115L;
        trace.dequeueMs = 140L;
        trace.supersededFrames = 2L;
        trace.compressStartMs = 141L;
        trace.compressEndMs = 151L;
        trace.httpStartMs = 153L;
        trace.requestBodyStartMs = 154L;
        trace.requestBodyEndMs = 160L;
        trace.responseEndMs = 300L;
        trace.displayEndMs = 306L;
        trace.applyServerTiming("v1,2,8,120,10,45,166");

        assertEquals("v1,100,112,115,90,140,2,141,151,153", trace.currentStagesHeader());
        assertEquals(
                "v1,1700000000000-12,100,112,115,90,140,2,141,151,154,160,153,300,306,2,8,120,10,45,166",
                trace.completedHeader()
        );
    }

    @Test
    public void replacementFrameInheritsPendingAgeAndCountsSupersededFrames() {
        LatencyTraceStore.TraceRecord first = new LatencyTraceStore.TraceRecord("first", 100L);
        first.pendingSinceMs = 80L;
        first.supersededFrames = 3L;
        LatencyTraceStore.TraceRecord replacement = new LatencyTraceStore.TraceRecord("replacement", 120L);

        replacement.inheritQueueBacklog(first);

        assertEquals(80L, replacement.pendingSinceMs);
        assertEquals(4L, replacement.supersededFrames);
    }
}
