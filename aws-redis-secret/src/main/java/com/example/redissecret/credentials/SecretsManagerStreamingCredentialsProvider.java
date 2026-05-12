package com.example.redissecret.credentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Streaming credentials provider for Lettuce 6.6+ that integrates with AWS Secrets Manager.
 *
 * <p>Uses a {@link Sinks.Many} to push credential updates to Lettuce. When
 * {@link #refreshCredentials()} detects a password change, the new credentials
 * are emitted. Lettuce with {@code ReauthenticateBehavior.ON_NEW_CREDENTIALS}
 * will then send AUTH on all active connections without disconnecting.</p>
 *
 * <p>Uses AWS SDK v2 {@link SecretsManagerClient} directly. The polling interval
 * (default 60s) already limits API call frequency, making a separate caching
 * layer unnecessary for MVP.</p>
 */
public class SecretsManagerStreamingCredentialsProvider implements RedisCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerStreamingCredentialsProvider.class);

    private final String secretId;
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    /**
     * Hot sink that replays the latest credential to new subscribers.
     * This ensures Lettuce subscriptions and new connection requests
     * always receive the most recent credentials.
     */
    private final Sinks.Many<RedisCredentials> sink = Sinks.many().replay().latest();

    /**
     * Cached password used for change detection. Avoids pushing duplicate credentials.
     */
    private final AtomicReference<String> cachedPassword = new AtomicReference<>();

    public SecretsManagerStreamingCredentialsProvider(String secretId,
                                                      SecretsManagerClient secretsManagerClient) {
        this.secretId = secretId;
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Load initial credentials from Secrets Manager and emit them.
     * Must be called once after construction, before the provider is
     * handed to Lettuce.
     */
    public void init() {
        doRefresh("initialization");
    }

    /**
     * Called by {@link SecretRotationWatcher} on each polling tick.
     * Fetches the latest secret, compares with the cached value,
     * and emits new credentials if changed.
     */
    public void refreshCredentials() {
        doRefresh("rotation-poll");
    }

    private void doRefresh(String trigger) {
        try {
            String secretString = fetchSecret();
            if (secretString == null || secretString.isBlank()) {
                log.warn("[{}] Secret value is empty for secretId={}", trigger, secretId);
                return;
            }

            SecretValue secretValue = parseSecret(secretString);
            String newPassword = secretValue.password();
            String oldPassword = cachedPassword.get();

            if (newPassword.equals(oldPassword)) {
                log.debug("[{}] Password unchanged for secretId={}", trigger, secretId);
                return;
            }

            RedisCredentials credentials = RedisCredentials.just(secretValue.username(), newPassword);
            Sinks.EmitResult result = sink.tryEmitNext(credentials);

            if (result.isSuccess()) {
                cachedPassword.set(newPassword);
                log.info("[{}] New credentials emitted for secretId={}, user={}", trigger, secretId, secretValue.username());
            } else {
                log.error("[{}] Failed to emit credentials: emitResult={}", trigger, result);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to refresh credentials for secretId={}", trigger, secretId, e);
        }
    }

    private String fetchSecret() {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretId)
                .build();
        return secretsManagerClient.getSecretValue(request).secretString();
    }

    private SecretValue parseSecret(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, SecretValue.class);
    }

    /**
     * Returns a Mono with the current credential.
     * Used by Lettuce when establishing new connections.
     */
    @Override
    public Mono<RedisCredentials> resolveCredentials() {
        return sink.asFlux().next();
    }

    /**
     * Returns the continuous stream of credential updates.
     * Lettuce subscribes to this for the streaming re-authentication feature.
     */
    @Override
    public Flux<RedisCredentials> credentials() {
        return sink.asFlux();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * Get the current cached username (used for initial RedisURI configuration).
     */
    public String getCurrentUsername() {
        RedisCredentials creds = sink.asFlux().blockFirst();
        return creds != null ? creds.getUsername() : null;
    }

    /**
     * Get the current cached password (used for initial RedisURI configuration).
     */
    public String getCurrentPassword() {
        RedisCredentials creds = sink.asFlux().blockFirst();
        return creds != null ? new String(creds.getPassword()) : null;
    }

    /**
     * Internal record for deserializing the Secrets Manager JSON payload.
     */
    public record SecretValue(String username, String password) {
    }
}
