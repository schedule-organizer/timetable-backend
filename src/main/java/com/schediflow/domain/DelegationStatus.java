package com.schediflow.domain;

/**
 * Lifecycle of a delegation request. APPROVED and REJECTED are terminal.
 */
public enum DelegationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
