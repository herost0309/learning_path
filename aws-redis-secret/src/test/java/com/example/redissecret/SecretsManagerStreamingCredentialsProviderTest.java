package com.example.redissecret;

import com.example.redissecret.credentials.SecretsManagerStreamingCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretsManagerStreamingCredentialsProviderTest {

    private static final String SECRET_ID = "test/redis/credentials";
    private static final String USERNAME = "appuser";

    @Mock
    private SecretsManagerClient secretsManagerClient;

    @Mock
    private GetSecretValueResponse getSecretValueResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SecretsManagerStreamingCredentialsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SecretsManagerStreamingCredentialsProvider(SECRET_ID, secretsManagerClient);
    }

    private void mockSecretValue(String username, String password) throws Exception {
        String json = objectMapper.writeValueAsString(
                new SecretsManagerStreamingCredentialsProvider.SecretValue(username, password));
        when(getSecretValueResponse.secretString()).thenReturn(json);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(getSecretValueResponse);
    }

    @Test
    void init_shouldEmitInitialCredentials() throws Exception {
        mockSecretValue(USERNAME, "password-v1");

        provider.init();

        StepVerifier.create(provider.resolveCredentials())
                .expectNextMatches(creds ->
                        creds.getUsername().equals(USERNAME) &&
                        new String(creds.getPassword()).equals("password-v1"))
                .verifyComplete();
    }

    @Test
    void refreshCredentials_shouldEmitNewPassword() throws Exception {
        // Mock: first call returns v1, second call returns v2
        String v1 = objectMapper.writeValueAsString(
                new SecretsManagerStreamingCredentialsProvider.SecretValue(USERNAME, "password-v1"));
        String v2 = objectMapper.writeValueAsString(
                new SecretsManagerStreamingCredentialsProvider.SecretValue(USERNAME, "password-v2"));
        when(getSecretValueResponse.secretString()).thenReturn(v1, v2);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(getSecretValueResponse);

        // Init emits v1 into the replay sink
        provider.init();

        // Subscribe: replay().latest() immediately replays v1.
        // After verifying v1, then() triggers refresh which emits v2.
        StepVerifier.create(provider.credentials().take(2))
                .expectNextMatches(c ->
                        new String(c.getPassword()).equals("password-v1"))
                .then(() -> provider.refreshCredentials())
                .expectNextMatches(c ->
                        new String(c.getPassword()).equals("password-v2"))
                .verifyComplete();
    }

    @Test
    void refreshCredentials_noChange_shouldNotEmit() throws Exception {
        mockSecretValue(USERNAME, "password-v1");

        provider.init();
        provider.refreshCredentials(); // Same password, should not emit duplicate

        // Only one emission expected (from init)
        StepVerifier.create(provider.credentials().take(1))
                .expectNextMatches(c ->
                        new String(c.getPassword()).equals("password-v1"))
                .verifyComplete();
    }

    @Test
    void supportsStreaming_returnsTrue() {
        assert provider.supportsStreaming();
    }
}
