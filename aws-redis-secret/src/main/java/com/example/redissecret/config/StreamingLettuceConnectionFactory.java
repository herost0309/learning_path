package com.example.redissecret.config;

import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.lettuce.LettuceClusterConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionProvider;
import org.springframework.lang.Nullable;

/**
 * Custom {@link RedisConnectionFactory} that uses a pre-configured Lettuce
 * {@link RedisClusterClient} with streaming credentials provider support.
 *
 * <p>This factory is necessary because Spring Data Redis's {@code LettuceConnectionFactory}
 * does not expose a way to set a Lettuce native streaming {@code RedisCredentialsProvider}
 * on the underlying client. By managing the {@link RedisClusterClient} ourselves,
 * we can set the streaming provider on the {@code RedisURI} objects before the client
 * is created.</p>
 *
 * <p>Uses a shared connection that is reused across operations. When credentials
 * rotate, Lettuce's {@code ON_NEW_CREDENTIALS} re-auth behavior sends AUTH on
 * the existing connection without disconnecting.</p>
 */
public class StreamingLettuceConnectionFactory implements RedisConnectionFactory, SmartLifecycle {

    private final RedisClusterClient clusterClient;
    private final SharedConnectionProvider sharedConnectionProvider;
    private volatile boolean running = false;

    public StreamingLettuceConnectionFactory(RedisClusterClient clusterClient) {
        this.clusterClient = clusterClient;
        this.sharedConnectionProvider = new SharedConnectionProvider(clusterClient);
    }

    @Override
    public void start() {
        if (!running) {
            sharedConnectionProvider.init();
            running = true;
        }
    }

    @Override
    public void stop() {
        if (running) {
            sharedConnectionProvider.close();
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return true;
    }

    @Override
    public RedisConnection getConnection() {
        return getClusterConnection();
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        ensureRunning();
        return new LettuceClusterConnection(sharedConnectionProvider);
    }

    @Override
    @Nullable
    public RedisSentinelConnection getSentinelConnection() {
        throw new UnsupportedOperationException("Sentinel connections are not supported");
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return new RedisSystemException(ex.getMessage(), ex);
    }

    /**
     * Destroy the factory, closing the shared connection and shutting down the client.
     */
    public void destroy() {
        stop();
        clusterClient.shutdown();
    }

    private void ensureRunning() {
        if (!running) {
            start();
        }
    }

    /**
     * Connection provider that maintains a single shared {@link StatefulRedisClusterConnection}.
     * Returns the shared connection on every {@link #getConnection} call.
     * {@link #release} is a no-op to prevent closing the shared connection.
     */
    static class SharedConnectionProvider implements LettuceConnectionProvider {

        private final RedisClusterClient clusterClient;
        private volatile StatefulRedisClusterConnection<byte[], byte[]> connection;

        SharedConnectionProvider(RedisClusterClient clusterClient) {
            this.clusterClient = clusterClient;
        }

        void init() {
            if (connection == null || !connection.isOpen()) {
                connection = clusterClient.connect(ByteArrayCodec.INSTANCE);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends io.lettuce.core.api.StatefulConnection<?, ?>> T getConnection(
                Class<T> connectionType) {
            if (connection == null || !connection.isOpen()) {
                init();
            }
            return (T) connection;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends io.lettuce.core.api.StatefulConnection<?, ?>> java.util.concurrent.CompletionStage<T> getConnectionAsync(
                Class<T> connectionType) {
            return java.util.concurrent.CompletableFuture.completedFuture(getConnection(connectionType));
        }

        @Override
        public void release(io.lettuce.core.api.StatefulConnection<?, ?> connection) {
            // No-op: the shared connection must stay alive for streaming re-auth.
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.lang.Void> releaseAsync(
                io.lettuce.core.api.StatefulConnection<?, ?> connection) {
            // No-op: the shared connection must stay alive for streaming re-auth.
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        public void close() {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }
}
