package com.example.activityservice.exception;

/** A downstream dependency is unreachable or failing. Mapped to HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
