package com.flowerfarm.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DesktopSession / FarmRole permissions")
class FarmSessionTest {

    private final DesktopSession session = new DesktopSession();

    @Test
    @DisplayName("auth off defaults to full OWNER access")
    void authOffIsOwner() {
        session.setAuthEnabled(false);
        session.clear();
        assertThat(session.canMutateData()).isTrue();
        assertThat(session.canClearHistory()).isTrue();
        assertThat(session.role()).isEqualTo(FarmRole.OWNER);
    }

    @Test
    @DisplayName("VIEWER is read-only")
    void viewerReadOnly() {
        session.setAuthEnabled(true);
        session.set(new FarmUser("viewer", "view", FarmRole.VIEWER));
        assertThat(session.canMutateData()).isFalse();
        assertThat(session.canClearHistory()).isFalse();
        assertThat(session.displayName()).contains("Viewer");
    }

    @Test
    @DisplayName("HAND can write but not clear history")
    void handPermissions() {
        session.setAuthEnabled(true);
        session.set(new FarmUser("hand", "harvest", FarmRole.HAND));
        assertThat(session.canMutateData()).isTrue();
        assertThat(session.canClearHistory()).isFalse();
        assertThat(FarmRole.HAND.shortDescription()).containsIgnoringCase("write");
    }

    @Test
    @DisplayName("OWNER full access")
    void ownerFull() {
        session.setAuthEnabled(true);
        session.set(new FarmUser("farm", "kitsap", FarmRole.OWNER));
        assertThat(session.canMutateData()).isTrue();
        assertThat(session.canClearHistory()).isTrue();
        assertThat(FarmRole.OWNER.canClearHistory()).isTrue();
    }
}
