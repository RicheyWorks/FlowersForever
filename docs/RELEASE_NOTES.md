# FlowersForever private-preview notes

## Next — Visual Season & Bed Planner foundation

- Adds a persistent Season Planner with fast planting-block entry, edit, and
  removal while preserving existing harvest records.
- Maps sowing, growing, and expected harvest stages on a full-year bed timeline.
- Summarizes active blocks, occupied beds, total bed feet, expected stems, and
  the season's harvest window.
- Adds sortable plan records, viewer-safe permissions, demo planting data,
  a versioned database migration, and visual regression coverage.

## 1.2.0 — Farm Knowledge Log preview

- Adds a desktop Knowledge Log for observations, problems, opportunities,
  actions, action owners, results, follow-up dates, confidence, and reusable tags.
- Adds grounded plain-language search that recognizes ordinary farm terms and
  month names without generating diagnoses or advice.
- Lets a lesson optionally link to a saved harvest, inventory item, order, or
  customer while preserving the lesson if the operational record is removed.
- Adds role-aware `/api/knowledge` CRUD/search routes and `/api/knowledge/tags`.
- Adds Flyway V3 so fresh and existing H2 pilot databases receive the knowledge
  tables without replacing existing farm records.
- Extends the demo profile with three realistic lessons for mildew, harvest
  timing, and market demand.

## 1.1.0 — Private-pilot hardening

The 1.1 hardening work remains part of the 1.2 private preview. No current build
is approved for customer distribution. The previous 1.0.19 artifacts are retired.

## Release safety

- One fully tested executable JAR feeds both Windows and macOS installers.
- Pull requests that change release packaging build both installers, launch the
  packaged runtime on clean Windows and macOS runners, require a successful
  database-startup marker, and preserve a private candidate bundle without
  creating a release.
- Tags, POM versions, JAR names, and installer versions must match exactly.
- A release is created only after tests, SBOM, license policy, and both native
  packaging jobs pass.
- SHA-256 values cover every installer, the JAR, SBOM, and third-party notice.
- Weka and its GPLv3 distribution issue were removed; transparent ordinary
  least-squares forecasting is implemented directly in FlowersForever.
- Runtime third-party notices are generated, bundled, and attached to releases.
- An unsigned Windows app image triggered Norton's `IDP.Generic` heuristic during
  local validation. The runtime completed its controlled startup and shutdown,
  but antivirus blocks must not be bypassed. Code signing is now a pilot release
  gate, and suspected false positives should be reviewed by the security vendor.
- Tagged publishing now depends on a valid Artifact Signing signature for both
  the Windows launcher and MSI plus a Developer ID-signed, Apple-notarized,
  stapled, Gatekeeper-approved DMG. Enrollment credentials stay in the protected
  `release-signing` environment; pull requests never receive them.

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
