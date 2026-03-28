package com.schediflow.exception;

public class UnauthorizedException extends SchediFlowException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
