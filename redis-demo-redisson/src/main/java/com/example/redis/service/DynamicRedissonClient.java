package com.example.redis.service;

import com.example.redis.config.RedisProperties;
import com.example.redis.config.RedissonConfig;
import com.example.redis.exception.RedisConnectionException;
import com.example.redis.model.RedisSecret;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dynamic Redisson client that supports password rotation.
 * Automatically reconnects with new credentials when authentication fails.
 */
@Slf4j
public class DynamicRedissonClient implements AutoCloseable {

    private final SecretsManagerService secretsManagerService;
    private final RedissonConfig redissonConfig;
    private final RedisProperties properties;
    private final MeterRegistry meterRegistry;

    private final AtomicReference<RedissonClient> currentClient = new AtomicReference<>();
    private final ReentrantLock clientLock = new ReentrantLock();

    // Metrics
    private final Counter reconnectCounter;
    private final Counter authFailureCounter;

    public DynamicRedissonClient(
            SecretsManagerService secretsManagerService,
            RedissonConfig redissonConfig,
            RedisProperties properties,
            MeterRegistry meterRegistry) {

        this.secretsManagerService = secretsManagerService;
        this.redissonConfig = redissonConfig;
        this.properties = properties;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.reconnectCounter = Counter.builder("redis.reconnect.count")
                .description("Number of Redis reconnections")
                .tag("type", "redisson")
                .register(meterRegistry);

        this.authFailureCounter = Counter.builder("redis.auth.failure.count")
                .description("Number of Redis authentication failures")
                .tag("type", "redisson")
                .register(meterRegistry);

        // Initialize client
        initializeClient();
    }

    /**
     * Initialize the Redisson client
     */
    private void initializeClient() {
        try {
            RedisSecret secret = secretsManagerService.getRedisSecret();
            createClient(secret);
            log.info("Successfully initialized Redisson client");
        } catch (Exception e) {
            log.error("Failed to initialize Redisson client: {}", e.getMessage());
            throw new RedisConnectionException("Failed to initialize Redisson client", e);
        }
    }

    /**
     * Get the current Redisson client
     */
    public RedissonClient getClient() {
        RedissonClient client = currentClient.get();
        if (client == null || client.isShutdown()) {
            return getOrEstablishClient();
        }
        return client;
    }

    /**
     * Get or establish client with lock
     */
    private RedissonClient getOrEstablishClient() {
        clientLock.lock();
        try {
            RedissonClient existing = currentClient.get();
            if (existing != null && !existing.isShutdown()) {
                return existing;
            }

            RedisSecret secret = secretsManagerService.getRedisSecret();
            return createClient(secret);

        } finally {
            clientLock.unlock();
        }
    }

    /**
     * Create a new Redisson client with the given secret
     */
    private RedissonClient createClient(RedisSecret secret) {
        log.info("Creating new Redisson client for host: {}", secret.getHost());

        Config config = redissonConfig.createConfigWithSecret(secret);
        RedissonClient client = Redisson.create(config);

        currentClient.set(client);
        reconnectCounter.increment();

        log.info("Redisson client created successfully");
        return client;
    }

    /**
     * Force refresh the client with new credentials
     */
    public void refreshClient() {
        clientLock.lock();
        try {
            log.info("Refreshing Redisson client with new credentials");

            // Get fresh secret
            RedisSecret newSecret = secretsManagerService.refreshSecret();

            // Close old client
            closeCurrentClient();

            // Create new client
            createClient(newSecret);

            log.info("Redisson client refreshed successfully");

        } catch (Exception e) {
            log.error("Failed to refresh Redisson client", e);
            throw new RedisConnectionException("Failed to refresh Redisson client", e);
        } finally {
            clientLock.unlock();
        }
    }

    /**
     * Execute operation with automatic retry on authentication failure
     */
    public <T> T executeWithRetry(RedissonOperation<T> operation) {
        return executeWithRetry(operation, 0);
    }

    private <T> T executeWithRetry(RedissonOperation<T> operation, int attempt) {
        try {
            return operation.execute(getClient());

        } catch (Exception e) {
            if (isAuthenticationError(e) && attempt < properties.getMaxRetryAttempts()) {
                authFailureCounter.increment();
                log.warn("Authentication failed, refreshing client and retrying (attempt {}/{})",
                        attempt + 1, properties.getMaxRetryAttempts());

                refreshClient();

                // Add delay before retry
                try {
                    Thread.sleep(100 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                return executeWithRetry(operation, attempt + 1);
            }

            log.error("Redis operation failed after {} attempts: {}", attempt + 1, e.getMessage());
            throw new RedisConnectionException("Redis operation failed", e);
        }
    }

    /**
     * Check if the error is an authentication error
     */
    private boolean isAuthenticationError(Exception e) {
        String message = e.getMessage();
        if (message == null && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        if (message == null) {
            return false;
        }
        return message.contains("NOAUTH") ||
                message.contains("Authentication") ||
                message.contains("WRONGPASS") ||
                message.contains("invalid password") ||
                message.contains("Access denied");
    }

    /**
     * Close the current client
     */
    private void closeCurrentClient() {
        RedissonClient client = currentClient.getAndSet(null);
        if (client != null && !client.isShutdown()) {
            try {
                client.shutdown();
                log.info("Previous Redisson client shutdown complete");
            } catch (Exception e) {
                log.warn("Error shutting down previous Redisson client: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if client is healthy
     */
    public boolean isHealthy() {
        try {
            RedissonClient client = currentClient.get();
            if (client == null || client.isShutdown()) {
                return false;
            }

            // Try a simple ping
            client.getBucket("health:check").set("ping", 1, TimeUnit.SECONDS);
            Object value = client.getBucket("health:check").get();
            return "ping".equals(value);

        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current secret info
     */
    public RedisSecret getCurrentSecret() {
        return secretsManagerService.getRedisSecret();
    }

    /**
     * Get secret version ID
     */
    public String getSecretVersionId() {
        return secretsManagerService.getCurrentVersionId();
    }

    @Override
    public void close() {
        log.info("Shutting down DynamicRedissonClient");
        closeCurrentClient();
    }

    /**
     * Functional interface for Redisson operations
     */
    @FunctionalInterface
    public interface RedissonOperation<T> {
        T execute(RedissonClient client) throws Exception;
    }
}
