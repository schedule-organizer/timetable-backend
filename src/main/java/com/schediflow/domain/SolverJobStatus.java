package com.schediflow.domain;

/**
 * Lifecycle of a solver run. COMPLETED, FAILED and CANCELLED are terminal.
 */
public enum SolverJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
