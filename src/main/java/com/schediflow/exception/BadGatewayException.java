package com.schediflow.exception;

/**
 * Upstream or external dependency failure (e.g. holiday feed unreachable).
 */
public class BadGatewayException extends SchediFlowException {

    public BadGatewayException(String message) {
        super("BAD_GATEWAY", message, 502);
    }
}
