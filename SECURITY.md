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

### AI and automation harness

The provider-neutral runtime harness and its loopback REST transport are
disabled by default. Enabling it requires a separate token of at least 24
characters on every discovery, observation, and command request. The token does
not replace normal Spring Security: under the `auth` profile, HTTP Basic role
authorization is also required.

Read and navigation are the only default capabilities after enablement. Draft,
mutation, and destructive actions require separate operator switches;
destructive access cannot be enabled without mutation access. Physical-world
actions add another independent switch and remain blocked unless mutation and
destructive access are also enabled. Each physical command expires within at
most 30 seconds, preventing delayed delivery of stale actuation. Plugins declare
the risk for each action, so a client cannot downgrade its own request. Generic
Swing button activation is classified destructive. Completed request IDs are
replay-safe within a bounded process-local cache, and conflicting reuse fails.

UI observations exclude editable field and password values. Visible control
labels can still contain private farm, customer, or event information. Keep the
harness on loopback, protect the token like a credential, supervise elevated
sessions, and disable it again after use. The transport is unavailable in
`container`, `cli`, and `release-smoke` profiles. Configuration and integration
examples are in the [AI harness guide](docs/AI_HARNESS.md).

Ordinary snapshots publish every reachable primary/nested route using tab
titles only. `navigate-route` accepts only an exact published path and rejects
missing, disabled, or ambiguous steps. This expands navigation coverage without
exposing field contents or bypassing the same risk policy applied to other
commands.

The authenticated manifest exposes effective capability flags and JSON-Schema
tool definitions, but never returns the harness token or editable field values.
Clients should omit tools marked `allowed=false`; server-side policy still
rejects them if submitted directly.

Structured control values and application-window pixels require the independent
`SENSITIVE_READ` policy, which is disabled by default. Password fields remain
unreadable under every policy. Captures are bounded, returned in memory rather
than written to server storage, and count against the 8 MiB replay-output cache.
Treat enabled sensitive-read sessions and downstream model transcripts as access
to visible customer and farm data.

Device and robot plugins may declare typed sensor signals. The gateway validates
their JSON types, strips signals marked sensitive unless sensitive read is
enabled, rejects undeclared physical metadata, and requires descriptor/snapshot
identity to match. This boundary does
not replace hardware safety: robot adapters must enforce local emergency stop,
watchdog, collision, speed/travel, coordinate-frame, interlock, and loss-of-link
behavior independently of the AI client and network transport.

The `tesla-optimus` integration is disabled by default and currently has only a
simulator transport. It exposes bounded high-level tasks with a stage/token/
execute handshake, never raw joints, trajectories, or velocity commands. A
future hardware adapter must use an official owner interface, authenticate and
attest the intended robot, reject stale or mismatched state locally, preserve a
hardware stop path independent of this process, and require robot-local reset
after an emergency stop. Do not enable physical actions merely to make a tool
catalog look complete.

Reviewed Optimus plans expire within five minutes and cannot be replaced without
cancel or execution. The plugin blocks staging and motion on stale, future-dated,
stopped, or not-ready state and consumes its local plan before an adapter execute
call. This fail-closed behavior makes an ambiguous transport result require
observation and a new review; a hardware adapter must still enforce token
idempotency and plan expiry on the robot side.

Robot state, staged plans, and command receipts must carry adapter-provided
timestamps. Missing timestamps are rejected instead of being replaced with the
application clock, and future-dated state, plans, and receipts fail closed.

Hardware mode additionally requires
`flowerfarm.harness.optimus.expected-robot-id` (or
`FLOWERFARM_OPTIMUS_EXPECTED_ROBOT_ID`) to exactly match the adapter-reported
stable ID. Simulator/hardware mode, model, software version, and coordinate
frame are explicit; an identity change after startup fails closed. Active jobs
report a typed lifecycle and command ID, and graceful cancellation must target
that exact ID using a fresh, expiring physical command. The robot-owned
emergency-stop path remains independent and never exposes a remote reset.

Robot command receipts and meaningful lifecycle changes are persisted as
append-only events in the local database. Records include the adapter-reported
robot ID and bounded status detail so recovery can reject cross-robot history;
protect them with the same OS access controls as other farm data. The read-only
`get-operation` and bounded `list-operations` tools return status, progress,
timestamps, and reconciliation state but do not return the stored robot ID or
status detail. A nonterminal
record missing from current adapter state is marked `UNKNOWN`, never inferred as
successful.

Normal Optimus control is protected by a durable, robot-scoped capability
lease. Only one 15–300 second lease can be active for a stable adapter identity.
Staging, staged-plan cancellation, execution, graceful cancellation, and safe
pose require its token; renewal rotates the token, release invalidates it, and
expiry fails closed. Emergency stop does not require lease ownership so a
second authorized supervisor can still stop the robot. The database stores the
lease holder label and a SHA-256 token digest, never the plaintext capability.
Plaintext tokens exist transiently in the acquisition/renewal response, caller
memory, request body, and bounded in-process replay cache: treat all four as
secrets and do not log them. The holder label stays in the local lease record and
is omitted from AI-facing observations and status responses; it is audit metadata
under the shared local harness credential, not proof of an external human
identity.

### Automatic updates

The desktop fetches one signed manifest over HTTPS (and, after consent, the
installers it lists). Manifests and installers must carry an Ed25519 signature
from a publisher key built into the app; size and SHA-256 must match; plain HTTP
and unsigned or altered files are refused. No farm data is sent. Checks can be
turned off by the farm or with `flowerfarm.updates.enabled=false`. The private
signing key never enters the repository. See [Automatic updates](docs/AUTO_UPDATES.md).

### Data at rest

Default inventory DB is local **H2** (`./data/flowerfarm.mv.db`) or optional **SQLite**. Protect the `data/` directory with OS file permissions on multi-user machines.

## Dependency hygiene

- CI runs `mvn clean verify` on every push/PR to `main`.
- Dependabot opens weekly PRs for Maven and GitHub Actions.
- Major framework bumps (e.g. Spring Boot 4, OpenPDF 3) are reviewed deliberately; minor/patch deps are preferred.
- The POM overrides inherited versions with Jackson **2.21.5**, Log4j **2.25.5**,
  Commons Lang **3.18.0**, and Tomcat **10.1.59** to address dependency advisories.
  Tomcat's FORM authentication, DIGEST replay, and path-constraint fixes were made
  in 10.1.58, but that release vote failed; Apache identifies **10.1.59** as the
  released version containing those fixes. See the
  [Apache Tomcat security advisory](https://tomcat.apache.org/security-10.html#Fixed_in_Apache_Tomcat_10.1.59).

## Hardening checklist (shared barn PC)

1. Enable `auth` profile; change all default passwords.
2. Keep `8080` off the public internet (desktop open mode is loopback-only; authenticated LAN deployments still need a firewall).
3. Restrict filesystem access to `data/` and any `application-local.properties`.
4. Prefer VIEWER accounts for office/guest kiosks.
5. Review connector audit history for unexpected live syncs.
6. For containers, supply `FLOWERFARM_AUTH_USERS` and terminate TLS at a trusted reverse proxy.
7. Leave the AI harness disabled unless a supervised local integration needs it; rotate its token after any suspected disclosure.
