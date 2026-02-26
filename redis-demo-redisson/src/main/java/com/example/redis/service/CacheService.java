package com.example.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache service demonstrating Redis usage with automatic password rotation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedissonTemplate redissonTemplate;

    private static final String CACHE_PREFIX = "cache:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * Get value from cache
     */
    public Optional<String> get(String key) {
        try {
            String value = redissonTemplate.get(CACHE_PREFIX + key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.error("Failed to get value from cache for key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Set value in cache with default TTL
     */
    public void set(String key, String value) {
        set(key, value, DEFAULT_TTL);
    }

    /**
     * Set value in cache with custom TTL
     */
    public void set(String key, String value, Duration ttl) {
        try {
            redissonTemplate.setEx(CACHE_PREFIX + key, value, ttl);
            log.debug("Cached value for key: {} with TTL: {}", key, ttl);
        } catch (Exception e) {
            log.error("Failed to set value in cache for key: {}", key, e);
            throw new CacheException("Failed to set cache value", e);
        }
    }

    /**
     * Delete value from cache
     */
    public boolean delete(String key) {
        try {
            return redissonTemplate.delete(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.error("Failed to delete value from cache for key: {}", key, e);
            return false;
        }
    }

    /**
     * Check if key exists in cache
     */
    public boolean exists(String key) {
        try {
            return redissonTemplate.exists(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.error("Failed to check existence in cache for key: {}", key, e);
            return false;
        }
    }

    /**
     * Get or compute pattern
     */
    public String getOrCompute(String key, Duration ttl, CacheLoader loader) {
        Optional<String> cached = get(key);

        if (cached.isPresent()) {
            log.debug("Cache hit for key: {}", key);
            return cached.get();
        }

        log.debug("Cache miss for key: {}, computing value", key);
        String value = loader.load();
        set(key, value, ttl);
        return value;
    }

    /**
     * Set with distributed lock
     */
    public void setWithLock(String key, String value, Duration ttl) {
        String lockKey = "lock:" + key;
        redissonTemplate.executeWithLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            set(key, value, ttl);
            return null;
        });
    }

    /**
     * Increment counter
     */
    public long increment(String key) {
        String counterKey = CACHE_PREFIX + "counter:" + key;
        try {
            String value = redissonTemplate.get(counterKey);
            long newValue = (value == null) ? 1 : Long.parseLong(value) + 1;
            redissonTemplate.setEx(counterKey, String.valueOf(newValue), DEFAULT_TTL);
            return newValue;
        } catch (Exception e) {
            log.error("Failed to increment counter for key: {}", key, e);
            throw new CacheException("Failed to increment counter", e);
        }
    }

    /**
     * Force reconnection
     */
    public void forceReconnect() {
        log.info("Force reconnecting to Redis");
        redissonTemplate.forceReconnect();
    }

    /**
     * Check health
     */
    public boolean isHealthy() {
        return redissonTemplate.isHealthy();
    }

    /**
     * Get connection info
     */
    public java.util.Map<String, Object> getConnectionInfo() {
        return redissonTemplate.getConnectionInfo();
    }

    /**
     * Functional interface for cache loader
     */
    @FunctionalInterface
    public interface CacheLoader {
        String load();
    }

    /**
     * Cache exception
     */
    public static class CacheException extends RuntimeException {
        public CacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
