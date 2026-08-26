# Security policy

## Supported versions

| Component | Version | Notes |
|-----------|---------|--------|
| **Application** | `1.0.x` on `main` | Latest release tag is the supported line |
| **Spring Boot** | **3.5.16** (parent) | Final OSS patch of the 3.5 line; prefer upgrading to supported Boot when moving to production long-term |
| **Java** | 17+ | Temurin 17 in CI |

Spring Boot **3.4** and **3.5** are past open-source end of life. This project stays on **3.5.16** (last 3.5 OSS release) rather than jumping to Boot **4.x** until a deliberate migration is planned. Commercial / extended support options exist from Spring/vendors if you must remain on 3.x in production.

## Reporting a vulnerability

Please open a **private** security advisory on GitHub if available, or contact the repository owner. Do not file a public issue for credential leaks or remote exploit details until a fix is ready.

## Application security model

### Default (open API)

With **no** `auth` profile, the embedded REST API allows local requests (barn single-user / demo) and binds to `127.0.0.1`. This is intentional for offline farm PCs. **Do not expose port 8080 to the public internet** in open mode.

The Docker image activates a separate fail-closed `container` profile. It binds to
`0.0.0.0`, requires HTTP Basic, rejects passwords shorter than 12 characters, and
requires at least one OWNER. Configure accounts with
`FLOWERFARM_AUTH_USERS=user:password:ROLE,...`; the container refuses to start when
secure credentials are absent.

### Barn auth (`auth` profile)

Enable with:

```bash
java -jar flowerfarm-manager-*.jar --spring.profiles.active=auth
```

| Role | REST | GUI writes | Clear connector history |
|------|------|------------|-------------------------|
| **OWNER** | full | yes | yes |
| **HAND** | read + write | yes | no |
| **VIEWER** | GET only | blocked | no |

- Transport: **HTTP Basic** (local LAN / shared PC). Use HTTPS reverse-proxy if you ever put this on a wider network.
- Sessions: secured REST requests are stateless; credentials are evaluated on each request.
- CSRF is disabled (stateless API + desktop client). Suitable for local barn use, not multi-tenant SaaS.
- Actuator: `/actuator/health` and `/actuator/info` remain public when auth is on; other actuator endpoints follow Spring Boot defaults for the profile.

### Credential configuration

The `auth` and `container` profiles fail closed: supply
`FLOWERFARM_AUTH_USERS=user:password:ROLE,...`, use passwords of at least 12
characters, and include at least one OWNER. No usable password ships in the
application configuration. Passwords are encoded with **BCrypt** at runtime;
the environment value remains the cleartext source of truth and should be
protected by the host or secret manager.

Weak demo accounts require the explicit
`FLOWERFARM_ALLOW_DEMO_CREDENTIALS=true` override and must never be used on a
shared or externally reachable deployment.

### Connectors and secrets

- **Offline (default):** dual-mode connectors write JSON mirrors under `data/` — no cloud keys required.
- **Live mode:** put API tokens in `application-local.properties` (gitignored) or environment-specific config. Never commit live keys.
- Webhook HMAC secrets and shop tokens should be rotated if leaked.
- Connector requests use bounded network timeouts, and connector failure details redact common credential forms before they reach API responses or audit history.
- Offline JSON mirrors are replaced atomically so an interrupted export keeps the last complete file.

### Data at rest

Default inventory DB is local **H2** (`./data/flowerfarm.mv.db`) or optional **SQLite**. Protect the `data/` directory with OS file permissions on multi-user machines.

## Dependency hygiene

- CI runs `mvn clean verify` on every push/PR to `main`.
- Dependabot opens weekly PRs for Maven and GitHub Actions.
- Major framework bumps (e.g. Spring Boot 4, OpenPDF 3) are reviewed deliberately; minor/patch deps are preferred.

## Hardening checklist (shared barn PC)

1. Enable `auth` profile; change all default passwords.
2. Keep `8080` off the public internet (desktop open mode is loopback-only; authenticated LAN deployments still need a firewall).
3. Restrict filesystem access to `data/` and any `application-local.properties`.
4. Prefer VIEWER accounts for office/guest kiosks.
5. Review connector audit history for unexpected live syncs.
6. For containers, supply `FLOWERFARM_AUTH_USERS` and terminate TLS at a trusted reverse proxy.
