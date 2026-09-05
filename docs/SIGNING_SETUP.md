# Native signing setup

**Current plan:** Microsoft and Apple enrollment is intentionally deferred by
the maintainer for about 15 days after the September 4, 2026 discussion. The
maintainer will handle enrollment when ready. Until then, continue unsigned
private-preview engineering and defer publisher-identity and credential
requests. Enrollment is not a prerequisite for those private builds.

FlowersForever candidate builds remain private, unsigned, and non-publishing.
An external pilot release requires both tagged-release signing jobs to pass in
the GitHub `release-signing` environment. The workflow then verifies the
Windows launcher and MSI signatures, Apple Developer ID signature, Apple
notarization result, stapled ticket, Gatekeeper assessment, and packaged-runtime
startup before publication.

Do not place certificates, private keys, passwords, or Azure client secrets in
the repository. Do not test release credentials from pull-request jobs.

## 1. Choose the legal publisher identity

Decide whether each enrollment uses Richmond's verified individual identity or
a qualifying legal organization. The visible publisher must match the identity
that Microsoft and Apple validate. Apple does not treat a DBA or trade name as
an organization; organization enrollment requires a legal entity and D-U-N-S
number. Individual Apple enrollment displays the individual's legal name.

- [Microsoft Artifact Signing setup](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart)
- [Apple Developer Program enrollment](https://developer.apple.com/programs/enroll/)

Current published prices are [$9.99/month for the Microsoft Basic signing
account](https://learn.microsoft.com/en-us/azure/artifact-signing/how-to-change-sku)
and [$99/year for Apple Developer Program
membership](https://developer.apple.com/programs/enroll/). Confirm the price
shown in each provider's portal before purchasing.

## 2. Configure Microsoft Artifact Signing

1. Create or select a **paid Azure subscription** and Microsoft Entra tenant.
   Artifact Signing does not support free, trial, or sponsored subscriptions;
   see [Microsoft's subscription requirements](https://learn.microsoft.com/en-us/azure/artifact-signing/faq#can-i-use-artifact-signing-with-a-free-trial-or-sponsored-azure-subscription).
   The Azure billing account type must match the intended individual or
   organization identity validation. Confirm the legal name and billing address
   before validation; individual validation takes these details from the billing
   account. See [Microsoft's identity prerequisites](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart#prerequisites).
2. Register the `Microsoft.CodeSigning` provider.
3. Create a Basic Artifact Signing account, complete Public Trust identity
   validation, and create a Public Trust certificate profile.
4. Create a Microsoft Entra application/service principal for GitHub Actions.
5. Add a GitHub OIDC federated credential restricted to this repository and the
   `release-signing` environment. Do not create a long-lived Azure client secret.
6. Assign the **Artifact Signing Certificate Profile Signer** role at the
   narrowest certificate-profile scope supported by the account.

Use GitHub environment secrets:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SUBSCRIPTION_ID`

Use GitHub environment variables:

- `AZURE_ARTIFACT_SIGNING_ENDPOINT`
- `AZURE_ARTIFACT_SIGNING_ACCOUNT`
- `AZURE_ARTIFACT_SIGNING_PROFILE`

The workflow uses GitHub OIDC with `azure/login@v3` and Microsoft's
`azure/artifact-signing-action@v2`. It signs and timestamps the generated
`FlowersForever.exe` and `FlowersForeverAI.exe`, verifies both signatures,
packages the signed app image, then signs and verifies the MSI. The final check
requires all three valid signatures to have the same publisher subject; it
allows certificate rotation between signing operations. The timestamp uses
Microsoft's RFC 3161 service so the signature can remain valid after the short-lived signing
certificate expires.

- [Microsoft's Artifact Signing action](https://github.com/Azure/artifact-signing-action)
- [GitHub OIDC with Azure](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-azure)

## 3. Configure Apple Developer ID and notarization

1. Enroll in the Apple Developer Program.
2. Have the Apple Developer Program **Account Holder** create a **Developer ID
   Application** certificate for direct distribution. This is the role required
   for the exportable certificate used here; see [Apple's Developer ID
   certificate instructions](https://developer.apple.com/help/account/certificates/create-developer-id-certificates/).
3. Export the certificate and private key as a password-protected `.p12` file.
4. Have the **Account Holder request App Store Connect API access**, then have
   the Account Holder or an Admin create a **team API key** authorized for
   notarization. Retain its key ID, issuer ID, and `.p8` private key. See [Apple's
   API access and team-key instructions](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api).
   This workflow requires a team key because its `notarytool` commands always
   supply `--issuer`; see [Apple's notarization authentication
   syntax](https://developer.apple.com/documentation/technotes/tn3147-migrating-to-the-latest-notarization-tool).
   Apple's [API-key documentation](https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api)
   also excludes individual API keys from `notaryTool` access. The API-key type
   is separate from the individual or organization enrollment choice.
5. Base64-encode the `.p12` and `.p8` contents without adding line breaks.

Use GitHub environment secrets:

- `APPLE_DEVELOPER_ID_P12_BASE64`
- `APPLE_DEVELOPER_ID_P12_PASSWORD`
- `APPLE_NOTARY_KEY_BASE64`
- `APPLE_NOTARY_KEY_ID`
- `APPLE_NOTARY_ISSUER_ID`

Use the GitHub environment variable:

- `APPLE_SIGNING_IDENTITY` — the user/team portion of the Developer ID signing
  identity that `jpackage --mac-signing-key-user-name` should select

The workflow imports the certificate into an ephemeral keychain, requests a
Developer ID-signed DMG from `jpackage`, submits that DMG with `notarytool`,
requires an `Accepted` result, preserves the notarization log, staples and
validates the ticket, verifies the disk image, assesses the mounted app with
Gatekeeper, launches it in release-smoke mode, and deletes temporary key files.

- [Oracle `jpackage` signing options](https://docs.oracle.com/en/java/javase/17/jpackage/support-application-features.html)
- [Apple notarization requirements](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution)
- [Apple custom notarization workflow](https://developer.apple.com/documentation/security/customizing-the-notarization-workflow)

## 4. Protect the GitHub environment

Create the `release-signing` environment in repository settings and store all
values above there, not as repository-wide secrets. Restrict deployment branches
to protected version tags, keep the repository private, and require deliberate
approval before a tagged signing job receives credentials when the GitHub plan
supports that protection.

The release workflow intentionally behaves differently by event:

| Event | Result |
|---|---|
| Pull request | Unsigned Windows/macOS candidate builds and runtime smoke tests; no credentials and no publication |
| Manual dispatch | Same non-publishing candidate bundle, optionally with an exact candidate version |
| Manual dispatch with `preview_release_id` | Unsigned assets and completion evidence in a pre-created private draft; no publication or signing credentials |
| Matching stable `vX.Y.Z` tag | Protected Windows signing and macOS signing/notarization; publication only after both pass |

## 5. Enrollment acceptance checklist

- [ ] Legal publisher identity selected and recorded
- [ ] Azure Artifact Signing identity validation approved
- [ ] Azure certificate profile active and OIDC role tested
- [ ] Apple Developer Program enrollment active
- [ ] Developer ID Application certificate generated and exported
- [ ] App Store Connect notarization API key generated
- [ ] `release-signing` environment protected and populated
- [ ] Controlled stable tag proves both signed jobs without exposing secrets
- [ ] Signed MSI and both installed launchers pass the [Norton acceptance check](ANTIVIRUS_REVIEW.md#release-acceptance) on a clean workstation
- [ ] Signed/notarized DMG passes Gatekeeper on a clean Mac
