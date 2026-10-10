package com.flowerfarm.update;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Decides whether an address is on this computer. Names are never resolved, except {@code localhost}. */
public final class LoopbackAddresses {

    private LoopbackAddresses() {
    }

    /** True for {@code localhost} and literal loopback addresses such as 127.0.0.1 or [::1]. */
    public static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        try {
            String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
            if (literal.isEmpty() || !literal.chars().allMatch(c -> Character.isDigit(c) || c == '.' || c == ':')) {
                return false; // Never resolve names here; only literal loopback addresses.
            }
            return InetAddress.getByName(literal).isLoopbackAddress();
        } catch (UnknownHostException invalid) {
            return false;
        }
    }

    /** True for an http or https address whose host is on this computer. */
    public static boolean isLoopback(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return ("http".equals(scheme) || "https".equals(scheme)) && isLoopbackHost(uri.getHost());
    }
}
