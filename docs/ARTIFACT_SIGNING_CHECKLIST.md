# Artifact Signing operator checklist (Windows Authenticode)

**Goal:** signed `FlowersForever.exe`, `FlowersForeverAI.exe`, and the MSI so Windows SmartScreen stops treating the installer as an unknown publisher.

**Already in the repo (do not rebuild):**

- `.github/workflows/release.yml` job `windows-release` — OIDC login + `azure/artifact-signing-action@v2` for both launchers and the MSI, then `Get-AuthenticodeSignature` must be `Valid` with a matching publisher subject.
- `docs/SIGNING_SETUP.md` — identity, Apple, and environment rules.
- App: `AuthenticodePublisherVerifier` (on when `flowerfarm.updates.authenticode-subject` is set).

**Blocked until Richey approves spend / identity:**

| Item | State 2026-10-05 |
|---|---|
| Code-signing cert on BLACKBEARD | None (no Code Signing EKU in `Cert:\CurrentUser\My`) |
| GitHub environment `release-signing` | Missing (repo has 0 environments) |
| Azure Artifact Signing account | Not configured for this repo |
| Ed25519 update key | Present (unrelated to Authenticode) |

**Do not run the Azure create steps below until Richey says yes to the monthly cost and the legal publisher name.**

Prices change; confirm in the portal. Microsoft documents Basic Artifact Signing around **$9.99/month**. Artifact Signing **does not** work on free/trial/sponsored Azure subscriptions.

Suggested publisher display name: **RicheyWorks** (must match what identity validation and later `flowerfarm.updates.authenticode-subject` use, e.g. `CN=RicheyWorks`).

---

## A. After Richey approves — Azure (one-time)

Run in Azure Cloud Shell or a machine with Azure CLI logged in as the subscription owner. Adjust names if a name is taken (Artifact Signing account names are globally unique).

```powershell
# 1) Paid subscription only — confirm:
az account show --query "{name:name, id:id, state:state}" -o table

# 2) Resource group + provider
az group create -n rg-flowersforever-signing -l eastus
az provider register --namespace Microsoft.CodeSigning
az provider show -n Microsoft.CodeSigning --query registrationState -o tsv

# 3) Artifact Signing account (Basic)
az extension add --name artifact-signing --upgrade
az artifact-signing create -n richeyworks-ff-signing -l eastus -g rg-flowersforever-signing --sku Basic
az artifact-signing show -g rg-flowersforever-signing -n richeyworks-ff-signing -o jsonc
```

Then **only in the Azure portal** (CLI cannot finish this):

1. Open the Artifact Signing account → **Identity validation** → **Public Trust**.
2. Complete individual or organization validation (billing account name/address must match the intended publisher).
3. Wait until validation is **Approved**.
4. Create a **Public Trust** certificate profile, e.g. `FlowersForeverPublicTrust`.

Note the region endpoint (East US is commonly `https://eus.codesigning.azure.net/`). Confirm on the account overview in the portal.

## B. GitHub OIDC app (no long-lived client secret)

```powershell
# App registration + service principal
$app = az ad app create --display-name "github-FlowersForever-release-signing" -o json | ConvertFrom-Json
az ad sp create --id $app.appId | Out-Null
Write-Output "AZURE_CLIENT_ID=$($app.appId)"
# Tenant + subscription
az account show --query "{AZURE_TENANT_ID:tenantId, AZURE_SUBSCRIPTION_ID:id}" -o jsonc
```

In Entra ID → App registration → **Federated credentials** → GitHub Actions deploying Azure resources:

| Field | Value |
|---|---|
| Organization | `RicheyWorks` |
| Repository | `FlowersForever` |
| Entity | **Environment** |
| Environment name | `release-signing` |
| Name (credential) | `flowersforever-release-signing` |

Assign role **Artifact Signing Certificate Profile Signer** on the certificate profile (narrowest scope), to that app's service principal.

## C. GitHub `release-signing` environment

In the repo: Settings → Environments → **New environment** → `release-signing`.

- Restrict to tags matching `v*` (or protected version tags only).
- Require reviewer approval on the environment if the GitHub plan allows it.

**Secrets** (Environment secrets, not repo-wide):

| Secret | Value |
|---|---|
| `AZURE_CLIENT_ID` | App (client) ID from step B |
| `AZURE_TENANT_ID` | Directory (tenant) ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID |

**Variables** (Environment variables):

| Variable | Example |
|---|---|
| `AZURE_ARTIFACT_SIGNING_ENDPOINT` | `https://eus.codesigning.azure.net/` |
| `AZURE_ARTIFACT_SIGNING_ACCOUNT` | `richeyworks-ff-signing` |
| `AZURE_ARTIFACT_SIGNING_PROFILE` | `FlowersForeverPublicTrust` |

CLI equivalent (after the environment exists in the UI):

```powershell
gh secret set AZURE_CLIENT_ID --env release-signing
gh secret set AZURE_TENANT_ID --env release-signing
gh secret set AZURE_SUBSCRIPTION_ID --env release-signing
gh variable set AZURE_ARTIFACT_SIGNING_ENDPOINT --env release-signing --body "https://eus.codesigning.azure.net/"
gh variable set AZURE_ARTIFACT_SIGNING_ACCOUNT --env release-signing --body "richeyworks-ff-signing"
gh variable set AZURE_ARTIFACT_SIGNING_PROFILE --env release-signing --body "FlowersForeverPublicTrust"
```

## D. Prove it (still needs Richey's yes to tag)

1. Push a controlled stable tag `vX.Y.Z` (only when ready to publish).
2. Confirm the `Signed Windows MSI` job in `release.yml` is green.
3. On a clean PC: download the MSI → Properties → Digital Signatures, or `Get-AuthenticodeSignature`.
4. Follow [Norton / antivirus acceptance](ANTIVIRUS_REVIEW.md#release-acceptance).
5. Then set packaged `flowerfarm.updates.authenticode-subject` to the signed subject (e.g. `CN=RicheyWorks`) so auto-update also requires Authenticode.

## E. Out of scope here

- Apple Developer ID / notarization (separate enrollment, ~$99/year) — still in `SIGNING_SETUP.md`.
- Merging PR #23 / #24 — needs an explicit yes; unrelated to Azure.
- Creating Azure resources or spending money before approval.

