package com.flowerfarm.automation;

/** Non-sensitive metadata for one visible user-facing control. */
public record HarnessControl(String kind, String selector, boolean enabled,
                             boolean focusable, boolean commandable) {
}
