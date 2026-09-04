package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.model.RobotControlLease;
import com.flowerfarm.repository.RobotControlLeaseJpaRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.holder;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.robotKey;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.simulated;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.ttl;

/** Transactional, restart-safe robot control lease store. */
@Component
public class JpaOptimusControlLeaseService
        implements OptimusControlLeaseService {

    private final RobotControlLeaseJpaRepository repository;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public JpaOptimusControlLeaseService(
            RobotControlLeaseJpaRepository repository) {
        this(repository, Clock.systemUTC(), new SecureRandom());
    }

    JpaOptimusControlLeaseService(
            RobotControlLeaseJpaRepository repository,
            Clock clock, SecureRandom random) {
        this.repository = repository;
        this.clock = clock;
        this.random = random;
    }

    @Override
    @Transactional
    public LeaseGrant acquire(
            RobotIdentity identity, String holder, int ttlSeconds) {
        String key = robotKey(identity);
        Instant now = clock.instant();
        RobotControlLease current = repository.findForUpdate(key).orElse(null);
        if (current != null && current.activeAt(now)) {
            throw new IllegalStateException("Robot control is already leased until "
                    + current.getExpiresAt());
        }
        requireIdentity(current, identity);
        return replace(current, key, identity, UUID.randomUUID().toString(),
                holder(holder), ttl(ttlSeconds), now);
    }

    @Override
    @Transactional
    public LeaseGrant renew(
            RobotIdentity identity, String leaseToken, int ttlSeconds) {
        Instant now = clock.instant();
        RobotControlLease current = requireActive(identity, leaseToken, now, true);
        return replace(current, robotKey(identity), identity, current.getLeaseId(),
                current.getHolder(), ttl(ttlSeconds), now);
    }

    @Override
    @Transactional
    public void release(RobotIdentity identity, String leaseToken) {
        Instant now = clock.instant();
        RobotControlLease current = requireActive(identity, leaseToken, now, true);
        current.release(now);
        repository.save(current);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaseAuthorization requireValid(
            RobotIdentity identity, String leaseToken) {
        RobotControlLease current = requireActive(
                identity, leaseToken, clock.instant(), false);
        return new LeaseAuthorization(current.getLeaseId(), current.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public LeaseStatus status(RobotIdentity identity) {
        RobotControlLease current = repository.findById(robotKey(identity)).orElse(null);
        Instant now = clock.instant();
        if (current == null || !current.activeAt(now)
                || !current.belongsTo(identity.robotId(), simulated(identity))) {
            return LeaseStatus.inactive();
        }
        return new LeaseStatus(true, current.getLeaseId(),
                current.getExpiresAt().toString());
    }

    private LeaseGrant replace(
            RobotControlLease current, String key, RobotIdentity identity,
            String leaseId, String holder, int ttlSeconds, Instant now) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBytes);
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        if (current == null) {
            current = new RobotControlLease(
                    key, identity.robotId(), simulated(identity), leaseId,
                    holder, TransientOptimusControlLeaseService.tokenHash(token),
                    now, expiresAt);
        } else {
            current.replace(leaseId, holder,
                    TransientOptimusControlLeaseService.tokenHash(token),
                    now, expiresAt);
        }
        try {
            repository.saveAndFlush(current);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                    "Another controller acquired this robot lease concurrently",
                    exception);
        }
        return new LeaseGrant(leaseId, token, now, expiresAt);
    }

    private RobotControlLease requireActive(
            RobotIdentity identity, String leaseToken,
            Instant now, boolean lock) {
        String key = robotKey(identity);
        RobotControlLease current = (lock
                ? repository.findForUpdate(key) : repository.findById(key))
                .orElseThrow(JpaOptimusControlLeaseService::invalidLease);
        requireIdentity(current, identity);
        String suppliedHash = TransientOptimusControlLeaseService
                .tokenHash(leaseToken);
        if (!current.activeAt(now) || !MessageDigest.isEqual(
                current.getTokenHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                suppliedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw invalidLease();
        }
        return current;
    }

    private static void requireIdentity(
            RobotControlLease current, RobotIdentity identity) {
        if (current != null && !current.belongsTo(
                identity.robotId(), simulated(identity))) {
            throw new IllegalStateException(
                    "Stored control lease belongs to a different robot identity");
        }
    }

    private static IllegalStateException invalidLease() {
        return new IllegalStateException(
                "A valid active control lease is required for this robot");
    }
}
