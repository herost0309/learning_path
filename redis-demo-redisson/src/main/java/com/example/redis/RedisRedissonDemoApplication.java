package com.example.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AWS ElastiCache Redis with Secrets Manager Demo Application (Redisson)
 *
 * Features:
 * - Dynamic password rotation support
 * - Automatic reconnection on authentication failure
 * - Secret caching with TTL
 * - Redisson advanced features (distributed locks, collections, etc.)
 */
@SpringBootApplication
@EnableScheduling
public class RedisRedissonDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisRedissonDemoApplication.class, args);
    }
}
