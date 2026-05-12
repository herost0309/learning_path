package com.example.redissecret.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redis.secret")
public class RedisSecretProperties {

    /**
     * AWS Secrets Manager secret ARN or name containing Redis credentials.
     * Expected JSON structure: {"username": "...", "password": "..."}
     */
    private String secretId;

    /**
     * ElastiCache cluster nodes, comma-separated host:port pairs.
     */
    private String clusterNodes;

    /**
     * Polling interval in seconds for detecting secret rotation.
     */
    private long pollInterval = 60;

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }
}
