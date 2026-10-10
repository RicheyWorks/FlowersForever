package com.flowerfarm.automation.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MCP doctor bootstrap")
class McpDoctorBootstrapTest {

    private static final String TOKEN_PROPERTY = "flowerfarm.harness.token";
    private String originalToken;
    private String originalValidity;

    @BeforeEach
    void rememberProperties() {
        originalToken = System.getProperty(TOKEN_PROPERTY);
        originalValidity = System.getProperty(McpDoctorBootstrap.TOKEN_VALID_PROPERTY);
    }

    @AfterEach
    void restoreProperties() {
        restore(TOKEN_PROPERTY, originalToken);
        restore(McpDoctorBootstrap.TOKEN_VALID_PROPERTY, originalValidity);
    }

    @Test
    @DisplayName("short tokens allow diagnosis but are reported as invalid")
    void preparesPlaceholderForInvalidToken() {
        System.setProperty(TOKEN_PROPERTY, "too-short");

        McpDoctorBootstrap.prepare();

        assertThat(System.getProperty(McpDoctorBootstrap.TOKEN_VALID_PROPERTY))
                .isEqualTo("false");
        assertThat(System.getProperty(TOKEN_PROPERTY))
                .hasSizeGreaterThanOrEqualTo(24)
                .isNotEqualTo("too-short");
    }

    @Test
    @DisplayName("valid process tokens are preserved")
    void preservesValidToken() {
        String token = "valid-doctor-token-123456789";
        System.setProperty(TOKEN_PROPERTY, token);

        McpDoctorBootstrap.prepare();

        assertThat(System.getProperty(McpDoctorBootstrap.TOKEN_VALID_PROPERTY))
                .isEqualTo("true");
        assertThat(System.getProperty(TOKEN_PROPERTY)).isEqualTo(token);
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
