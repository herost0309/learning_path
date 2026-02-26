package com.example.redis.config;

import com.example.redis.service.DynamicRedissonClient;
import com.example.redis.service.SecretsManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Redis connection with secret rotation status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final DynamicRedissonClient redissonClient;
    private final SecretsManagerService secretsManagerService;

    @Override
    public Health health() {
        try {
            if (redissonClient.isHealthy()) {
                var secret = redissonClient.getCurrentSecret();
                String versionId = redissonClient.getSecretVersionId();
                boolean newVersionAvailable = secretsManagerService.isNewSecretAvailable();

                return Health.up()
                        .withDetail("status", "UP")
                        .withDetail("connection", "healthy")
                        .withDetail("redisHost", secret.getHost())
                        .withDetail("redisPort", secret.getPortOrDefault())
                        .withDetail("redisUsername", secret.getUsernameOrDefault())
                        .withDetail("secretVersion", versionId)
                        .withDetail("lastRotated", secret.getLastRotated())
                        .withDetail("newSecretAvailable", newVersionAvailable)
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "DOWN")
                        .withDetail("error", "Health check failed")
                        .build();
            }

        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("status", "DOWN")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
