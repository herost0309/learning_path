package com.example.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 连接密钥模型 - 支持 AWS Secrets Manager
 *
 * ==================== 完整配置示例 ====================
 *
 * 【1. 单机模式 - Standalone】
 * 适用场景：开发环境、单节点 Redis、Redis with Replicas
 *
 * Secret JSON:
 * {
 *   "mode": "standalone",
 *   "host": "my-redis.xxxxx.use1.cache.amazonaws.com",
 *   "port": 6379,
 *   "username": "default",
 *   "password": "your-password",
 *   "ssl": true
 * }
 *
 * 生成的 URL: rediss://my-redis.xxxxx.use1.cache.amazonaws.com:6379
 *
 *
 * 【2. 集群模式 - Cluster (方式A: 配置端点 - 推荐)】
 * 适用场景：AWS ElastiCache Cluster Mode Enabled
 * 优点：自动发现所有节点，无需手动维护节点列表
 *
 * Secret JSON:
 * {
 *   "mode": "cluster",
 *   "configurationEndpoint": "my-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com",
 *   "port": 6379,
 *   "username": "default",
 *   "password": "your-password",
 *   "ssl": true,
 *   "scanInterval": 5000
 * }
 *
 * 生成的 URL: rediss://my-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com:6379
 *
 *
 * 【3. 集群模式 - Cluster (方式B: 显式节点列表)】
 * 适用场景：自建 Redis Cluster、需要精确控制连接节点
 *
 * Secret JSON:
 * {
 *   "mode": "cluster",
 *   "nodes": [
 *     {"host": "redis-node-0001.xxxxx.use1.cache.amazonaws.com", "port": 6379},
 *     {"host": "redis-node-0002.xxxxx.use1.cache.amazonaws.com", "port": 6379},
 *     {"host": "redis-node-0003.xxxxx.use1.cache.amazonaws.com", "port": 6379},
 *     {"host": "redis-node-0004.xxxxx.use1.cache.amazonaws.com", "port": 6379},
 *     {"host": "redis-node-0005.xxxxx.use1.cache.amazonaws.com", "port": 6379},
 *     {"host": "redis-node-0006.xxxxx.use1.cache.amazonaws.com", "port": 6379}
 *   ],
 *   "username": "default",
 *   "password": "your-password",
 *   "ssl": true
 * }
 *
 * 生成的 URLs:
 *   rediss://redis-node-0001.xxxxx.use1.cache.amazonaws.com:6379
 *   rediss://redis-node-0002.xxxxx.use1.cache.amazonaws.com:6379
 *   rediss://redis-node-0003.xxxxx.use1.cache.amazonaws.com:6379
 *   ...
 *
 *
 * 【4. 哨兵模式 - Sentinel】
 * 适用场景：自建 Redis 高可用方案
 *
 * Secret JSON:
 * {
 *   "mode": "sentinel",
 *   "masterName": "mymaster",
 *   "sentinelAddresses": [
 *     "rediss://sentinel1.example.com:26379",
 *     "rediss://sentinel2.example.com:26379",
 *     "rediss://sentinel3.example.com:26379"
 *   ],
 *   "username": "default",
 *   "password": "your-password",
 *   "masterPassword": "master-password"
 * }
 *
 *
 * ==================== AWS ElastiCache URL 格式说明 ====================
 *
 * 【单节点 / Primary Endpoint】
 * 格式: rediss://<cluster-id>.xxxxx.use1.cache.amazonaws.com:6379
 * 示例: rediss://my-redis.abcd1234.use1.cache.amazonaws.com:6379
 *
 * 【集群配置端点 - Configuration Endpoint】
 * 格式: rediss://<cluster-id>.xxxxx.clustercfg.use1.cache.amazonaws.com:6379
 * 示例: rediss://my-cluster.abcd1234.clustercfg.use1.cache.amazonaws.com:6379
 * 注意: 包含 "clustercfg" 关键字
 *
 * 【集群节点端点 - Node Endpoint】
 * 格式: rediss://<cluster-id>-<shard>-<node>.xxxxx.use1.cache.amazonaws.com:6379
 * 示例: rediss://my-cluster-0001-001.abcd1234.use1.cache.amazonaws.com:6379
 *       rediss://my-cluster-0001-002.abcd1234.use1.cache.amazonaws.com:6379
 *       rediss://my-cluster-0002-001.abcd1234.use1.cache.amazonaws.com:6379
 *       ...
 *
 *
 * ==================== 协议说明 ====================
 *
 * redis://  - 非加密连接 (仅限开发环境)
 * rediss:// - TLS/SSL 加密连接 (AWS ElastiCache 必须使用)
 *
 */
