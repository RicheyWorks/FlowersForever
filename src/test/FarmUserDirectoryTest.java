package com.flowerfarm.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FarmUserDirectory")
class FarmUserDirectoryTest {

    @Test
    @DisplayName("parses multi-user CSV with roles")
    void parseUsers() {
        FarmUserDirectory dir = new FarmUserDirectory(
                "farm:kitsap:OWNER,hand:harvest:HAND,viewer:view:VIEWER",
                "x", "y");
        assertThat(dir.listUsers()).hasSize(3);
        assertThat(dir.authenticate("farm", "kitsap".toCharArray())).isPresent()
                .get().extracting(FarmUser::role).isEqualTo(FarmRole.OWNER);
        assertThat(dir.authenticate("hand", "harvest".toCharArray())).isPresent()
                .get().extracting(FarmUser::role).isEqualTo(FarmRole.HAND);
        assertThat(dir.authenticate("viewer", "view".toCharArray())).isPresent()
                .get().extracting(FarmUser::role).isEqualTo(FarmRole.VIEWER);
        assertThat(dir.authenticate("farm", "wrong".toCharArray())).isEmpty();
    }

    @Test
    @DisplayName("falls back to single user as OWNER")
    void fallback() {
        FarmUserDirectory dir = new FarmUserDirectory("", "solo", "secret");
        assertThat(dir.listUsers()).hasSize(1);
        assertThat(dir.authenticate("solo", "secret".toCharArray())).isPresent()
                .get().extracting(FarmUser::role).isEqualTo(FarmRole.OWNER);
    }
}
