package com.schediflow.exception;

public class ResourceNotFoundException extends SchediFlowException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, 404);
    }
}
