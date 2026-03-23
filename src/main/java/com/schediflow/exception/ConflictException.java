package com.schediflow.exception;

public class ConflictException extends SchediFlowException {

    public ConflictException(String message) {
        super("CONFLICT", message, 409);
    }
}
