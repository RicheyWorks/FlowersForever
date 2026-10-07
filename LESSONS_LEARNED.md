# Lessons learned for FlowersForever collaborators

## Shared-tree integration: farm combinations

The farm picker was updated to accept **Custom farm** in a mix while the launcher,
workspace catalog, and guide still treated Custom as a separate switch. A focused
test then exposed a null check using `Set.of(...).contains(null)` in the new
combination path; Java's immutable set throws for that query. The path now uses a
null-safe stream check. This was an integration mismatch in the shared working
tree; it is not evidence that any particular collaborator caused the issue.

For the next agent or AI working in this repository:

1. Inspect `git status --short` and the exact files you intend to touch before
   editing. The checkout contains substantial local and untracked work from
   multiple contributors. Preserve it; do not reset or clean the tree.
2. Keep one editor responsible for each file during parallel work. If another
   contributor changes that file, reread it and integrate the changes before
   writing your own patch. Do not rely on a prior snapshot of the file.
3. A farm-type change crosses `FarmCombinationDialog`, `FarmTypeLauncher`,
   `FlowerFarmGUI`, `FarmOperationSettings`, `FarmTabCatalog`,
   `WorkspaceHomeTab`, relevant tests, and user documentation. Verify the
   selection, persisted settings, visible tools, and copy as one workflow.
4. **Custom farm** may coexist with named types and may be the main type. It
   supplies shared general records, including Harvest Log, for unlisted work;
   it does not imply another crop-specific planning notebook. Named tools must
   remain available in the same mix. From the untouched rose starter, Custom
   changes the primary enterprise to **Other**; a configured flower line stays.
5. Run focused tests after integration, then the full quality gate. Run only
   one Maven build at a time in this shared checkout, since `target` is shared.

No conflicting Grok edit was identified during this pass. If a future conflict
appears, add the exact files, observed behavior, resolution, and test result
here so the next collaborator can continue without repeating the diagnosis.

Verification for this integration: the full `mvn -q -Pquality verify` run passed
2,566 unit tests and two packaged integration tests before the final starter
default adjustment. After that adjustment, all 153 focused farm-selection tests
and `mvn -q -Pquality -DskipTests verify` passed. The diff check is clean.

## GitHub README preview and shared branch drift

On October 7, GitHub's `main` still showed the older flower-only README while
the picture-rich farm-type README and several screenshots existed only as local
changes. The shared local checkout was eight commits behind its remote feature
branch, and its existing draft PR to `main` reported a merge conflict. Do not
force-push or commit the whole dirty checkout to fix the landing page. Prepare
README and image changes in an isolated worktree from the latest remote feature
tip, verify every relative image and guide link there, and keep the PR draft
until the corresponding farm-type code is integrated. A README-only merge to
`main` would describe tools absent from that branch.

The picture-first README preview is on branch
`codex/readme-marketing-preview-20261007` with draft PR
https://github.com/RicheyWorks/FlowersForever/pull/31, based on the latest
remote `codex/recover-unpushed-work` tip. It includes its linked pictures and
guides, and all relative links resolve. The PR intentionally remains draft.

## Integrating farm docs after the remote feature branch moved

The farm-type source checkout started eight commits behind the feature branch
used for the integration worktree. Four docs changed on both sides:
`docs/BACKUP_RESTORE.md`, `docs/KNOWN_LIMITATIONS.md`, `docs/PILOT_ACCESS.md`,
and `docs/USER_GUIDE.md`. Copying the local versions alone would discard the
newer in-app backup, automatic update, pilot support, and first-run guidance.
The integration copied the farm-type text, then restored those newer remote
sections in the same files. Assigned-file `git diff --check` found no patch
errors after the merge.

The farm-type guide also points to `docs/FARM_TYPES.md`,
`docs/CSRBT_FIELD_KIT_PORT.md`, and two screenshots that live in draft PR #31
(`app-berry-block-plan.png` and `app-egg-collection-form.png`). Keep the
integration and README preview changes in a coordinated merge order. A docs
link scan on the integration branch will report those paths until PR #31 is
present; the other guide screenshots were copied into the integration worktree.

The integration commit was merged into the README preview branch before
publishing the combined change, so the guide dependencies now resolve together.
On the integration commit, `mvn -q -Pquality verify` passed 2,683 tests in 282
suites, and the worker UI test script passed 15 tests. A local link scan of the
combined README and five key guides found 108 references with no missing paths.
The CSRBT `greenhouse.html` snapshot retains one upstream trailing space at
line 3473. Leave that source byte intact: its manifest checksum and the
provenance statement depend on the page matching the upstream commit.

## CSRBT checksums on a Windows checkout

The copied CSRBT pages had matching manifest hashes in the original working
tree, but a new Windows checkout converted line endings and made all 44
manifest entries appear mismatched. The Git blobs still held the correct
upstream bytes. `.gitattributes` now marks
`src/main/resources/csrbt-field-kit/**` as `-text`, preserving those bytes on
checkout. A fresh worktree from the verification branch produced 0 mismatches
and passed `FieldScienceBundleTest`. If an older worktree still holds converted
files after the attributes change, recreate that checkout or rewrite the files
from their Git blobs; a routine restore may leave clean files untouched.

## Reviewable farm-preview PR stack

GitHub rejected the original combined PR diff at 413 files (300-file display
limit). The first split still exceeded its 20,000-line diff limit for code and
tests. Keep the review stack in this order: CSRBT bundle **#33**, data and
migrations **#36**, crop notebooks **#37**, specialty notebooks **#38**, farm
chooser and shared desktop wiring **#34**, specialty tests **#39**, remaining
cross-workspace tests **#35**, then the README and guides **#31**. Each PR
targets its immediate predecessor and its diff endpoint works. The full
verification branch passed 2,683 Maven tests and 15 worker UI tests from a
fresh Windows checkout. GitHub's Release and dependency-submission jobs did
not start because of an account payment or spending-limit issue; this was an
external CI blocker, not a test result. Keep the PRs draft until the stack and
signed private release are approved.
