package com.flowerfarm.automation;

/** Non-sensitive metadata for one visible user-facing control. */
public record HarnessControl(String kind, String selector, boolean enabled,
                             boolean focusable, boolean commandable, String label) {

    public HarnessControl {
        label = label == null ? "" : label;
    }

    /** Older plugins may omit the separate display label. */
    public HarnessControl(String kind, String selector, boolean enabled,
                          boolean focusable, boolean commandable) {
        this(kind, selector, enabled, focusable, commandable, "");
    }
}
