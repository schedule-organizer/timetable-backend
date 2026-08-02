package com.schediflow.domain;

/**
 * How long the solver is allowed to search.
 *
 * <p>The story specifies timeouts per subscription tier (Starter 30s / Professional 2min /
 * Enterprise 10min), but no tier concept exists anywhere in this codebase. Mode carries those three
 * durations instead, and each is overridable via {@code app.solver.timeout-seconds.*}. Binding them
 * to a real tier is a product decision — see deferred-work.md.</p>
 */
public enum SolverMode {
    FAST(30),
    BALANCED(120),
    THOROUGH(600);

    private final int defaultTimeoutSeconds;

    SolverMode(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int defaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }
}
