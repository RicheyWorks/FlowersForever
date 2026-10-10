package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.holder;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.robotKey;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.simulated;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.ttl;

/** Process-local lease store used by directly constructed plugins and tests. */
final class TransientOptimusControlLeaseService
        implements OptimusControlLeaseService {

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LeaseEntry> leases = new HashMap<>();

    TransientOptimusControlLeaseService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized LeaseGrant acquire(
            RobotIdentity identity, String holder, int ttlSeconds) {
        String robotKey = robotKey(identity);
        Instant now = clock.instant();
        LeaseEntry current = leases.get(robotKey);
        if (active(current, now)) {
            throw new IllegalStateException("Robot control is already leased until "
                    + current.expiresAt());
        }
        return replace(robotKey, identity, UUID.randomUUID().toString(),
                holder(holder), ttl(ttlSeconds), now);
    }

    @Override
    public synchronized LeaseGrant renew(
            RobotIdentity identity, String leaseToken, int ttlSeconds) {
        String robotKey = robotKey(identity);
        Instant now = clock.instant();
        LeaseEntry current = requireActive(robotKey, identity, leaseToken, now);
        return replace(robotKey, identity, current.leaseId(),
                current.holder(), ttl(ttlSeconds), now);
    }

    @Override
    public synchronized void release(RobotIdentity identity, String leaseToken) {
        String robotKey = robotKey(identity);
        Instant now = clock.instant();
        requireActive(robotKey, identity, leaseToken, now);
        leases.remove(robotKey);
    }

    @Override
    public synchronized LeaseAuthorization requireValid(
            RobotIdentity identity, String leaseToken) {
        LeaseEntry current = requireActive(
                robotKey(identity), identity, leaseToken, clock.instant());
        return new LeaseAuthorization(current.leaseId(), current.expiresAt());
    }

    @Override
    public synchronized LeaseStatus status(RobotIdentity identity) {
        LeaseEntry current = leases.get(robotKey(identity));
        if (!active(current, clock.instant()) || !sameRobot(current, identity)) {
            return LeaseStatus.inactive();
        }
        return new LeaseStatus(true, current.leaseId(), current.expiresAt().toString());
    }

    private LeaseGrant replace(
            String robotKey, RobotIdentity identity, String leaseId, String holder,
            int ttlSeconds, Instant now) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBytes);
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        leases.put(robotKey, new LeaseEntry(
                leaseId, holder, tokenHash(token), identity.robotId(),
                simulated(identity), now, expiresAt));
        return new LeaseGrant(leaseId, token, now, expiresAt);
    }

    private LeaseEntry requireActive(
            String robotKey, RobotIdentity identity,
            String leaseToken, Instant now) {
        LeaseEntry current = leases.get(robotKey);
        if (!active(current, now) || !sameRobot(current, identity)
                || !constantTimeEquals(current.tokenHash(), tokenHash(leaseToken))) {
            throw new IllegalStateException(
                    "A valid active control lease is required for this robot");
        }
        return current;
    }

    private static boolean active(LeaseEntry entry, Instant now) {
        return entry != null && entry.expiresAt().isAfter(now);
    }

    private static boolean sameRobot(LeaseEntry entry, RobotIdentity identity) {
        return entry != null && entry.robotId().equals(identity.robotId())
                && entry.simulated() == simulated(identity);
    }

    static String tokenHash(String token) {
        String normalized = token == null ? "" : token.trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private record LeaseEntry(
            String leaseId, String holder, String tokenHash,
            String robotId, boolean simulated,
            Instant issuedAt, Instant expiresAt) {
    }
}
