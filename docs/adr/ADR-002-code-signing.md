# ADR-002: Code signing for native installers

**Status:** Accepted (pilot trigger reached; enrollment pending)
**Date:** 2026-07-17
**Deciders:** Richmond (RicheyWorks)

## Context

ADR-001 ships unsigned jpackage installers (MSI, DMG). Windows SmartScreen shows "Unknown publisher" warnings and macOS Gatekeeper blocks unnotarized apps by default. During local 1.2 candidate validation on 2026-08-27, Norton also blocked the generated Windows launcher as `IDP.Generic`. The application completed its controlled release-smoke startup, migration, marker, and shutdown before the alert, but that evidence does not justify bypassing an antivirus block. Signing is now a release requirement for external pilots.

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

**Adopt signing before external pilot distribution.** The distribution trigger has been reached. Enrollment and identity validation are external account steps that remain pending, so unsigned candidate artifacts may be used only for controlled maintainer validation and must not be presented as an approved pilot release.

The chosen stack is **Azure Artifact Signing (Windows) + Apple Developer Program (macOS)**, wired into the gated `release.yml` packaging jobs (Azure's signing action for the MSI; `--mac-sign`, `notarytool submit --wait`, `stapler staple` for the DMG). Secrets live in GitHub repository secrets; no certificates are committed or stored in the release artifacts.

## Consequences

- Until adoption: README/release notes must keep documenting the unsigned-installer warnings honestly.
- On adoption: release pipeline gains external service dependencies (Azure tenant, Apple ID) — tag-day failures can now come from CA/notarization outages.
- Azure Artifact Signing requires a US/CA/EU/UK identity validation — fits current circumstances; re-check terms at adoption time.

## Action Items

1. [x] Evaluation recorded (this ADR)
2. [x] Add non-publishing Windows/macOS candidate packaging and executable smoke tests
3. [ ] Complete Azure tenant + Artifact Signing account enrollment and Apple Developer enrollment
4. [ ] Add signing and notarization steps to the gated `release.yml` packaging jobs
5. [ ] Submit the exact blocked Windows candidate to Norton for false-positive review after provenance confirmation
