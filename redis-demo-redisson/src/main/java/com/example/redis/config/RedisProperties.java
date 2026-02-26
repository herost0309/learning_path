package com.example.redis.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Redis configuration properties
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {

    // ==================== Secrets Manager Settings ====================

    /**
     * AWS Secrets Manager secret name or ARN
     */
    @NotBlank(message = "Secret name is required")
    private String secretName = "prod/elasticache/redis/auth";

    /**
     * Enable/disable secret caching
     */
    private boolean cacheSecret = true;

    /**
     * Secret cache TTL in seconds
     */
    @Min(value = 10, message = "Cache TTL must be at least 10 seconds")
    @Max(value = 3600, message = "Cache TTL cannot exceed 3600 seconds")
    private long secretCacheTtlSeconds = 60;

    /**
     * Connection timeout in seconds
     */
    @Min(value = 1, message = "Connection timeout must be at least 1 second")
    @Max(value = 30, message = "Connection timeout cannot exceed 30 seconds")
    private int connectionTimeoutSeconds = 5;

    /**
     * Command timeout in seconds
     */
    @Min(value = 1, message = "Command timeout must be at least 1 second")
    @Max(value = 60, message = "Command timeout cannot exceed 60 seconds")
    private int commandTimeoutSeconds = 3;

    /**
     * Enable automatic reconnection on auth failure
     */
    private boolean autoReconnect = true;

    /**
     * Maximum retry attempts for auth failures
     */
    @Min(value = 1, message = "Max retry attempts must be at least 1")
    @Max(value = 10, message = "Max retry attempts cannot exceed 10")
    private int maxRetryAttempts = 3;

    /**
     * Enable periodic secret refresh check
     */
    private boolean enableSecretRefreshCheck = true;

    /**
     * Secret refresh check interval in seconds
     */
    @Min(value = 10, message = "Refresh interval must be at least 10 seconds")
    @Max(value = 300, message = "Refresh interval cannot exceed 300 seconds")
    private long secretRefreshIntervalSeconds = 30;

    // ==================== Redisson Specific Settings ====================

    /**
     * Threads amount shared between all redis connection clients
     */
    @Min(0)
    private int threadCount = 0;  // 0 means use default (availableProcessors * 2)

    /**
     * Netty threads amount
     */
    @Min(0)
    private int nettyThreads = 0;  // 0 means use default

    /**
     * Connection pool size
     */
    @Min(1)
    private int connectionPoolSize = 64;

    /**
     * Minimum idle connection size
     */
    @Min(1)
    private int connectionMinimumIdleSize = 10;

    /**
     * Idle connection timeout in milliseconds
     */
    @Min(1000)
    private int idleConnectionTimeout = 10000;

    /**
     * Connect timeout in milliseconds
     */
    @Min(100)
    private int connectTimeout = 10000;

    /**
     * Command timeout in milliseconds
     */
    @Min(100)
    private int timeout = 3000;

    /**
     * Retry attempts
     */
    @Min(0)
    private int retryAttempts = 3;

    /**
     * Retry interval in milliseconds
     */
    @Min(100)
    private int retryInterval = 1500;

    /**
     * Ping connection interval in milliseconds
     */
    @Min(0)
    private int pingConnectionInterval = 30000;

    /**
     * Enable TCP keep alive
     */
    private boolean keepAlive = true;

    /**
     * Enable TCP no delay
     */
    private boolean tcpNoDelay = true;


}
