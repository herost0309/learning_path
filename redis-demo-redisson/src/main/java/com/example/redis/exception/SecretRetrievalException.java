package com.example.redis.exception;

/**
 * Exception thrown when secret retrieval fails
 */
public class SecretRetrievalException extends RuntimeException {

    public SecretRetrievalException(String message) {
        super(message);
    }

    public SecretRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
