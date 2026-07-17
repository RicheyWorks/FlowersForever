# ADR-002: Code signing for native installers

**Status:** Accepted (deferred adoption)
**Date:** 2026-07-17
**Deciders:** Richmond (RicheyWorks)

## Context

ADR-001 ships unsigned jpackage installers (MSI, DMG). Windows SmartScreen shows "Unknown publisher" warnings and macOS Gatekeeper blocks unnotarized apps by default (users must right-click → Open). Acceptable for a portfolio project and hand-installed barn PCs; a barrier if distribution ever widens. ADR-001 action item 8 asked for this evaluation.

## Options Considered

### Windows

| Option | Cost | Notes |
|--------|------|-------|
| Azure Artifact Signing (formerly Trusted Signing), Basic tier | ~$9.99/mo | Microsoft's recommended path for apps distributed outside the Store. Open to self-employed individuals (US/CA/EU/UK) since GA — the 3-year business-history requirement was dropped. Short-lived (~3-day) auto-renewing certs, so no renewal management. GitHub Actions integration exists. |
| Traditional OV code-signing certificate | ~$200–500/yr | Requires hardware token/HSM since 2023 CA rules — awkward in CI. SmartScreen reputation still builds gradually. |
| Do nothing | $0 | SmartScreen warning; users click "More info → Run anyway". |

### macOS

| Option | Cost | Notes |
|--------|------|-------|
| Apple Developer Program + notarization | $99/yr | Only real path: Developer ID certificate signs the app/DMG (`jpackage --mac-sign` supports this natively), then `notarytool` + `stapler` in the installer workflow. No per-notarization fee. |
| Do nothing | $0 | Gatekeeper block; right-click → Open workaround must be documented. |

## Decision

**Defer purchasing; adopt when there's a distribution trigger.** Signing both platforms costs ~$220/yr (~$120 Azure + $99 Apple) plus setup time — not justified while users are the maintainer's own machines and portfolio reviewers. The trigger to revisit: any external user who is not comfortable clicking through OS warnings, or public promotion of the installers.

When triggered, the chosen stack is **Azure Artifact Signing (Windows) + Apple Developer Program (macOS)**, wired into `installers.yml` as post-jpackage steps (Azure's signing action for the MSI; `--mac-sign`, `notarytool submit --wait`, `stapler staple` for the DMG). Secrets live in GitHub repo secrets; no certificates on disk.

## Consequences

- Until adoption: README/release notes must keep documenting the unsigned-installer warnings honestly.
- On adoption: release pipeline gains external service dependencies (Azure tenant, Apple ID) — tag-day failures can now come from CA/notarization outages.
- Azure Artifact Signing requires a US/CA/EU/UK identity validation — fits current circumstances; re-check terms at adoption time.

## Action Items

1. [x] Evaluation recorded (this ADR)
2. [ ] On trigger: Azure tenant + Artifact Signing account, Apple Developer enrollment, extend `installers.yml`
