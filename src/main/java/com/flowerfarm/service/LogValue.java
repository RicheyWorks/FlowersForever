package com.flowerfarm.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Escape variable log fields so external text cannot create another log line. */
public final class LogValue {
    private LogValue() { }

    public static String escape(Object value) {
        try {
            // Keep ordinary spaces readable; literal '+' and '%' are already encoded.
            return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8.name())
                    .replace("+", " ");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("Required UTF-8 encoding unavailable", impossible);
        }
    }
}
