# Private pilot access

FlowersForever is private, proprietary RicheyWorks software. It is distributed
only to approved pilot farms and customers under a separate written agreement.
Repository access alone does not grant a license to use or redistribute it.

## What an approved pilot receives

- the correct Windows or macOS installer for the approved release;
- the release `SHA256SUMS.txt` file and verification instructions;
- the plain-language user guide and farm-workstation checklist;
- initial backup setup and a restore walkthrough;
- a direct support path for onboarding and incident IDs; and
- in the app: **Help > Contact / Report a problem…** (prefilled version and computer
  details, with a support email you can change).

## Safe delivery and installation

1. Obtain the installer from the RicheyWorks contact already working with your
   farm. Do not accept forwarded copies or links from another farm.
2. Confirm the installer filename and version match the onboarding message.
3. Verify its SHA-256 value against `SHA256SUMS.txt`:

   ```powershell
   Get-FileHash .\FlowersForever-1.2.0.msi -Algorithm SHA256
   ```

   ```bash
   shasum -a 256 FlowersForever-1.2.0.dmg
   ```

4. Because pilot installers are not yet code-signed, the operating system or
   antivirus software may block them or show an unknown-publisher warning. Do
   not disable protection or create a broad exclusion. Stop and contact
   RicheyWorks if the checksum differs or security software reports a threat.
5. Before upgrading an existing farm, close FlowersForever and make a verified
   off-device backup.

## Keeping access private

- Do not forward installers, checksums, repository files, or credentials.
- Store OWNER credentials in the farm's password manager.
- Use the application only on approved workstations and for the approved farm.
- Report a lost device or exposed installer link to RicheyWorks promptly.

The repository's [proprietary license](../LICENSE) and the pilot agreement
govern FlowersForever itself. Bundled third-party libraries remain subject to
their own terms, recorded in [third-party notices](../THIRD_PARTY_NOTICES.md).
