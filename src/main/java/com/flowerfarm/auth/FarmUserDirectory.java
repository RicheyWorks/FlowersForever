package com.flowerfarm.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses multi-user accounts from configuration.
 *
 * <pre>
 * # Format: user:pass:ROLE,user2:pass2:ROLE
 * flowerfarm.auth.users=farm:kitsap:OWNER,hand:harvest:HAND,viewer:view:VIEWER
 * </pre>
 *
 * Falls back to single-user {@code flowerfarm.auth.username/password} as OWNER
 * when {@code users} is blank.
 */
@Component
public class FarmUserDirectory {

    private final Map<String, FarmUser> users = new LinkedHashMap<>();

    public FarmUserDirectory(
            @Value("${flowerfarm.auth.users:}") String usersCsv,
            @Value("${flowerfarm.auth.username:farm}") String fallbackUser,
            @Value("${flowerfarm.auth.password:kitsap}") String fallbackPass) {
        parse(usersCsv);
        if (users.isEmpty()) {
            users.put(fallbackUser.trim().toLowerCase(),
                    new FarmUser(fallbackUser.trim(), fallbackPass, FarmRole.OWNER));
        }
    }

    private void parse(String usersCsv) {
        if (usersCsv == null || usersCsv.isBlank()) {
            return;
        }
        for (String part : usersCsv.split(",")) {
            String[] bits = part.trim().split(":");
            if (bits.length < 2) {
                continue;
            }
            String user = bits[0].trim();
            String pass = bits[1];
            FarmRole role = bits.length >= 3 ? FarmRole.fromString(bits[2]) : FarmRole.HAND;
            if (!user.isEmpty()) {
                users.put(user.toLowerCase(), new FarmUser(user, pass, role));
            }
        }
    }

    public Optional<FarmUser> authenticate(String username, char[] password) {
        if (username == null) {
            return Optional.empty();
        }
        FarmUser u = users.get(username.trim().toLowerCase());
        if (u == null) {
            return Optional.empty();
        }
        String entered = password == null ? "" : new String(password);
        if (u.password().equals(entered)) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public Optional<FarmUser> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(username.trim().toLowerCase()));
    }

    public List<FarmUser> listUsers() {
        return new ArrayList<>(users.values());
    }
}
