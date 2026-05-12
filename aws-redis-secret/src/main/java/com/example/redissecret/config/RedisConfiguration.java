package com.example.redissecret.config;

import com.example.redissecret.credentials.SecretRotationWatcher;
import com.example.redissecret.credentials.SecretsManagerStreamingCredentialsProvider;
import com.example.redissecret.properties.RedisSecretProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Redis configuration that assembles all components for streaming credential rotation.
 *
 * <p>Creates a Lettuce {@link RedisClusterClient} with {@link RedisURI} objects that
 * have the streaming {@link SecretsManagerStreamingCredentialsProvider} set. This
 * enables Lettuce 6.6's native streaming re-authentication: when credentials rotate,
 * AUTH is sent on active connections without disconnecting.</p>
 *
 * <p>Uses {@link StreamingLettuceConnectionFactory} instead of Spring Data Redis's
 * {@code LettuceConnectionFactory} because the latter does not expose a way to set
 * the Lettuce native streaming credentials provider.</p>
 */
@Configuration
@EnableConfigurationProperties(RedisSecretProperties.class)
public class RedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

    @Bean
    public SecretsManagerStreamingCredentialsProvider credentialsProvider(
            software.amazon.awssdk.services.secretsmanager.SecretsManagerClient smClient,
            RedisSecretProperties props) {
        SecretsManagerStreamingCredentialsProvider provider =
                new SecretsManagerStreamingCredentialsProvider(props.getSecretId(), smClient);
        provider.init();
        log.info("StreamingCredentialsProvider initialized for secretId={}", props.getSecretId());
        return provider;
    }

    @Bean
    public SecretRotationWatcher secretRotationWatcher(
            SecretsManagerStreamingCredentialsProvider credentialsProvider) {
        return new SecretRotationWatcher(credentialsProvider);
    }

    /**
     * Creates Lettuce {@link RedisClusterClient} with streaming credentials provider
     * set on each seed {@link RedisURI}. The credentials provider is the mechanism
     * by which Lettuce 6.6+ receives credential updates and triggers re-authentication.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClusterClient redisClusterClient(
            SecretsManagerStreamingCredentialsProvider provider,
            RedisSecretProperties props) {

        // Build RedisURI list with streaming credentials provider
        List<RedisURI> seedUris = Arrays.stream(props.getClusterNodes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(node -> {
                    String[] parts = node.split(":");
                    String host = parts[0].trim();
                    int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 6379;

                    RedisURI uri = RedisURI.create(host, port);
                    // Set the streaming credentials provider on the URI.
                    // Lettuce will subscribe to the Flux and re-auth when new
                    // credentials are emitted.
                    uri.setCredentialsProvider(provider);
                    log.info("Configured seed node: {}:{} with streaming credentials provider", host, port);
                    return uri;
                })
                .toList();

        RedisClusterClient clusterClient = RedisClusterClient.create(seedUris);

        // Configure client options with streaming re-auth behavior
        clusterClient.setOptions(ClusterClientOptions.builder()
                .reauthenticateBehavior(ClientOptions.ReauthenticateBehavior.ON_NEW_CREDENTIALS)
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.ACCEPT_COMMANDS)
                .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
                        .enableAllAdaptiveRefreshTriggers()
                        .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
                        .build())
                .build());

        log.info("RedisClusterClient created with ON_NEW_CREDENTIALS re-auth behavior");
        return clusterClient;
    }

    @Bean(destroyMethod = "destroy")
    public StreamingLettuceConnectionFactory redisConnectionFactory(
            RedisClusterClient clusterClient) {
        return new StreamingLettuceConnectionFactory(clusterClient);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
