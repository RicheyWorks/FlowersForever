package com.flowerfarm.automation.mcp;

/** Prepares a diagnostic MCP boot without hiding a missing or short token. */
public final class McpDoctorBootstrap {

    public static final String TOKEN_VALID_PROPERTY =
            "flowerfarm.mcp-doctor.token-valid";
    private static final String HARNESS_TOKEN_PROPERTY = "flowerfarm.harness.token";
    private static final String DOCTOR_PLACEHOLDER_TOKEN =
            "mcp-doctor-placeholder-token-only";

    private McpDoctorBootstrap() {
    }

    /**
     * Lets the application graph start so the doctor can return structured
     * guidance when the real process token is absent or invalid.
     */
    public static void prepare() {
        String token = firstNonBlank(
                System.getProperty(HARNESS_TOKEN_PROPERTY),
                System.getenv("FLOWERFARM_HARNESS_TOKEN"));
        boolean valid = token != null && token.length() >= 24;
        System.setProperty(TOKEN_VALID_PROPERTY, Boolean.toString(valid));
        if (!valid) {
            System.setProperty(HARNESS_TOKEN_PROPERTY, DOCTOR_PLACEHOLDER_TOKEN);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
