package com.flowerfarm.controller;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.auth.FarmUserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lightweight session info for clients using HTTP Basic barn auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final boolean enabled;
    private final FarmUserDirectory directory;

    public AuthController(
            @Value("${flowerfarm.auth.enabled:false}") boolean enabled,
            FarmUserDirectory directory) {
        this.enabled = enabled;
        this.directory = directory;
    }

    /**
     * Who am I — works with or without auth profile.
     * When secured, returns the authenticated principal; otherwise local open mode.
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authEnabled", enabled);
        if (!enabled) {
            body.put("username", "local");
            body.put("role", FarmRole.OWNER.name());
            body.put("canWrite", true);
            body.put("canClearHistory", true);
            body.put("message", "Auth disabled — full local access.");
            return body;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            body.put("authenticated", false);
            body.put("message", "Not authenticated. Use HTTP Basic.");
            return body;
        }
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toList());
        FarmRole role = roles.stream()
                .map(FarmRole::fromString)
                .findFirst()
                .orElse(FarmRole.VIEWER);
        body.put("authenticated", true);
        body.put("username", username);
        body.put("roles", roles);
        body.put("role", role.name());
        body.put("canWrite", role.canWrite());
        body.put("canClearHistory", role.canClearHistory());
        body.put("roleDescription", role.shortDescription());
        return body;
    }

    /** Public list of usernames + roles (no passwords) for barn PC hints. */
    @GetMapping("/accounts")
    public ResponseEntity<?> accounts() {
        if (!enabled) {
            return ResponseEntity.ok(Map.of(
                    "authEnabled", false,
                    "accounts", List.of(),
                    "message", "Auth disabled."));
        }
        List<Map<String, String>> accounts = directory.listUsers().stream()
                .map(u -> Map.of(
                        "username", u.username(),
                        "role", u.role().name(),
                        "label", u.role().label()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "authEnabled", true,
                "accounts", accounts));
    }
}
