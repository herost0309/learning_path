package com.example.redis.service;

import com.example.redis.config.RedisProperties;
import com.example.redis.exception.SecretRetrievalException;
import com.example.redis.model.RedisSecret;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for retrieving secrets from AWS Secrets Manager
 * with caching and automatic refresh capabilities
 */
@Slf4j
@Service
public class SecretsManagerService {

    private final SecretsManagerClient secretsManagerClient;
    private final RedisProperties properties;
    private final ObjectMapper objectMapper;

    // Cached secret with version tracking
    private final AtomicReference<CachedSecret> cachedSecret = new AtomicReference<>();

    public SecretsManagerService(SecretsManagerClient secretsManagerClient, RedisProperties properties) {
        this.secretsManagerClient = secretsManagerClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Initialize and cache secret on startup
     */
    @PostConstruct
    public void init() {
        log.info("Initializing SecretsManagerService with secret: {}", properties.getSecretName());
        try {
            getRedisSecret();
            log.info("Successfully initialized Redis secret cache");
        } catch (Exception e) {
            log.warn("Failed to initialize Redis secret cache: {}", e.getMessage());
        }
    }

    /**
     * Get Redis secret from Secrets Manager
     */
    public RedisSecret getRedisSecret() {
        return getRedisSecret(false);
    }

    /**
     * Get Redis secret with optional cache bypass
     *
     * @param bypassCache If true, always fetch from Secrets Manager
     */
    public RedisSecret getRedisSecret(boolean bypassCache) {
        if (!bypassCache && properties.isCacheSecret()) {
            CachedSecret cached = cachedSecret.get();
            if (cached != null && !cached.isExpired(properties.getSecretCacheTtlSeconds() * 1000)) {
                log.debug("Returning cached secret (version: {})", cached.getVersionId());
                return cached.getSecret();
            }
        }

        return fetchAndCacheSecret();
    }

    /**
     * Force refresh the cached secret
     */
    public RedisSecret refreshSecret() {
        log.info("Force refreshing Redis secret");
        return fetchAndCacheSecret();
    }

    /**
     * Fetch secret from AWS Secrets Manager and cache it
     */
    private RedisSecret fetchAndCacheSecret() {
        try {
            log.debug("Fetching secret from Secrets Manager: {}", properties.getSecretName());

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(properties.getSecretName())
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);

            String secretString = response.secretString();
            if (secretString == null && response.secretBinary() != null) {
                secretString = new String(response.secretBinary().asByteArray());
            }

            RedisSecret secret = objectMapper.readValue(secretString, RedisSecret.class);

            // Update last retrieved time
            if (secret.getLastRotated() == null) {
                secret.setLastRotated(LocalDateTime.now());
            }

            // Update cache
            cachedSecret.set(new CachedSecret(secret, response.versionId()));

            log.info("Successfully fetched and cached Redis secret (version: {}, host: {})",
                    response.versionId(), secret.getHost());

            return secret;

        } catch (Exception e) {
            log.error("Failed to retrieve Redis secret from Secrets Manager", e);
            throw new SecretRetrievalException("Failed to retrieve Redis secret", e);
        }
    }

    /**
     * Check if a new secret version is available
     */
    public boolean isNewSecretAvailable() {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(properties.getSecretName())
                    .versionStage("AWSCURRENT")
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);

            CachedSecret cached = cachedSecret.get();
            if (cached == null) {
                return true;
            }

            boolean isNew = !response.versionId().equals(cached.getVersionId());
            if (isNew) {
                log.info("New secret version detected: {} -> {}",
                        cached.getVersionId(), response.versionId());
            }
            return isNew;

        } catch (Exception e) {
            log.warn("Failed to check for new secret version: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current cached secret version ID
     */
    public String getCurrentVersionId() {
        CachedSecret cached = cachedSecret.get();
        return cached != null ? cached.getVersionId() : null;
    }

    /**
     * Inner class to hold cached secret with metadata
     */
    private static class CachedSecret {
        private final RedisSecret secret;
        private final String versionId;
        private final long cachedAt;

        public CachedSecret(RedisSecret secret, String versionId) {
            this.secret = secret;
            this.versionId = versionId;
            this.cachedAt = System.currentTimeMillis();
        }

        public RedisSecret getSecret() {
            return secret;
        }

        public String getVersionId() {
            return versionId;
        }

        public boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - cachedAt > ttlMs;
        }
    }
}
