package com.example.redis.service;

import com.example.redis.exception.RedisConnectionException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redis template using Redisson with automatic password rotation support
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonTemplate {

    private final DynamicRedissonClient dynamicClient;
    private final MeterRegistry meterRegistry;
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(10);

    // ==================== Basic Operations ====================

    /**
     * Set a value
     */
    public void set(String key, Object value) {
        executeWithTimer("set", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            bucket.set(value);
            return null;
        });
    }

    /**
     * Set a value with TTL
     */
    public void setEx(String key, Object value, Duration ttl) {
        executeWithTimer("setEx", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            bucket.set(value, ttl.toMillis(), TimeUnit.MILLISECONDS);
            return null;
        });
    }

    /**
     * Set if not exists
     */
    public boolean setNx(String key, Object value) {
        return executeWithTimer("setNx", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.setIfAbsent(value);
        });
    }

    /**
     * Set if not exists with TTL
     */
    public boolean setNx(String key, Object value, Duration ttl) {
        return executeWithTimer("setNx", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.setIfAbsent(value, ttl);
        });
    }

    /**
     * Get a value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return executeWithTimer("get", () -> {
            RBucket<T> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.get();
        });
    }

    /**
     * Get and delete
     */
    @SuppressWarnings("unchecked")
    public <T> T getAndDelete(String key) {
        return executeWithTimer("getAndDelete", () -> {
            RBucket<T> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.getAndDelete();
        });
    }

    /**
     * Delete a key
     */
    public boolean delete(String key) {
        return executeWithTimer("delete", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.delete();
        });
    }

    /**
     * Delete multiple keys
     */
    public long delete(String... keys) {
        return executeWithTimer("deleteMulti", () -> {
            long deleted = 0;
            for (String key : keys) {
                if (dynamicClient.getClient().getBucket(key).delete()) {
                    deleted++;
                }
            }
            return deleted;
        });
    }

    /**
     * Check if key exists
     */
    public boolean exists(String key) {
        return executeWithTimer("exists", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.isExists();
        });
    }

    /**
     * Set expiration
     */
    public boolean expire(String key, Duration ttl) {
        return executeWithTimer("expire", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            return bucket.expire(ttl.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Get TTL
     */
    public long ttl(String key) {
        return executeWithTimer("ttl", () -> {
            RBucket<Object> bucket = dynamicClient.getClient().getBucket(key);
            long remainTime = bucket.remainTimeToLive();
            return remainTime > 0 ? remainTime / 1000 : remainTime;
        });
    }

    // ==================== Hash Operations ====================

    /**
     * Set hash field
     */
    public void hSet(String key, String field, Object value) {
        executeWithTimer("hSet", () -> {
            RMap<String, Object> map = dynamicClient.getClient().getMap(key);
            map.put(field, value);
            return null;
        });
    }

    /**
     * Set multiple hash fields
     */
    public void hSetAll(String key, Map<String, Object> map) {
        executeWithTimer("hSetAll", () -> {
            RMap<String, Object> rmap = dynamicClient.getClient().getMap(key);
            rmap.putAll(map);
            return null;
        });
    }

    /**
     * Get hash field
     */
    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String field) {
        return executeWithTimer("hGet", () -> {
            RMap<String, T> map = dynamicClient.getClient().getMap(key);
            return map.get(field);
        });
    }

    /**
     * Get all hash fields
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> hGetAll(String key) {
        return executeWithTimer("hGetAll", () -> {
            RMap<String, T> map = dynamicClient.getClient().getMap(key);
            return new HashMap<>(map.readAllMap());
        });
    }

    /**
     * Delete hash fields
     */
    public long hDel(String key, String... fields) {
        return executeWithTimer("hDel", () -> {
            RMap<String, Object> map = dynamicClient.getClient().getMap(key);
            long deleted = 0;
            for (String field : fields) {
                if (map.remove(field) != null) {
                    deleted++;
                }
            }
            return deleted;
        });
    }

    /**
     * Check if hash field exists
     */
    public boolean hExists(String key, String field) {
        return executeWithTimer("hExists", () -> {
            RMap<String, Object> map = dynamicClient.getClient().getMap(key);
            return map.containsKey(field);
        });
    }

    // ==================== List Operations ====================

    /**
     * Left push
     */
    public void lPush(String key, Object... values) {
        executeWithTimer("lPush", () -> {
            RList<Object> list = dynamicClient.getClient().getList(key);
            for (int i = values.length - 1; i >= 0; i--) {
                list.add(0, values[i]);
            }
            return null;
        });
    }

    /**
     * Right push
     */
    public void rPush(String key, Object... values) {
        executeWithTimer("rPush", () -> {
            RList<Object> list = dynamicClient.getClient().getList(key);
            list.addAll(Arrays.asList(values));
            return null;
        });
    }

    /**
     * Left pop
     */
    @SuppressWarnings("unchecked")
    public <T> T lPop(String key) {
        return executeWithTimer("lPop", () -> {
            RList<T> list = dynamicClient.getClient().getList(key);
            if (list.isEmpty()) {
                return null;
            }
            return list.remove(0);
        });
    }

    /**
     * Right pop
     */
    @SuppressWarnings("unchecked")
    public <T> T rPop(String key) {
        return executeWithTimer("rPop", () -> {
            RList<T> list = dynamicClient.getClient().getList(key);
            if (list.isEmpty()) {
                return null;
            }
            return list.remove(list.size() - 1);
        });
    }

    /**
     * Get list range
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> lRange(String key, long start, long end) {
        return executeWithTimer("lRange", () -> {
            RList<T> list = dynamicClient.getClient().getList(key);
            return new ArrayList<>(list.range((int) start, (int) end));
        });
    }

    /**
     * Get list length
     */
    public long lLen(String key) {
        return executeWithTimer("lLen", () -> {
            RList<Object> list = dynamicClient.getClient().getList(key);
            return list.size();
        });
    }

    // ==================== Set Operations ====================

    /**
     * Add to set
     */
    public boolean sAdd(String key, Object... members) {
        return executeWithTimer("sAdd", () -> {
            RSet<Object> set = dynamicClient.getClient().getSet(key);
            boolean added = false;
            for (Object member : members) {
                added |= set.add(member);
            }
            return added;
        });
    }

    /**
     * Get all set members
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        return executeWithTimer("sMembers", () -> {
            RSet<T> set = dynamicClient.getClient().getSet(key);
            return new HashSet<>(set.readAll());
        });
    }

    /**
     * Check if member exists in set
     */
    public boolean sIsMember(String key, Object member) {
        return executeWithTimer("sIsMember", () -> {
            RSet<Object> set = dynamicClient.getClient().getSet(key);
            return set.contains(member);
        });
    }

    /**
     * Remove from set
     */
    public boolean sRem(String key, Object... members) {
        return executeWithTimer("sRem", () -> {
            RSet<Object> set = dynamicClient.getClient().getSet(key);
            boolean removed = false;
            for (Object member : members) {
                removed |= set.remove(member);
            }
            return removed;
        });
    }

    // ==================== Sorted Set Operations ====================

    /**
     * Add to sorted set
     */
    public boolean zAdd(String key, double score, Object member) {
        return executeWithTimer("zAdd", () -> {
            RScoredSortedSet<Object> set = dynamicClient.getClient().getScoredSortedSet(key);
            return set.add(score, member);
        });
    }

    /**
     * Get sorted set range
     */
    @SuppressWarnings("unchecked")
    public <T> Collection<T> zRange(String key, long start, long end) {
        return executeWithTimer("zRange", () -> {
            RScoredSortedSet<T> set = dynamicClient.getClient().getScoredSortedSet(key);
            return set.valueRange((int) start, (int) end);
        });
    }

    /**
     * Remove from sorted set
     */
    public boolean zRem(String key, Object... members) {
        return executeWithTimer("zRem", () -> {
            RScoredSortedSet<Object> set = dynamicClient.getClient().getScoredSortedSet(key);
            return set.removeAll(Arrays.asList(members));
        });
    }

    // ==================== Distributed Lock ====================

    /**
     * Try to acquire lock
     */
    public RLock acquireLock(String lockKey, Duration waitTime, Duration leaseTime) {
        return dynamicClient.executeWithRetry(client -> {
            RLock lock = client.getLock(lockKey);
            if (lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS)) {
                return lock;
            }
            throw new RedisConnectionException("Failed to acquire lock: " + lockKey);
        });
    }

    /**
     * Release lock
     */
    public void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Execute with lock
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, LockOperation<T> operation) {
        RLock lock = null;
        try {
            lock = acquireLock(lockKey, waitTime, leaseTime);
            return operation.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisConnectionException("Lock acquisition interrupted", e);
        } catch (Exception e) {
            throw new RedisConnectionException("Operation failed", e);
        } finally {
            releaseLock(lock);
        }
    }

    // ==================== Async Operations ====================

    public <T> CompletableFuture<T> getAsync(String key) {
        return CompletableFuture.supplyAsync(() -> get(key), asyncExecutor);
    }

    public CompletableFuture<Void> setAsync(String key, Object value) {
        return CompletableFuture.runAsync(() -> set(key, value), asyncExecutor);
    }

    public CompletableFuture<Void> setExAsync(String key, Object value, Duration ttl) {
        return CompletableFuture.runAsync(() -> setEx(key, value, ttl), asyncExecutor);
    }

    // ==================== Batch Operations ====================

    /**
     * Execute batch operations
     */
    public <T> T executeBatch(BatchOperation<T> operation) {
        return dynamicClient.executeWithRetry(client -> {
            RBatch batch = client.createBatch();
            try {
                T result = operation.execute(batch);
                batch.execute();
                return result;
            } catch (Exception e) {
                throw new RedisConnectionException("Batch operation failed", e);
            }
        });
    }

    // ==================== Helper Methods ====================

    /**
     * Execute with timer metric
     */
    private <T> T executeWithTimer(String operationName, RedisOperation<T> operation) {
        long start = System.currentTimeMillis();
        try {
            T result = dynamicClient.executeWithRetry(client -> operation.execute());
            return result;
        } finally {
            long duration = System.currentTimeMillis() - start;
            Timer.builder("redis.operation.timer")
                    .tag("operation", operationName)
                    .register(meterRegistry)
                    .record(java.time.Duration.ofMillis(duration));
        }
    }

    /**
     * Force reconnection
     */
    public void forceReconnect() {
        log.info("Force reconnecting to Redis");
        dynamicClient.refreshClient();
    }

    /**
     * Check if client is healthy
     */
    public boolean isHealthy() {
        return dynamicClient.isHealthy();
    }

    /**
     * Get current secret info
     */
    public Map<String, Object> getConnectionInfo() {
        var secret = dynamicClient.getCurrentSecret();
        Map<String, Object> info = new HashMap<>();
        info.put("host", secret.getHost());
        info.put("port", secret.getPortOrDefault());
        info.put("username", secret.getUsernameOrDefault());
        info.put("lastRotated", secret.getLastRotated());
        info.put("secretVersion", dynamicClient.getSecretVersionId());
        info.put("healthy", isHealthy());
        return info;
    }

    // ==================== Functional Interfaces ====================

    @FunctionalInterface
    public interface RedisOperation<T> {
        T execute() throws Exception;
    }

    @FunctionalInterface
    public interface LockOperation<T> {
        T execute() throws Exception;
    }

    @FunctionalInterface
    public interface BatchOperation<T> {
        T execute(RBatch batch) throws Exception;
    }
}
