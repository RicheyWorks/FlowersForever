# Pilot access

FlowersForever helps a small cut-flower team connect planting plans, harvests,
stock, customer orders, market packing, and the lessons behind the work. A
sample-farm walkthrough lets a team judge the workflow before entering its own
records. While the signed release is pending, ask your established RicheyWorks
contact for a guided walkthrough. The [five-minute sample-farm tour](../README.md#take-a-five-minute-tour)
previews what you will see. RicheyWorks presenters can use the
[presenter runbook](DEMO.md#2-five-minute-sample-farm-tour). Also review the
[current limitations](KNOWN_LIMITATIONS.md).

FlowersForever's public source is available under the [MIT License](../LICENSE).
Supported external pilots await an approved signed release. Once that release
is ready, onboarding and support are coordinated with the participating farm;
this pilot process does not restrict the permissions granted by the MIT License.

Current unsigned preview installers are reserved for controlled maintainer
validation. They are not approved for external farm distribution. The
[release signing gate](SIGNING_SETUP.md) must pass before pilot delivery.

## What an approved pilot receives after release approval

- the correct Windows or macOS installer for the approved release;
- the release `SHA256SUMS.txt` file and verification instructions;
- the plain-language user guide and farm-workstation checklist;
- initial backup setup and a restore walkthrough;
- a direct support path for onboarding and incident IDs; and
- in the app: **Help > Contact / Report a problem…** (prefilled version and computer
  details; **Open GitHub Issues…** for
  [RicheyWorks/FlowersForever](https://github.com/RicheyWorks/FlowersForever/issues/new),
  or **Copy details** — no default support email that can bounce).

## Safe delivery and installation

1. After RicheyWorks confirms the signed release is approved, obtain the
   installer from the contact already working with your farm. Do not accept
   forwarded copies or links from another farm.
2. Confirm the installer filename and version match the onboarding message.
3. Verify its SHA-256 value against `SHA256SUMS.txt`. Replace `VERSION` below with
   the approved installer version; the examples do not identify a released build:

   ```powershell
    Get-FileHash .\FlowersForever-VERSION.msi -Algorithm SHA256
   ```

   ```bash
    shasum -a 256 FlowersForever-VERSION.dmg
   ```

4. Confirm the installer has the expected verified publisher using the
   onboarding instructions. Do not install an unsigned candidate or disable
   protection to bypass a warning. Stop and contact RicheyWorks if the checksum
   differs or security software blocks the installer.
5. Before upgrading an existing farm, close FlowersForever and make a verified
   off-device backup.

## Protect farm credentials

- Do not forward farm credentials or private farm records.
- Store OWNER credentials in the farm's password manager.
- Use pilot builds on the workstations covered by the farm's acceptance checks.
- Report a lost device or exposed farm credentials to RicheyWorks promptly.

The repository's [MIT License](../LICENSE) governs the owned code and materials. Bundled third-party libraries remain subject to
their own terms, recorded in [third-party notices](../THIRD_PARTY_NOTICES.md).
