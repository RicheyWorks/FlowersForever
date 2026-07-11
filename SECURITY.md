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

With **no** `auth` profile, the embedded REST API allows all local requests (barn single-user / demo). This is intentional for offline farm PCs. **Do not expose port 8080 to the public internet** in this mode.

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
- CSRF is disabled (stateless API + desktop client). Suitable for local barn use, not multi-tenant SaaS.
- Actuator: `/actuator/health` and `/actuator/info` remain public when auth is on; other actuator endpoints follow Spring Boot defaults for the profile.

### Default demo accounts

Configured in `application-auth.properties` (change before any shared deployment):

| User | Password | Role |
|------|----------|------|
| `farm` | `kitsap` | OWNER |
| `hand` | `harvest` | HAND |
| `viewer` | `view` | VIEWER |

Override with `flowerfarm.auth.users=user:pass:ROLE,...`. Passwords are encoded with **BCrypt** at runtime for Spring Security; the property file still holds the cleartext source of truth for the barn directory — treat that file as sensitive on shared machines.

### Connectors and secrets

- **Offline (default):** dual-mode connectors write JSON mirrors under `data/` — no cloud keys required.
- **Live mode:** put API tokens in `application-local.properties` (gitignored) or environment-specific config. Never commit live keys.
- Webhook HMAC secrets and shop tokens should be rotated if leaked.

### Data at rest

Default inventory DB is local **H2** (`./data/flowerfarm.mv.db`) or optional **SQLite**. Protect the `data/` directory with OS file permissions on multi-user machines.

## Dependency hygiene

- CI runs `mvn clean verify` on every push/PR to `main`.
- Dependabot opens weekly PRs for Maven and GitHub Actions.
- Major framework bumps (e.g. Spring Boot 4, OpenPDF 3) are reviewed deliberately; minor/patch deps are preferred.

## Hardening checklist (shared barn PC)

1. Enable `auth` profile; change all default passwords.
2. Keep `8080` off the public internet (firewall / bind to localhost or LAN only if needed).
3. Restrict filesystem access to `data/` and any `application-local.properties`.
4. Prefer VIEWER accounts for office/guest kiosks.
5. Review connector audit history for unexpected live syncs.
