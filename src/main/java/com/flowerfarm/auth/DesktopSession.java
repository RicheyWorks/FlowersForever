package com.flowerfarm.auth;

import org.springframework.stereotype.Component;

/**
 * Spring-owned identity and capability state for the single desktop window.
 * REST authorization remains owned by Spring Security and never reads this object.
 */
@Component
public final class DesktopSession {

    private volatile FarmUser current;
    private volatile boolean authEnabled;

    public void setAuthEnabled(boolean enabled) {
        authEnabled = enabled;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void set(FarmUser user) {
        current = user;
    }

    public void clear() {
        current = null;
    }

    public FarmUser current() {
        return current;
    }

    public boolean isAuthenticated() {
        return current != null;
    }

    public FarmRole role() {
        if (!authEnabled) {
            return FarmRole.OWNER;
        }
        return current == null ? FarmRole.VIEWER : current.role();
    }

    public boolean canMutateData() {
        return role().canWrite();
    }

    public boolean canClearHistory() {
        return role().canClearHistory();
    }

    public String displayName() {
        if (!authEnabled) {
            return "local (auth off)";
        }
        if (current == null) {
            return "not signed in";
        }
        return current.username() + " · " + current.role().label();
    }

    public String roleHint() {
        return role().shortDescription();
    }
}
