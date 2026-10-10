# Windows antivirus review

If Norton blocks FlowersForever, close the alert after saving its details and
contact RicheyWorks through the private support channel. Send the detection
name, Alert ID if shown, application version, and a screenshot. Keep protection
enabled and leave a quarantined file in quarantine. RicheyWorks handles the
build review and supplies an approved replacement installer.

## Maintainer evidence

The September 4 alert names `FlowersForeverAI.exe`, `IDP.Generic`, and
Behavioral Protection. The inspected local 1.2.0 app image has unsigned desktop
and AI launchers. This is evidence for investigation, not confirmation of a
false positive or proof that Norton caused every earlier startup timeout.

Collect metadata from the affected app image or installer with Windows
PowerShell 5.1 or newer; the report does not launch either program:

```powershell
& .\scripts\collect-windows-security-report.ps1 `
    -AppImage 'C:\path\to\FlowersForever' `
    -Installer 'C:\path\to\FlowersForever-1.2.0.msi' `
    -DetectionName 'IDP.Generic' `
    -AlertId '<ID shown by Norton, if present>'
```

Supply either artifact path or both; omit `-AlertId` when unavailable. The
script writes a uniquely named JSON report under `target` and prints its path.
It records only the named launchers and MSI: SHA-256, size, file version,
Authenticode status, publisher, certificate thumbprint, and timestamp authority.
It omits absolute input paths, farm records, environment variables, and logs.
Missing files stay marked missing; unreadable files stay marked unreadable.
It never restores quarantine, changes antivirus settings, uploads samples, or
overwrites an existing file.

An optional `-BuildCommit <commit>` records a maintainer-supplied assertion.
Verify the hashes against retained build evidence separately; the current
checkout is not proof of a binary's origin. Preserve Norton version, definition
version, alert time, and the download/build source in the private case record.

## Norton review

1. Confirm the source and build provenance; compare hashes with the retained
   private build. If they differ, investigate before describing a false positive.
2. Update Norton definitions and run its Full Scan. Norton recommends these
   checks before reporting a persistent detection.
3. After the owner authorizes sharing the sample, use the **File** tab of
   [Norton's false-positive form](https://submissions.norton.com/reportfalsepositive).
   Include `IDP.Generic`, Alert ID when available, the observed behavior, and
   build details. Norton's current instructions accept one file in a
   non-password-protected ZIP/RAR up to 500 MB. Submit the matching retained
   build file; do not restore a blocked workstation copy just to obtain it.
4. Retain the case reference and outcome. Test the exact reviewed build again
   with current Norton definitions before approving customer distribution.

The collector's JSON supports the case record; it does not replace the binary
sample requested by Norton. A rebuilt or newly signed file has different bytes
and needs its own hash and validation. See Norton's
[preparation guidance](https://support.norton.com/sp/en/us/home/current/solutions/kb20100222230832EN)
and [submission instructions](https://support.norton.com/sp/en/us/home/current/solutions/kb20090410134005EN).

## Release acceptance

Complete the [signing setup](SIGNING_SETUP.md). The tagged workflow signs both
`FlowersForever.exe` and `FlowersForeverAI.exe`, packages that app image, and
signs the MSI. All three must have valid signatures with the same publisher
subject. Certificate thumbprints can change between signing operations, so
publisher comparison does not require the same short-lived certificate.
Private candidate MSI/DMG builds and the local MSI build also package the exact
app image that passed their launcher smoke checks.

Signing identifies the publisher and protects integrity; it does not determine
whether Norton will accept the file. Microsoft also documents that a newly
signed release can still show SmartScreen reputation warnings. See
[Microsoft's signing guidance](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/deployment/use-code-signing-for-better-control-and-protection)
and [SmartScreen reputation guidance](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).

The customer acceptance check is one approved signed MSI installed on a clean
Windows workstation with current Norton definitions, followed by the normal
desktop launch and, when AI is used, the AI doctor and client connection. Record
the installer and installed-launcher hashes with the result. Publisher setup
and any unresolved antivirus detection remain release gates.
