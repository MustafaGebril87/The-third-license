package com.thethirdlicense.exceptions;

/** Thrown when a client exceeds the allowed number of failed login attempts. */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
