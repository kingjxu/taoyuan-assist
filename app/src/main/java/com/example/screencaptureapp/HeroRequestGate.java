package com.example.screencaptureapp;

/**
 * Suppresses repeated uploads after a successful Hero decision while keeping failures and new
 * Hero opportunities eligible immediately.
 */
final class HeroRequestGate {
    static final long DEFAULT_SAFETY_RETRY_MS = 6_000L;

    private final long safetyRetryMs;
    private long generation;
    private boolean heroVisible;
    private long successfulGeneration = -1L;
    private long successfulAtMs;

    HeroRequestGate() {
        this(DEFAULT_SAFETY_RETRY_MS);
    }

    HeroRequestGate(long safetyRetryMs) {
        if (safetyRetryMs <= 0L) {
            throw new IllegalArgumentException("safetyRetryMs must be positive");
        }
        this.safetyRetryMs = safetyRetryMs;
    }

    synchronized Observation observe(boolean currentHeroVisible) {
        if (currentHeroVisible != heroVisible) {
            generation++;
        }
        heroVisible = currentHeroVisible;
        return new Observation(generation, currentHeroVisible);
    }

    synchronized boolean shouldUpload(Observation observation, long nowMs) {
        if (observation == null) {
            return true;
        }
        if (observation.generation != generation) {
            return false;
        }
        if (!observation.heroVisible) {
            return true;
        }
        if (successfulGeneration != observation.generation) {
            return true;
        }
        return nowMs - successfulAtMs >= safetyRetryMs;
    }

    synchronized void markSuccessful(Observation observation, long nowMs) {
        if (observation == null
                || !observation.heroVisible
                || observation.generation != generation) {
            return;
        }
        successfulGeneration = observation.generation;
        successfulAtMs = nowMs;
    }

    synchronized void markFailed(Observation observation) {
        if (observation != null
                && observation.generation == generation
                && successfulGeneration == observation.generation) {
            successfulGeneration = -1L;
            successfulAtMs = 0L;
        }
    }

    synchronized void reset() {
        generation++;
        heroVisible = false;
        successfulGeneration = -1L;
        successfulAtMs = 0L;
    }

    static final class Observation {
        final long generation;
        final boolean heroVisible;

        Observation(long generation, boolean heroVisible) {
            this.generation = generation;
            this.heroVisible = heroVisible;
        }
    }
}
