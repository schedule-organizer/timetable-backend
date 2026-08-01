package com.schediflow.websocket;

import org.springframework.messaging.MessagingException;

/**
 * Raised when a STOMP frame fails authentication or authorization. Spring turns this into an ERROR
 * frame and closes the session.
 */
public class MessagingAuthenticationException extends MessagingException {

    public MessagingAuthenticationException(String description) {
        super(description);
    }
}
