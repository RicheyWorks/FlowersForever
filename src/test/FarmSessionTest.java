package com.flowerfarm.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FarmSession / FarmRole permissions")
class FarmSessionTest {

    @AfterEach
    void tearDown() {
        FarmSession.clear();
        FarmSession.setAuthEnabled(false);
    }

    @Test
    @DisplayName("auth off defaults to full OWNER access")
    void authOffIsOwner() {
        FarmSession.setAuthEnabled(false);
        FarmSession.clear();
        assertThat(FarmSession.canMutateData()).isTrue();
        assertThat(FarmSession.canClearHistory()).isTrue();
        assertThat(FarmSession.role()).isEqualTo(FarmRole.OWNER);
    }

    @Test
    @DisplayName("VIEWER is read-only")
    void viewerReadOnly() {
        FarmSession.setAuthEnabled(true);
        FarmSession.set(new FarmUser("viewer", "view", FarmRole.VIEWER));
        assertThat(FarmSession.canMutateData()).isFalse();
        assertThat(FarmSession.canClearHistory()).isFalse();
        assertThat(FarmSession.displayName()).contains("Viewer");
    }

    @Test
    @DisplayName("HAND can write but not clear history")
    void handPermissions() {
        FarmSession.setAuthEnabled(true);
        FarmSession.set(new FarmUser("hand", "harvest", FarmRole.HAND));
        assertThat(FarmSession.canMutateData()).isTrue();
        assertThat(FarmSession.canClearHistory()).isFalse();
        assertThat(FarmRole.HAND.shortDescription()).containsIgnoringCase("write");
    }

    @Test
    @DisplayName("OWNER full access")
    void ownerFull() {
        FarmSession.setAuthEnabled(true);
        FarmSession.set(new FarmUser("farm", "kitsap", FarmRole.OWNER));
        assertThat(FarmSession.canMutateData()).isTrue();
        assertThat(FarmSession.canClearHistory()).isTrue();
        assertThat(FarmRole.OWNER.canClearHistory()).isTrue();
    }
}
