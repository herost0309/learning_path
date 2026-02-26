package com.example.redis.exception;

/**
 * Exception thrown when Redis connection fails
 */
public class RedisConnectionException extends RuntimeException {

    public RedisConnectionException(String message) {
        super(message);
    }

    public RedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
