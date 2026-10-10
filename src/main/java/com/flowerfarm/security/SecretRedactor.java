package com.flowerfarm.security;

import java.util.regex.Pattern;

/**
 * Removes credential-shaped values before text crosses a logging, API, or
 * connector boundary. The key name is preserved so operators can still see
 * which configuration failed without exposing its value.
 */
public final class SecretRedactor {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"']?(?:api[-_]?key|access[-_]?token|token|secret|password)"
                    + "[\\\"']?\\s*[:=]\\s*[\\\"']?)[^&\\s,\\\"'}]+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*:\\s*(?:bearer|basic)\\s+)[^\\s,;]+");
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "://([^:/@\\s]+):([^@/\\s]+)@");

    private SecretRedactor() {
    }

    /**
     * Redacts common secret formats, folds line breaks, and bounds the result.
     *
     * @param value text that may contain credentials
     * @param maxLength maximum returned length, including an ellipsis when truncated
     * @return safe text, or {@code null} when the input is {@code null}
     */
    public static String redact(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength < 2) {
            throw new IllegalArgumentException("maxLength must be at least 2");
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        sanitized = NAMED_SECRET.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = AUTHORIZATION.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = URL_CREDENTIALS.matcher(sanitized).replaceAll("://$1:[REDACTED]@");
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength - 1) + "…";
        }
        return sanitized;
    }
}
