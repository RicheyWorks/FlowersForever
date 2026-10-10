package com.flowerfarm.gui.automation;

/** A queued action no longer matches the desktop state its caller observed. */
final class DesktopContextChangedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    DesktopContextChangedException() {
        super("Desktop context changed; observe again and use a new request ID");
    }
}
