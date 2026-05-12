package com.example.redissecret.credentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled component that periodically checks AWS Secrets Manager for
 * credential changes and pushes updates to the streaming credentials provider.
 *
 * <p>This is the MVP approach using polling. For lower detection latency,
 * add an EventBridge rule forwarding rotation events to SQS and consume
 * them with {@code @SqsListener}.</p>
 */
public class SecretRotationWatcher {

    private static final Logger log = LoggerFactory.getLogger(SecretRotationWatcher.class);

    private final SecretsManagerStreamingCredentialsProvider credentialsProvider;

    public SecretRotationWatcher(SecretsManagerStreamingCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    @Scheduled(fixedDelayString = "${redis.secret.poll-interval:60}000")
    public void watch() {
        log.debug("Polling Secrets Manager for credential changes...");
        credentialsProvider.refreshCredentials();
    }
}
