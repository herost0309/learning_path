package com.example.redis.controller;

import com.example.redis.service.CacheService;
import com.example.redis.service.RedissonTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for Redis cache operations
 */
@Slf4j
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Tag(name = "Cache API", description = "Redis cache operations with dynamic password rotation")
public class CacheController {

    private final CacheService cacheService;
    private final RedissonTemplate redissonTemplate;

    @GetMapping("/{key}")
    @Operation(summary = "Get value from cache", description = "Retrieve a value from Redis cache by key")
    public ResponseEntity<Map<String, Object>> get(
            @Parameter(description = "Cache key") @PathVariable String key) {

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", cacheService.get(key).orElse(null));
        response.put("exists", cacheService.exists(key));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{key}")
    @Operation(summary = "Set value in cache", description = "Store a value in Redis cache with optional TTL")
    public ResponseEntity<Map<String, Object>> set(
            @Parameter(description = "Cache key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        String value = body.get("value").toString();
        int ttlMinutes = body.containsKey("ttlMinutes")
                ? Integer.parseInt(body.get("ttlMinutes").toString())
                : 30;

        cacheService.set(key, value, Duration.ofMinutes(ttlMinutes));

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("ttlMinutes", ttlMinutes);
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete value from cache", description = "Remove a value from Redis cache")
    public ResponseEntity<Map<String, Object>> delete(
            @Parameter(description = "Cache key") @PathVariable String key) {

        boolean deleted = cacheService.delete(key);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("deleted", deleted);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/exists/{key}")
    @Operation(summary = "Check if key exists", description = "Check if a key exists in Redis cache")
    public ResponseEntity<Map<String, Object>> exists(
            @Parameter(description = "Cache key") @PathVariable String key) {

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("exists", cacheService.exists(key));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/increment/{key}")
    @Operation(summary = "Increment counter", description = "Increment a counter in Redis")
    public ResponseEntity<Map<String, Object>> increment(
            @Parameter(description = "Counter key") @PathVariable String key) {

        long value = cacheService.increment(key);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/lock/{key}")
    @Operation(summary = "Set value with lock", description = "Set a value using distributed lock")
    public ResponseEntity<Map<String, Object>> setWithLock(
            @Parameter(description = "Cache key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        String value = body.get("value").toString();
        int ttlMinutes = body.containsKey("ttlMinutes")
                ? Integer.parseInt(body.get("ttlMinutes").toString())
                : 30;

        cacheService.setWithLock(key, value, Duration.ofMinutes(ttlMinutes));

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("ttlMinutes", ttlMinutes);
        response.put("locked", true);
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reconnect")
    @Operation(summary = "Force reconnection", description = "Force Redis client to reconnect with fresh credentials")
    public ResponseEntity<Map<String, Object>> forceReconnect() {
        log.info("Manual reconnection triggered");

        cacheService.forceReconnect();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reconnected");
        response.put("message", "Successfully reconnected to Redis with fresh credentials");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Redis health check", description = "Check Redis connection health and secret rotation status")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        try {
            response.put("redisStatus", cacheService.isHealthy() ? "UP" : "DOWN");
            response.putAll(cacheService.getConnectionInfo());

        } catch (Exception e) {
            response.put("redisStatus", "DOWN");
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    @Operation(summary = "Get connection info", description = "Get detailed Redis connection information")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        return ResponseEntity.ok(cacheService.getConnectionInfo());
    }

    // ==================== Advanced Redisson Features ====================

    @PostMapping("/hash/{key}")
    @Operation(summary = "Set hash field", description = "Set a field in a Redis hash")
    public ResponseEntity<Map<String, Object>> hSet(
            @Parameter(description = "Hash key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        String field = body.get("field").toString();
        String value = body.get("value").toString();

        redissonTemplate.hSet(key, field, value);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("field", field);
        response.put("value", value);
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/hash/{key}/{field}")
    @Operation(summary = "Get hash field", description = "Get a field from a Redis hash")
    public ResponseEntity<Map<String, Object>> hGet(
            @Parameter(description = "Hash key") @PathVariable String key,
            @Parameter(description = "Field name") @PathVariable String field) {

        String value = redissonTemplate.hGet(key, field);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("field", field);
        response.put("value", value);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/hash/{key}")
    @Operation(summary = "Get all hash fields", description = "Get all fields from a Redis hash")
    public ResponseEntity<Map<String, Object>> hGetAll(
            @Parameter(description = "Hash key") @PathVariable String key) {

        Map<String, Object> values = redissonTemplate.hGetAll(key);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("values", values);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/list/{key}/push")
    @Operation(summary = "Push to list", description = "Push values to a Redis list (right push)")
    public ResponseEntity<Map<String, Object>> rPush(
            @Parameter(description = "List key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        String[] values = body.get("values").toString().split(",");

        redissonTemplate.rPush(key, (Object[]) values);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("count", values.length);
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/list/{key}")
    @Operation(summary = "Get list range", description = "Get elements from a Redis list")
    public ResponseEntity<Map<String, Object>> lRange(
            @Parameter(description = "List key") @PathVariable String key,
            @Parameter(description = "Start index") @RequestParam(defaultValue = "0") long start,
            @Parameter(description = "End index") @RequestParam(defaultValue = "-1") long end) {

        var values = redissonTemplate.lRange(key, start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("values", values);
        response.put("length", values.size());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/set/{key}")
    @Operation(summary = "Add to set", description = "Add members to a Redis set")
    public ResponseEntity<Map<String, Object>> sAdd(
            @Parameter(description = "Set key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        String[] members = body.get("members").toString().split(",");

        boolean added = redissonTemplate.sAdd(key, (Object[]) members);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("added", added);
        response.put("count", members.length);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/set/{key}")
    @Operation(summary = "Get set members", description = "Get all members of a Redis set")
    public ResponseEntity<Map<String, Object>> sMembers(
            @Parameter(description = "Set key") @PathVariable String key) {

        var members = redissonTemplate.sMembers(key);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("members", members);
        response.put("size", members.size());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/sortedset/{key}")
    @Operation(summary = "Add to sorted set", description = "Add a member to a Redis sorted set")
    public ResponseEntity<Map<String, Object>> zAdd(
            @Parameter(description = "Sorted set key") @PathVariable String key,
            @RequestBody Map<String, Object> body) {

        double score = Double.parseDouble(body.get("score").toString());
        String member = body.get("member").toString();

        boolean added = redissonTemplate.zAdd(key, score, member);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("score", score);
        response.put("member", member);
        response.put("added", added);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sortedset/{key}")
    @Operation(summary = "Get sorted set range", description = "Get members from a Redis sorted set")
    public ResponseEntity<Map<String, Object>> zRange(
            @Parameter(description = "Sorted set key") @PathVariable String key,
            @Parameter(description = "Start index") @RequestParam(defaultValue = "0") long start,
            @Parameter(description = "End index") @RequestParam(defaultValue = "-1") long end) {

        var members = redissonTemplate.zRange(key, start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("members", members);
        response.put("size", members.size());

        return ResponseEntity.ok(response);
    }
}