@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedisSecret {

    // ==================== 连接模式 ====================

    /**
     * Redis 连接模式
     * 可选值: standalone, cluster, sentinel
     * 默认: standalone
     */
    @JsonProperty("mode")
    private String mode = "standalone";

    // ==================== 认证信息 ====================

    /**
     * 用户名
     * AWS ElastiCache 默认用户为 "default"
     */
    @JsonProperty("username")
    private String username;

    /**
     * 密码
     * AWS ElastiCache 的 AUTH token
     */
    @JsonProperty("password")
    private String password;

    // ==================== 单机模式配置 ====================

    /**
     * Redis 主机地址
     * 示例: my-redis.xxxxx.use1.cache.amazonaws.com
     */
    @JsonProperty("host")
    private String host;

    /**
     * Redis 端口
     * 默认: 6379
     */
    @JsonProperty("port")
    private Integer port;

    // ==================== 集群模式配置 ====================

    /**
     * 集群配置端点
     *
     * AWS ElastiCache 集群配置端点格式:
     * my-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com
     *
     * 特点:
     * - 包含 "clustercfg" 关键字
     * - 自动发现集群所有节点
     * - 推荐使用此方式
     */
    @JsonProperty("configurationEndpoint")
    private String configurationEndpoint;

    /**
     * 集群节点列表
     *
     * 显式指定所有集群节点，适用于:
     * - 自建 Redis Cluster
     * - 需要精确控制连接节点
     * - 跨区域部署
     */
    @JsonProperty("nodes")
    private List<ClusterNode> nodes;

    /**
     * 集群拓扑扫描间隔（毫秒）
     * 默认: 5000 (5秒)
     */
    @JsonProperty("scanInterval")
    private Long scanInterval;

    // ==================== 哨兵模式配置 ====================

    /**
     * 哨兵监控的 master 名称
     */
    @JsonProperty("masterName")
    private String masterName;

    /**
     * 哨兵节点地址列表
     * 格式: rediss://sentinel-host:26379
     */
    @JsonProperty("sentinelAddresses")
    private List<String> sentinelAddresses;

    /**
     * 哨兵密码（如果有）
     */
    @JsonProperty("sentinelPassword")
    private String sentinelPassword;

    // ==================== 通用配置 ====================

    /**
     * 是否启用 SSL/TLS
     * AWS ElastiCache 必须为 true
     */
    @JsonProperty("ssl")
    private Boolean ssl = true;

    /**
     * 数据库索引 (0-15)
     * 默认: 0
     */
    @JsonProperty("database")
    private Integer database;

    /**
     * 客户端名称
     */
    @JsonProperty("clientName")
    private String clientName;

    // ==================== 元数据 ====================

    /**
     * 集群名称 / 复制组 ID
     */
    @JsonProperty("clusterName")
    private String clusterName;

    /**
     * 最后轮换时间
     */
    @JsonProperty("lastRotated")
    private LocalDateTime lastRotated;

    // ==================== 便捷方法 ====================

    /**
     * 获取端口（带默认值）
     */
    public int getPortOrDefault() {
        return port != null ? port : 6379;
    }

    /**
     * 获取用户名（带默认值）
     */
    public String getUsernameOrDefault() {
        return (username != null && !username.isEmpty()) ? username : "default";
    }

    /**
     * 是否启用 SSL
     */
    public boolean isSslEnabled() {
        return ssl == null || ssl;
    }

    /**
     * 判断是否为集群模式
     */
    public boolean isClusterMode() {
        return "cluster".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否为哨兵模式
     */
    public boolean isSentinelMode() {
        return "sentinel".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否为单机模式
     */
    public boolean isStandaloneMode() {
        return mode == null || "standalone".equalsIgnoreCase(mode);
    }

    /**
     * 获取 URL 协议前缀
     * @return "rediss://" 或 "redis://"
     */
    public String getProtocol() {
        return isSslEnabled() ? "rediss://" : "redis://";
    }

    // ==================== URL 生成方法 ====================

    /**
     * 生成单机模式 Redis URL
     *
     * 格式: rediss://host:port
     * 示例: rediss://my-redis.xxxxx.use1.cache.amazonaws.com:6379
     *
     * @return Redis URL
     */
    public String getRedisUrl() {
        String protocol = getProtocol();

        if (host != null && !host.isEmpty()) {
            return String.format("%s%s:%d", protocol, host, getPortOrDefault());
        }

        if (configurationEndpoint != null && !configurationEndpoint.isEmpty()) {
            return String.format("%s%s:%d", protocol, configurationEndpoint, getPortOrDefault());
        }

        throw new IllegalStateException(
            "无法生成 Redis URL: 请配置 'host' 或 'configurationEndpoint'");
    }

    /**
     * 获取集群节点 URL 列表
     *
     * 方式1 - 使用配置端点（推荐）:
     *   ["rediss://my-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com:6379"]
     *   Redisson 会自动发现所有集群节点
     *
     * 方式2 - 显式节点列表:
     *   [
     *     "rediss://my-cluster-0001-001.xxxxx.use1.cache.amazonaws.com:6379",
     *     "rediss://my-cluster-0001-002.xxxxx.use1.cache.amazonaws.com:6379",
     *     "rediss://my-cluster-0002-001.xxxxx.use1.cache.amazonaws.com:6379",
     *     ...
     *   ]
     *
     * @return 集群节点 URL 数组
     */
    public String[] getClusterNodeUrls() {
        String protocol = getProtocol();

        // 优先使用显式节点列表
        if (nodes != null && !nodes.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (ClusterNode node : nodes) {
                String host = node.getHost();
                int port = node.getPort() != null ? node.getPort() : 6379;
                urls.add(String.format("%s%s:%d", protocol, host, port));
            }
            log.info("使用显式节点列表，共 {} 个节点", urls.size());
            return urls.toArray(new String[0]);
        }

        // 使用配置端点
        if (configurationEndpoint != null && !configurationEndpoint.isEmpty()) {
            String url = String.format("%s%s:%d", protocol, configurationEndpoint, getPortOrDefault());
            log.info("使用集群配置端点: {}", url);
            return new String[]{url};
        }

        throw new IllegalStateException(
            "无法生成集群节点 URLs: 请配置 'nodes' 或 'configurationEndpoint'");
    }

    /**
     * 获取哨兵节点地址列表
     *
     * 格式: rediss://sentinel-host:26379
     *
     * @return 哨兵地址列表
     */
    public List<String> getSentinelAddressList() {
        if (sentinelAddresses != null && !sentinelAddresses.isEmpty()) {
            return sentinelAddresses;
        }
        throw new IllegalStateException("请配置 'sentinelAddresses'");
    }

    /**
     * 获取集群扫描间隔（毫秒）
     */
    public long getScanIntervalOrDefault() {
        return scanInterval != null ? scanInterval : 5000;
    }

    /**
     * 获取数据库索引
     */
    public int getDatabaseOrDefault() {
        return database != null ? database : 0;
    }

    /**
     * 打印连接信息（用于日志调试）
     */
    public String getConnectionInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Redis Connection Info:\n");
        sb.append("  Mode: ").append(mode).append("\n");
        sb.append("  SSL: ").append(isSslEnabled()).append("\n");
        sb.append("  Username: ").append(getUsernameOrDefault()).append("\n");

        if (isStandaloneMode()) {
            sb.append("  URL: ").append(getRedisUrl()).append("\n");
        } else if (isClusterMode()) {
            sb.append("  Cluster Nodes:\n");
            for (String url : getClusterNodeUrls()) {
                sb.append("    - ").append(url).append("\n");
            }
        } else if (isSentinelMode()) {
            sb.append("  Master: ").append(masterName).append("\n");
            sb.append("  Sentinels:\n");
            for (String addr : getSentinelAddressList()) {
                sb.append("    - ").append(addr).append("\n");
            }
        }

        return sb.toString();
    }

    // ==================== 内部类 ====================

    /**
     * 集群节点配置
     *
     * 示例:
     * {
     *   "host": "my-cluster-0001-001.xxxxx.use1.cache.amazonaws.com",
     *   "port": 6379
     * }
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClusterNode {

        /**
         * 节点主机地址
         *
         * AWS ElastiCache 节点命名规则:
         * <cluster-id>-<shard-number>-<node-number>.xxxxx.use1.cache.amazonaws.com
         *
         * 示例:
         * my-cluster-0001-001.abcd1234.use1.cache.amazonaws.com
         *   ^           ^    ^
         *   |           |    └── 节点编号 (001=主, 002=从)
         *   |           └── 分片编号 (0001-0003)
         *   └── 集群 ID
         */
        @JsonProperty("host")
        private String host;

        /**
         * 节点端口
         * 默认: 6379
         */
        @JsonProperty("port")
        private Integer port;

        /**
         * 节点 ID (可选，用于标识)
         */
        @JsonProperty("id")
        private String id;
    }
}
