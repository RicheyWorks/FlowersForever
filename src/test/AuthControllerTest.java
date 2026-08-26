package com.flowerfarm.controller;

import com.flowerfarm.auth.FarmUserDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthController session contract")
class AuthControllerTest {

    private final FarmUserDirectory directory = new FarmUserDirectory(
            "owner:owner-secret:OWNER,hand:hand-secret:HAND,viewer:view-secret:VIEWER",
            "unused",
            "unused");

    @Test
    @DisplayName("open mode reports local owner capabilities")
    void openModeReportsLocalOwner() {
        AuthController controller = new AuthController(false, directory);

        Map<String, Object> body = controller.me(null);

        assertThat(body).containsEntry("authEnabled", false)
                .containsEntry("username", "local")
                .containsEntry("role", "OWNER")
                .containsEntry("canWrite", true)
                .containsEntry("canClearHistory", true);
    }

    @Test
    @DisplayName("secured mode reports missing authentication without capabilities")
    void securedModeReportsMissingAuthentication() {
        AuthController controller = new AuthController(true, directory);

        Map<String, Object> body = controller.me(null);

        assertThat(body).containsEntry("authEnabled", true)
                .containsEntry("authenticated", false)
                .doesNotContainKeys("canWrite", "canClearHistory");
    }

    @Test
    @DisplayName("secured mode exposes the authenticated barn role")
    void securedModeReportsKnownRole() {
        AuthController controller = new AuthController(true, directory);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "hand",
                        "ignored",
                        List.of(new SimpleGrantedAuthority("ROLE_HAND")));

        Map<String, Object> body = controller.me(authentication);

        assertThat(body).containsEntry("authenticated", true)
                .containsEntry("username", "hand")
                .containsEntry("role", "HAND")
                .containsEntry("canWrite", true)
                .containsEntry("canClearHistory", false);
        assertThat(body.get("roles")).isEqualTo(List.of("HAND"));
    }

    @Test
    @DisplayName("unknown authorities fail closed while a later valid role is honored")
    void unknownAuthoritiesFailClosed() {
        AuthController controller = new AuthController(true, directory);
        UsernamePasswordAuthenticationToken unknown =
                UsernamePasswordAuthenticationToken.authenticated(
                        "external",
                        "ignored",
                        List.of(new SimpleGrantedAuthority("SCOPE_inventory")));
        UsernamePasswordAuthenticationToken ownerAfterUnknown =
                UsernamePasswordAuthenticationToken.authenticated(
                        "owner",
                        "ignored",
                        List.of(
                                new SimpleGrantedAuthority("SCOPE_inventory"),
                                new SimpleGrantedAuthority("ROLE_OWNER")));

        assertThat(controller.me(unknown)).containsEntry("role", "VIEWER")
                .containsEntry("canWrite", false)
                .containsEntry("canClearHistory", false);
        assertThat(controller.me(ownerAfterUnknown)).containsEntry("role", "OWNER")
                .containsEntry("canWrite", true)
                .containsEntry("canClearHistory", true);
    }

    @Test
    @DisplayName("account hints expose usernames and roles but never passwords")
    void accountHintsDoNotExposePasswords() {
        AuthController controller = new AuthController(true, directory);

        ResponseEntity<?> response = controller.accounts();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        String body = String.valueOf(response.getBody());
        assertThat(body).contains("owner", "OWNER", "hand", "HAND", "viewer", "VIEWER")
                .doesNotContain("owner-secret", "hand-secret", "view-secret", "password");
    }

    @Test
    @DisplayName("open account hints are empty")
    void openAccountHintsAreEmpty() {
        AuthController controller = new AuthController(false, directory);

        ResponseEntity<?> response = controller.accounts();

        assertThat(response.getBody()).isEqualTo(Map.of(
                "authEnabled", false,
                "accounts", List.of(),
                "message", "Auth disabled."));
    }
}
