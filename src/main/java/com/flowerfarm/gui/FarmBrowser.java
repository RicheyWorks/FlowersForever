package com.flowerfarm.gui;

import java.awt.Desktop;
import java.net.URI;

/** Replaceable browser boundary for opening https links from the desktop. */
@FunctionalInterface
public interface FarmBrowser {

    void open(URI uri) throws Exception;

    static FarmBrowser none() {
        return uri -> {
            throw new UnsupportedOperationException("Browser is not available");
        };
    }

    static FarmBrowser desktop() {
        return uri -> {
            if (uri == null) {
                throw new IllegalArgumentException("URI is required");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Desktop browse is not available");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Desktop browse is not available");
            }
            desktop.browse(uri);
        };
    }
}
