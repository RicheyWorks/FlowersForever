package com.flowerfarm.gui;

import java.awt.Desktop;
import java.net.URI;

/** Replaceable mailto boundary for deterministic desktop journeys. */
@FunctionalInterface
public interface FarmMailer {

    void openMailto(URI mailto) throws Exception;

    /** Uses the operating system mail handler in the installed application. */
    static FarmMailer desktop() {
        return uri -> {
            if (uri == null) {
                throw new IllegalArgumentException("mailto URI is required");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Desktop mail is not available");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.MAIL)) {
                throw new UnsupportedOperationException("Desktop mail is not available");
            }
            desktop.mail(uri);
        };
    }
}
