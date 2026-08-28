package com.flowerfarm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecretRedactor")
class SecretRedactorTest {

    @Test
    @DisplayName("redacts configuration, authorization, and URL credentials")
    void redactsCredentialShapes() {
        String safe = SecretRedactor.redact(
                "database-password=secret Authorization: Bearer token-value "
                        + "https://user:pass@example.test/path?api-key=abc123",
                500);

        assertThat(safe)
                .contains("password=[REDACTED]", "Bearer [REDACTED]",
                        "user:[REDACTED]@", "api-key=[REDACTED]")
                .doesNotContain("password=secret", "token-value", "pass@example", "abc123");
    }

    @Test
    @DisplayName("folds lines and bounds output")
    void foldsAndBounds() {
        assertThat(SecretRedactor.redact(" one\r\ntwo three ", 10))
                .isEqualTo("one  two …");
        assertThatThrownBy(() -> SecretRedactor.redact("x", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
