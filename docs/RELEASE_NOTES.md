# FlowersForever 1.1.0 private-pilot notes

Version 1.1.0 is in proprietary pilot hardening and is not yet approved for
customer distribution. The previous 1.0.19 artifacts are retired.

## Release safety

- One fully tested executable JAR feeds both Windows and macOS installers.
- Tags, POM versions, JAR names, and installer versions must match exactly.
- A release is created only after tests, SBOM, license policy, and both native
  packaging jobs pass.
- SHA-256 values cover every installer, the JAR, SBOM, and third-party notice.
- Weka and its GPLv3 distribution issue were removed; transparent ordinary
  least-squares forecasting is implemented directly in FlowersForever.
- Runtime third-party notices are generated, bundled, and attached to releases.

## Data and security

- H2 schemas now use versioned Flyway migrations followed by Hibernate
  validation. Existing 1.0 databases are baselined and upgraded without
  replacing their farm records.
- A migration acceptance test verifies both fresh installation and an in-place
  legacy order upgrade.
- Credential-shaped values are redacted from API and connector logs.
- Unexpected API failures include a support incident ID without exposing the
  underlying secret or exception detail.
- Authentication no longer compiles a demo password fallback into production.

## Existing production workflow

- Stable packaged data under `%LOCALAPPDATA%\FlowersForever\data`
- Checksum-validated backup and recoverable restore
- Harvest, inventory, CRM, order, market-day, reports, briefing, and closeout
- Role-aware desktop and REST controls
- Offline-first connector mirrors and guarded live connector modes

Review [private pilot access](PILOT_ACCESS.md), [deployment](DEPLOYMENT.md),
[backup and restore](BACKUP_RESTORE.md), and [known limitations](KNOWN_LIMITATIONS.md)
before approving a pilot workstation.
