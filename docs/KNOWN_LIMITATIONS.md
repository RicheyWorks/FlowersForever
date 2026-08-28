# Private-pilot known limitations

- The Windows MSI and macOS DMG are not code-signed. Unsigned, low-reputation launchers can trigger SmartScreen, Gatekeeper, or antivirus heuristics. Validate the SHA-256 checksum, but do not override an antivirus threat block; stop and send the detection details to RicheyWorks for review.
- The supported customer database is local embedded H2, not a shared multi-workstation database. REST clients may share one running authenticated instance; they must not point separate app processes at copied database files.
- H2 upgrades use versioned Flyway migrations and Hibernate validation. Always take a pre-upgrade backup; downgrades still require restoring the matching pre-upgrade backup.
- SQLite remains a developer-preview compatibility profile and is not approved for customer data or covered by the H2 migration guarantee.
- Knowledge Log search is intentionally record-based: it matches ordinary words, tags, crops, beds, and month names, but does not infer diagnoses or generate advice. Seasonal comparisons, playbooks, reminders, and knowledge-report export are roadmap work.
- Backup and restore require the application to be closed.
- Live connectors depend on third-party APIs and credentials. Failed connector work is reported and audited, but remote-side partial completion may require reconciliation in the provider's portal.
- Irrigation falls back to Kitsap climatology when live weather is unavailable; it is operational guidance, not a soil-sensor or safety system.
- Native installers are built by release CI. Candidate CI also launches an app image on clean Windows and macOS runners before preserving either installer. Local MSI creation requires a compatible JDK/jpackage and WiX toolchain.
- The repository and release channel are private. Pilot installers may not be forwarded or redistributed.
