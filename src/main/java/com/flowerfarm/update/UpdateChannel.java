package com.flowerfarm.update;

import java.util.Locale;
import java.util.Optional;

/**
 * Staged rollout rings. A release is listed for one or more channels; each
 * install follows one channel. {@code owner} sees everything first, then
 * {@code early} (one pilot tester), then {@code stable} (everyone).
 */
public enum UpdateChannel {
    OWNER(0), EARLY(1), STABLE(2);

    private final int ring;

    UpdateChannel(int ring) {
        this.ring = ring;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True when this install should see a release listed for {@code releaseChannel}. */
    public boolean accepts(String releaseChannel) {
        return fromName(releaseChannel).map(listed -> listed.ring >= ring).orElse(false);
    }

    public static Optional<UpdateChannel> fromName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (UpdateChannel channel : values()) {
            if (channel.id().equals(value.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
