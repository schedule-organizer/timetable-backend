package com.schediflow.exception;

public class BadRequestException extends SchediFlowException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", message, 400);
    }
}
