package com.example.redis.config;

import com.example.redis.model.RedisSecret;
import com.example.redis.service.DynamicRedissonClient;
import com.example.redis.service.SecretsManagerService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.config.TransportMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Redisson configuration with AWS Secrets Manager integration
 *
 * This configuration supports three Redis deployment modes:
 *
 * 1. STANDALONE - Single Redis node
 *    URL Format: rediss://host:port
 *    Example: rediss://my-redis.xxxxx.use1.cache.amazonaws.com:6379
 *
 * 2. CLUSTER - Redis Cluster mode (multiple shards)
 *    URL Format: rediss://node1:port, rediss://node2:port, ...
 *    Example: rediss://my-redis-0001.xxxxx.use1.cache.amazonaws.com:6379
 *             rediss://my-redis-0002.xxxxx.use1.cache.amazonaws.com:6379
 *             rediss://my-redis-0003.xxxxx.use1.cache.amazonaws.com:6379
 *
 * 3. SENTINEL - Redis with Sentinel for high availability
 *    URL Format: redis://sentinel1:26379, redis://sentinel2:26379, ...
 *
 * Note: "rediss://" prefix indicates TLS/SSL connection (recommended for AWS ElastiCache)
 */
@Slf4j
@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(RedisProperties.class)
@EnableScheduling
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties properties;
    private final SecretsManagerService secretsManagerService;
    private DynamicRedissonClient dynamicRedissonClient;

    /**
     * AWS Secrets Manager client bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretsManagerClient secretsManagerClient() {
        String region = System.getenv("AWS_REGION");
        if (region == null) {
            region = "us-east-1";
        }

        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Create base Redisson config from properties
     */
    public Config createBaseConfig() {
        Config config = new Config();

        // Transport mode (NIO is recommended for most cases)
        config.setTransportMode(TransportMode.NIO);

        // Thread settings - only set if > 0
        if (properties.getThreadCount() > 0) {
            config.setThreads(properties.getThreadCount());
        }
        if (properties.getNettyThreads() > 0) {
            config.setNettyThreads(properties.getNettyThreads());
        }

        // Lock watchdog timeout (for distributed locks)
        config.setLockWatchdogTimeout(30000);

        return config;
    }

    /**
     * Create Redisson config based on secret mode (standalone/cluster/sentinel)
     */
    public Config createConfigWithSecret(RedisSecret secret) {
        Config config = createBaseConfig();

        if (secret.isClusterMode()) {
            configureClusterMode(config, secret);
        } else if (secret.isSentinelMode()) {
            configureSentinelMode(config, secret);
        } else {
            configureStandaloneMode(config, secret);
        }

        return config;
    }

    /**
     * Configure Standalone (Single Server) Mode
     */
    private void configureStandaloneMode(Config config, RedisSecret secret) {
        log.info("Configuring Redis in STANDALONE mode: {}", secret.getHost());

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(secret.getRedisUrl())
                .setPassword(secret.getPassword())
                .setUsername(secret.getUsernameOrDefault())
                // Connection pool settings
                .setConnectionPoolSize(properties.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                // Timeout settings
                .setIdleConnectionTimeout(properties.getIdleConnectionTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setTimeout(properties.getTimeout())
                // Retry settings
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                // Connection health
                .setPingConnectionInterval(properties.getPingConnectionInterval())
                .setKeepAlive(properties.isKeepAlive())
                .setTcpNoDelay(properties.isTcpNoDelay())
                // SSL/TLS settings
                .setSslEnableEndpointIdentification(false)
                // Subscription settings
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(10);
    }

    /**
     * Configure Cluster Mode
     */
    private void configureClusterMode(Config config, RedisSecret secret) {
        log.info("Configuring Redis in CLUSTER mode");

        ClusterServersConfig clusterConfig = config.useClusterServers()
                .setPassword(secret.getPassword())
                .setUsername(secret.getUsernameOrDefault())
                // Connection pool settings (per node)
                .setMasterConnectionPoolSize(properties.getConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(properties.getConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                // Timeout settings
                .setIdleConnectionTimeout(properties.getIdleConnectionTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setTimeout(properties.getTimeout())
                // Retry settings
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                // Connection health
                .setPingConnectionInterval(properties.getPingConnectionInterval())
                .setKeepAlive(properties.isKeepAlive())
                .setTcpNoDelay(properties.isTcpNoDelay())
                // SSL/TLS settings
                .setSslEnableEndpointIdentification(false)
                // Cluster-specific settings
                .setScanInterval(5000);

        // Add cluster node addresses
        String[] nodeUrls = secret.getClusterNodeUrls();
        for (String nodeUrl : nodeUrls) {
            log.info("Adding cluster node: {}", nodeUrl);
            clusterConfig.addNodeAddress(nodeUrl);
        }
    }

    /**
     * Configure Sentinel Mode
     */
    private void configureSentinelMode(Config config, RedisSecret secret) {
        log.info("Configuring Redis in SENTINEL mode with master: {}", secret.getMasterName());

        var sentinelConfig = config.useSentinelServers()
                .setMasterName(secret.getMasterName())
                .setPassword(secret.getPassword())
                .setUsername(secret.getUsernameOrDefault())
                // Connection pool settings
                .setMasterConnectionPoolSize(properties.getConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(properties.getConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                // Timeout settings
                .setIdleConnectionTimeout(properties.getIdleConnectionTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setTimeout(properties.getTimeout())
                // Retry settings
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                // Connection health
                .setPingConnectionInterval(properties.getPingConnectionInterval())
                .setKeepAlive(properties.isKeepAlive())
                .setTcpNoDelay(properties.isTcpNoDelay())
                // Sentinel-specific settings
                .setScanInterval(5000);

        // Add sentinel addresses
        if (secret.getSentinelAddresses() != null) {
            for (String address : secret.getSentinelAddresses()) {
                log.info("Adding sentinel address: {}", address);
                sentinelConfig.addSentinelAddress(address);
            }
        }
    }

    /**
     * Dynamic Redisson client provider bean
     */
    @Bean
    public DynamicRedissonClient dynamicRedissonClient(MeterRegistry meterRegistry) {
        this.dynamicRedissonClient = new DynamicRedissonClient(
                secretsManagerService,
                this,
                properties,
                meterRegistry
        );
        return this.dynamicRedissonClient;
    }

    /**
     * Periodic check for secret rotation
     */
    @Scheduled(fixedDelayString = "${redis.secret-refresh-interval-seconds:30}000")
    public void checkSecretRotation() {
        if (!properties.isEnableSecretRefreshCheck()) {
            return;
        }

        if (dynamicRedissonClient == null) {
            return;
        }

        try {
            if (secretsManagerService.isNewSecretAvailable()) {
                log.info("New secret version detected, triggering Redisson client refresh");
                dynamicRedissonClient.refreshClient();
            }
        } catch (Exception e) {
            log.warn("Failed to check for secret rotation: {}", e.getMessage());
        }
    }
}
