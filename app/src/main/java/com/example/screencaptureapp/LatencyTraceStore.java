package com.example.screencaptureapp;

import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

final class LatencyTraceStore {
    private static final int MAX_COMPLETED_REPORTS = 20;
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ConcurrentHashMap<String, TraceRecord> ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<TraceRecord> COMPLETED = new ConcurrentLinkedQueue<>();

    private LatencyTraceStore() {
    }

    static TraceRecord start(long captureWallTimeMs, long captureElapsedMs) {
        String traceId = captureWallTimeMs + "-" + SEQUENCE.incrementAndGet();
        TraceRecord record = new TraceRecord(traceId, captureElapsedMs);
        ACTIVE.put(traceId, record);
        return record;
    }

    static void discard(TraceRecord record) {
        if (record != null) {
            ACTIVE.remove(record.traceId, record);
        }
    }

    static void discard(String traceId) {
        if (traceId != null) {
            ACTIVE.remove(traceId);
        }
    }

    static void markDisplayed(String traceId) {
        TraceRecord record = traceId == null ? null : ACTIVE.remove(traceId);
        if (record == null) {
            return;
        }
        record.displayEndMs = SystemClock.elapsedRealtime();
        if (!record.isComplete()) {
            return;
        }
        COMPLETED.offer(record);
        while (COMPLETED.size() > MAX_COMPLETED_REPORTS) {
            COMPLETED.poll();
        }
        ScreenCaptureService.blog("TRACE_COMPLETE: " + record.toLogLine());
    }

    static TraceRecord peekCompleted() {
        return COMPLETED.peek();
    }

    static void acknowledgeCompleted(String traceId) {
        TraceRecord record = COMPLETED.peek();
        if (record != null && record.traceId.equals(traceId)) {
            COMPLETED.poll();
        }
    }

    static final class TraceRecord {
        final String traceId;
        final long captureMs;
        volatile long pendingSinceMs;
        volatile long supersededFrames;
        volatile long readyMs;
        volatile long enqueueMs;
        volatile long dequeueMs;
        volatile long compressStartMs;
        volatile long compressEndMs;
        volatile long requestBodyStartMs;
        volatile long requestBodyEndMs;
        volatile long httpStartMs;
        volatile long responseEndMs;
        volatile long displayEndMs;
        volatile long serverBindMs = -1L;
        volatile long serverAuthMs = -1L;
        volatile long serverRecognizeMs = -1L;
        volatile long serverPrepareMs = -1L;
        volatile long serverDecisionMs = -1L;
        volatile long serverTotalMs = -1L;

        TraceRecord(String traceId, long captureMs) {
            this.traceId = traceId;
            this.captureMs = captureMs;
            this.pendingSinceMs = captureMs;
        }

        void inheritQueueBacklog(TraceRecord previous) {
            if (previous != null) {
                pendingSinceMs = previous.pendingSinceMs;
                supersededFrames = previous.supersededFrames + 1L;
            }
        }

        String currentStagesHeader() {
            return String.format(Locale.US, "v1,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                    captureMs, readyMs, enqueueMs, pendingSinceMs, dequeueMs, supersededFrames,
                    compressStartMs, compressEndMs, httpStartMs);
        }

        void applyServerTiming(String value) {
            long[] parsed = parseServerTiming(value);
            if (parsed == null) {
                return;
            }
            serverBindMs = parsed[0];
            serverAuthMs = parsed[1];
            serverRecognizeMs = parsed[2];
            serverPrepareMs = parsed[3];
            serverDecisionMs = parsed[4];
            serverTotalMs = parsed[5];
        }

        String completedHeader() {
            return String.format(Locale.US,
                    "v1,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                    traceId, captureMs, readyMs, enqueueMs, pendingSinceMs, dequeueMs,
                    supersededFrames, compressStartMs, compressEndMs, requestBodyStartMs, requestBodyEndMs,
                    httpStartMs, responseEndMs, displayEndMs,
                    serverBindMs, serverAuthMs, serverRecognizeMs,
                    serverPrepareMs, serverDecisionMs, serverTotalMs);
        }

        String toLogLine() {
            return String.format(Locale.US,
                    "id=%s total=%dms pending_total=%dms capture_ready=%dms queue=%dms "
                            + "queue_backlog=%dms superseded=%d compress=%dms "
                            + "body=%dms http=%dms display=%dms server_total=%dms "
                            + "server_bind=%dms server_auth=%dms server_recognize=%dms "
                            + "server_prepare=%dms server_decision=%dms",
                    traceId, displayEndMs - captureMs, displayEndMs - pendingSinceMs,
                    readyMs - captureMs, dequeueMs - enqueueMs, dequeueMs - pendingSinceMs,
                    supersededFrames, compressEndMs - compressStartMs,
                    requestBodyEndMs - requestBodyStartMs, responseEndMs - httpStartMs,
                    displayEndMs - responseEndMs, serverTotalMs, serverBindMs,
                    serverAuthMs, serverRecognizeMs, serverPrepareMs, serverDecisionMs);
        }

        private boolean isComplete() {
            return captureMs > 0L && readyMs >= captureMs && enqueueMs >= readyMs
                    && dequeueMs >= enqueueMs && compressStartMs >= dequeueMs
                    && compressEndMs >= compressStartMs && requestBodyStartMs >= httpStartMs
                    && requestBodyEndMs >= requestBodyStartMs && responseEndMs >= requestBodyEndMs
                    && displayEndMs >= responseEndMs && serverTotalMs >= 0L;
        }
    }

    static long[] parseServerTiming(String value) {
        if (value == null || value.length() > 160) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 7 || !"v1".equals(parts[0])) {
            return null;
        }
        long[] result = new long[6];
        try {
            for (int index = 0; index < result.length; index++) {
                result[index] = Long.parseLong(parts[index + 1]);
                if (result[index] < 0L || result[index] > 60000L) {
                    return null;
                }
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return result;
    }
}
