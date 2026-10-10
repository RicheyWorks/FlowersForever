# Private draft preview builds

The public repository uses the normal **Release** workflow with an empty
`preview_release_id` to retain unsigned candidate bundles as Actions artifacts.
The private-draft path below deliberately refuses public repositories; it does
not publish a draft or release there. Candidate artifacts inherit repository
visibility and are for maintainer validation.

Use this manual path when Actions artifact storage prevents Windows/macOS
preview checks. It stores one verified JAR, its metadata, and the two tested
installers as assets of an **unpublished private draft release**. It does not
delete Actions archives, buy storage, publish a release, create a Git tag, or
use signing credentials. Customer distribution still requires the normal
signed release pipeline and platform acceptance.

## Prepare the draft

Use the private repository's maintainer account and a reviewed branch. Push all
changes first, then record its full commit SHA. Create a new draft release with:

- a unique `preview-<short-sha>-<UTC-timestamp>` tag name that does not exist;
- `target_commitish` equal to the **full commit SHA**, not a moving branch;
- `draft: true`, `prerelease: true`, and `make_latest: "false"`;
- a title/body identifying an unsigned private preview whose build is pending.

GitHub creates a new tag when the release is published. Confirm that the
proposed tag reference is absent before and after draft creation. Leave the
release as a draft throughout this process. See GitHub's
[release semantics](https://github.blog/news-insights/product-news/release-your-software/)
and [release API](https://docs.github.com/en/rest/releases/releases#create-a-release).

Create the draft with the maintainer's existing authenticated GitHub CLI/API,
then record its numeric release ID. GitHub may require the `workflow` permission
when the target commit changes workflow files; the workflow's own token cannot
grant itself that permission. The build only reads the prepared draft and
uploads assets; it never creates, edits, or publishes a release.

## Run the reviewed source

Dispatch the existing **Release** workflow on that same branch, supplying its
new `preview_release_id` input. The optional `version` input follows the normal
candidate rule: it must match the POM version with or without `-SNAPSHOT`.

```powershell
gh workflow run release.yml --ref '<reviewed-branch>' -f preview_release_id='<numeric-release-id>'
```

The selected branch must still point to the draft's exact commit. The manual
entry calls `private-preview.yml` from the same source revision. Ordinary PRs,
manual runs without a draft ID, and signed tagged releases retain their
existing Actions-artifact path. Only this explicit manual mode grants the
preview jobs permission to write private release assets.

## What the build verifies

1. Linux checks out the exact source SHA, resolves the candidate version, runs
   the full Maven verification and field UI tests, and uploads one JAR plus
   SBOMs/notices. `verified-inputs.json` records source, version, asset IDs,
   sizes, and SHA-256 hashes.
2. Windows and macOS download that same JAR by asset ID and verify its hash.
   Each builds an app image, runs the desktop startup/shutdown and AI doctor/stdio
   checks, packages the tested image, and uploads its installer and record.
   Windows requires a fresh desktop marker with valid contents and a zero exit
   code within 90 seconds; timeout cleanup stops the smoke process.
   macOS waits for each launcher to exit, reuses the same database across all
   three launches, and fails any phase that exceeds 90 seconds. A delayed phase
   produces a bounded JVM thread dump and limited lock metadata for diagnosis;
   the smoke test does not change the database's mixed-mode settings.
3. The final job checks the inputs and both installers together, generates and
   verifies `SHA256SUMS.txt`, then uploads `preview-complete.json`. Binary files
   are stored once; the completion record points to those existing assets.

The helper rejects public repositories, published or ordinary releases, an
existing tag, mismatched source/version, altered payloads, unexpected file
names, and attempts to overwrite an existing asset. It never reads farm data.
Every command and upload rechecks the private draft state. Private draft downloads use
authenticated asset APIs, and redirects do not forward the token to storage
hosts. See the [release asset API](https://docs.github.com/en/rest/releases/assets#get-a-release-asset).

## Review or recover a build

An incomplete draft is not an accepted preview. Require the final job to pass,
all six payload checksums to verify, and `preview-complete.json` to agree with
the source SHA and version before using the installers for maintainer tests.
Keep the normal [antivirus review](ANTIVIRUS_REVIEW.md) and
[signing acceptance](SIGNING_SETUP.md) requirements.

For a failed or interrupted run, keep its draft for investigation and prepare a
fresh uniquely named draft for another complete run. Do not clobber its assets
or infer completeness from an installer appearing alone. This path does not
automatically prune either failed drafts or older previews.

GitHub release assets use release storage instead of Actions artifact storage;
individual files must remain under GitHub's release asset limit. See
[GitHub release storage](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).
