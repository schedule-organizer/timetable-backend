package com.schediflow.dto.event;

import java.util.List;

/**
 * Sent to the personal queues of both the requesting and the target teacher when a delegation
 * request changes state (COVER-03, COVER-04).
 *
 * @param event discriminator; {@code type} carries the delegation type (SWAP / HANDOVER)
 */
public record DelegationUpdateEvent(
        String event,
        Long requestId,
        String type,
        String status,
        List<Long> lessonIds
) {

    public static final String TYPE = "DELEGATION_UPDATE";

    public DelegationUpdateEvent(Long requestId, String type, String status, List<Long> lessonIds) {
        this(TYPE, requestId, type, status, lessonIds);
    }
}
