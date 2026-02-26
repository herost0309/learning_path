package com.example.awssqs.config;


import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS SQS配置类
 */
@Configuration
public class SqsConfiguration {

    /**
     * 创建SqsAsyncClient (AWS SDK v2)
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .build();
    }

    /**
     * 创建SqsTemplate (Spring Cloud AWS 3.x)
     * 用于发送和接收SQS消息
     */
    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }

    /**
     * SQS配置属性
     */
    @Configuration
    @ConfigurationProperties(prefix = "aws.sqs")
    @Data
    public static class SqsProperties {

        /**
         * SQS队列配置映射
         * key: 队列名称
         * name: 队列配置对象
         */
        private Map<String, QueueConfig> queues = new HashMap<>();

        /**
         * 生产者默认配置
         */
        private ProducerConfig producer = new ProducerConfig();

        /**
         * 消费者默认配置
         */
        private ConsumerConfig consumer = new ConsumerConfig();

        /**
         * 队列配置类
         */
        @Data
        public static class QueueConfig {
            private String name;
            private String type = "STANDARD"; // STANDARD or FIFO
            private Integer retentionPeriod = 1209600; // 14天，以秒为单位
            private String dlqName;
            private Integer dlqMaxReceiveCount = 3;
            private Boolean enableMessageDeduplication = false;
            private Boolean enableContentBasedDeduplication = false;
            private Integer delaySeconds = 0;
            private Integer maxReceiveCount = 10;

            // 队列URL（自动生成或手动配置）
            private String queueUrl;
        }

        /**
         * 生产者配置
         */
        @Data
        public static class ProducerConfig {
            private Boolean autoCreate = true;
            private Integer delaySeconds = 0;
            private Integer messageVisibilityTimeout = 30; // 消息可见性超时（秒）
            private Boolean messageDeduplicationId = true; // 启用消息去重
            private Boolean messageIdEnabled = true; // 启用消息ID

            public Integer getDefaultDelaySeconds() {
                return delaySeconds != null ? delaySeconds : 0;
            }
        }

        /**
         * 消费者配置
         */
        @Data
        public static class ConsumerConfig {
            private Integer maxNumberOfMessages = 10;
            private Integer waitTimeSeconds = 20;
            private Integer visibilityTimeout = 30; // 从队列接收消息后可见时间（秒）
            private String acknowledgeMode = "MANUAL"; // MANUAL or AUTO
            private Boolean deleteAfterRead = false; // 处理后自动删除（建议手动确认）
            private Integer maxConcurrentConsumers = 5;
            private Integer pollTimeoutSeconds = 10; // 轮询超时时间（秒）
            private Long backoffTime = 1000L; // 失败后退避时间（毫秒）
            private Integer maxAttempts = 3;
        }
    }
}
