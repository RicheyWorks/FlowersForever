# Flowers Forever 1.0.19 release-candidate notes

This candidate consolidates the production farm workspace and adds deployment safeguards for real Windows use.

- Stable packaged data location under `%LOCALAPPDATA%\FlowersForever\data`, with explicit `FLOWERFARM_DATA_DIR` override.
- Non-destructive first-launch copy-forward from a legacy `./data` directory; the source remains available for rollback.
- Persistent local logs, exports, connector mirrors, and backups grouped beneath the resolved data directory.
- Stop-safe backup and checksum-validated restore scripts with recoverable pre-restore data retention.
- Reproducible Windows app-image/MSI build script with CycloneDX SBOM and SHA-256 manifest.
- Expanded release contracts and persistence/restart acceptance coverage.
- Cohesive worker-oriented desktop navigation, compact 1366×768 layouts, role-aware controls, keyboard access, validation recovery, and selection preservation.
- Saved-product corrections now validate the worker's complete reviewed order context, preserve the correction when another worker changes the order, and require review of the refreshed customer and handoff before retrying.
- Product removal now shows and validates the complete reviewed customer, delivery, instruction, and saved-product context; concurrent changes remove nothing and reload the exact order for a fresh review.
- Orders now exposes the exact saved-product removal review through the unique `Alt+O` keyboard shortcut, with the shortcut and safety consequence available to assistive technology.

Review `DEPLOYMENT.md`, `BACKUP_RESTORE.md`, and `KNOWN_LIMITATIONS.md` before approving a farm workstation.
