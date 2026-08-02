package com.schediflow.domain;

/**
 * What a delegation request does to the lessons it names.
 */
public enum DelegationType {
    /** Exchange lessons between the requester and the target teacher. */
    SWAP,
    /** Transfer the lessons to the target teacher outright. */
    HANDOVER
}
