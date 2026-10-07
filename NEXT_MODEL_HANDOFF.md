# Latest direction — October 7, 2026

## Notebook save recovery, keyboard reachability, and restore ownership

Continue in `C:\Users\730ri\.codex\worktrees\farm-shipping-polish\FlowersForever`
on `codex/farm-shipping-polish-20261007`. Draft PR #41 remains based on the
README/farm preview branch (PR #31). The original checkout on
`codex/recover-unpushed-work` still contains substantial local work: preserve it.
Upstream data-safety PR #32 (`cc96266`) was merged here as `5ed463e`; unrelated
`main` licensing changes were not pulled into this preview stack.

All 20 specialty notebooks preserve the service's acknowledged save and record
identity before rendering or refreshing. A failed list read no longer lets
Discard, review, or stage actions revive stale values. Create, edit, stage,
recovery, and host-notification failures are covered across the actual forms.
Each form reveals keyboard targets while passive refresh preserves typing and
scroll position. Recovery feedback wraps above Save/Discard and reassures the
grower that the completed save is kept.

Automatic and manual restore now journal the exact retained folder before either
move and preserve recovery instructions until the restored application opens.
Automatic candidates must pass database-version validation and have a local
receipt binding their checksum to the original records directory. Live snapshots
use the original farm identity. Foreign or ambiguous legacy restore journals stop
startup without moving records. Older/PowerShell backups remain manually restorable;
missing receipts do not authorize automatic recovery from a shared backup folder.
See `docs/DATA_SAFETY.md` and `docs/BACKUP_RESTORE.md` for the user-facing limits.

The forced-close regression reproduced an external Windows file lock after the
child had exited. Its harness now waits at most five seconds for that lock only,
then checks health once and still checks every acknowledged saved record. A
damaged/unreadable result or missing record is never retried into a pass.

Next UI candidate: the Seed Fields "Planning only" section description truncates
at 850 pixels because the shared section description is a plain JLabel. Reproduce
the compact layout before changing the shared component. Native installer and
interactive acceptance remain separate from the headless Swing renders here.

## Combined farm navigation and notebook draft decisions

Work continues in the isolated `farm-shipping-polish/FlowersForever` worktree,
on `codex/farm-shipping-polish-20261007`, based on the complete README/farm
preview branch at `c948c16`. The original checkout's local work is preserved.

The actual catalog for all 25 farm types exposed a startup exception after
26 navigation mnemonics. Additional workflows now use ordinary keyboard
navigation and Workspace links without claiming duplicate shortcuts. Mushroom,
cranberry, and pumpkin tools join the correct combined-farm navigation group.
Long farm-mix labels wrap in the launcher and Workspace home; home content
tracks the viewport width and its guidance grows with the available width.

All 20 specialty notebooks now bind a discard confirmation to the reviewed
draft, record identity, saved-row snapshot, selection, permissions, and
load/save state. A change during the modal question keeps the draft and asks
the grower to review again. This prevents a completed refresh from loading an
obsolete selected record after discarding another draft.

Focused verification passed 201 cases, including 100 notebook decision cases,
eight rendered light/dark cases at 850 and 1100 pixels, and the actual combined
catalog's navigation beyond 26 tabs. Corresponding suite floors are recorded
in `config/harness-test-floors.json`. Rendered examples are under
`target/ui-previews/farm-entry`.

The clean `python -B scripts/verify_harness.py --isolated` run passed 2,802
tests and two packaged-JAR checks on Temurin 22.0.2 (Java 17 compilation target).
Line coverage is 86.11%, branch coverage 69.39%; all coverage gates pass and
Checkstyle/SpotBugs report zero findings. Source digest:
`e60089066f0d5be41cc40dbe09ef3cf68964f452146890b31300f190f6ded947`.
Evidence is `dist/harness-evidence/latest.json`, with the complete log at
`dist/harness-evidence/farm-shipping-quality.log`. Native installers were not
rebuilt. GitHub's upstream check was still unable to start because of account
payment or spending-limit settings; do not treat that as a failed test.

## Previous direction — September 26, 2026

## Entry and review dialog polish

Knowledge Log entry/edit/retry forms and Season Planner add/edit/succession/retry
and read-only draft forms use the shared `FarmUi.dialogScroll` wrapper. Dialog
content fits its owner window and usable screen with room for native title bars
and option buttons. Forms track viewport width and preserve their natural height,
with keyboard reveal, comfortable inputs, wrapping guidance and padded notes.
Changed planting comparisons also use the wrapper. Existing fields, validation,
raw draft values, permissions and stale-decision checks remain intact.

CRM confirm/complete/cancel/delete reviews preserve the full customer identity in
larger wrapping headings. Their existing copyable consequence readers remain
scrollable, while the actual dialog content is bounded to its owner.

New rendered contracts cover lesson/planting forms with 660x500 and 1050x700
owners in both themes, keyboard reachability of every input, and persistent
OK/Cancel controls. Eight CRM cases cover long literal-markup identities and
the final reviewed consequence at 580x340. The existing form/workflow/safety
checks passed during focused verification. Final clean isolated Java 21 verification
passed 1,948 tests plus two packaged-JAR checks, with all 182 suite/phase floors met
and zero failures/errors/skips. Coverage is 87.28% lines and 70.46% branches;
Checkstyle and SpotBugs report zero findings. The tested source digest is
`c7b68305a48f6b8e261dc730e3775354eada93a8ae333b96284b53dedb00730c`.
Evidence is `dist/harness-evidence/latest.json`; the clean log is
`dist/harness-evidence/ui-polish-pass4-quality.log`. Lesson and planting dialog
previews are saved under `docs/images` and linked from `docs/GUI_ARCHITECTURE.md`.
Native installers were not rebuilt or accepted interactively.

One additional source-only candidate remains for a future pass: the Rose
Visualizer saved-ruleset picker sizes its selector from filenames. A very long
custom name may widen that dialog; reproduce with a compact render before changing
its layout. Reports use the native save chooser, and no comparably clear compact
issue was found in the existing four-row inventory editor during this audit.

## Reference workspace polish

Plant Guide keeps readable table columns and gives profiles and source attribution
their own adjustable reading space. The source selector no longer determines the
whole pane's width. Rose Guide separates header actions, wrapping sample status,
and filter rows. Event Services preserves catalog/detail and brief space, uses
two rows of comfortable labeled fields, and separates feedback from actions.
Irrigation uses larger padded text, viewport-sized checklist labels, and usable
plan/reference heights. These workspaces scroll vertically and reveal keyboard
targets. Pricing now selects the correct product after either table sort order.

The existing visual fixture covers 850x500 and 1050x700 layouts, light/dark themes,
and keyboard reveal of reference readers, rose rows, irrigation notes, and event
brief/actions. Functional pricing checks exercise ascending and descending sorts;
event checks retain catalog visibility and verify the scrolled action is reachable.
All eight focused checks and the final clean isolated Java 21 verification passed:
1,936 tests plus two packaged-JAR checks, all 180 suite/phase floors met, and zero
failures/errors/skips. Coverage is 87.25% lines and 70.44% branches; Checkstyle and
SpotBugs report zero findings. The source digest is
`504b09c54a8e417f5488b80eeb1712e030a65a57b489af6ef605fa783b8807ff`.
Evidence is in `dist/harness-evidence/latest.json`; the full log is
`dist/harness-evidence/ui-polish-pass3-quality.log`. Plant Guide and Event Services
previews are saved in `docs/images` and linked from `docs/GUI_ARCHITECTURE.md`.
Native installers were not rebuilt or accepted interactively.

The previously identified fixed-size Knowledge Log and planting dialogs are
addressed in the entry/review pass above, with dedicated compact renders.

## Further desktop polish

CRM customer forms use natural label widths, comfortable input heights, and
separate action rows. Refresh/document statuses stay in a wrapping footer so
compact page titles remain readable. Orders retain useful list and entry heights,
with filter statuses separated from actions and vertical workspace scrolling.

Market Day preserves pick-list/load-out space and wraps customer checkboxes and
status messages to the available width. Export status has its own row. Activity
History preserves timeline/inspector space, uses larger padded detail text, and
keeps recovery guidance separate from actions. Season Planner preserves usable
workspace heights and a fixed status footer; records retain readable column widths
and full-cell tooltips. Harvest Outlook preserves chart and evidence-table heights
and wraps insight text to each card's width. The rose preview fits its pane while
controls align left.

Keyboard reveal now walks all enclosing viewports, including nested Market Day
and CRM workspaces; table/text readers retain their internal position. Focused
tab strips reveal their selected tab header. Existing data, authorization,
confirmation, draft, and export semantics remain unchanged.

Final clean isolated Java 21 verification passed 1,936 tests plus two packaged-JAR
checks, with all 180 suite/phase floors met and zero failures/errors/skips.
Coverage is 87.26% lines and 70.49% branches; Checkstyle and SpotBugs report zero
findings. The source digest is
`363dc1af9bd21629e1d049a85862db7bf7caeca373e7f3921c674fc3feece59c`.
Evidence is in `dist/harness-evidence/latest.json`; the full log is
`dist/harness-evidence/ui-polish-pass2-quality.log`. Compact light/dark renders and
keyboard-revealed controls were reviewed, and the focused visual fixture passed.
README screenshots now show the refreshed Market Day, Outlook, and Season Planner
layouts. Native installers were not rebuilt or accepted interactively.

## Latest completed work: desktop layout polish

Dashboard quick actions keep a comfortable height in both large and compact
windows. The work and alert cards stack on narrower screens; vertical scrolling
keeps priority actions and tools reachable without squeezing them. Priority and
daily-sheet status have dedicated rows, and the refresh timestamp stays visible
in the footer.

Knowledge Log uses readable table widths, horizontal scrolling, escaped full-cell
tooltips, a balanced initial list/detail split, and larger padded lesson text.
Compact windows preserve a 300px lesson workspace with vertical scrolling instead
of shrinking the reader to a few lines. Its wrapping search status remains fixed.
Dashboard and Knowledge Log reveal keyboard-focused controls through the enclosing
viewport. Reports date fields use the shared comfortable entry styling, with
aligned guidance and less crowded actions. Existing data, permissions, draft,
search, and export behavior is unchanged.

The 232 focused design-system, visual, knowledge, and priority-workflow cases
passed. Light/dark renders were inspected, including 850x500 content areas,
keyboard-revealed actions, and the lesson reader. These rendered component checks
do not substitute for native installer or pointer/keyboard acceptance.
Local images are in `target/ui-previews`; the focused log is
`dist/harness-evidence/ui-polish-render.log`.

The final clean isolated Java 21 verification passed 1,936 tests and two
packaged-JAR checks, with all 180 suite/phase floors met. Coverage is 87.25%
lines and 70.48% branches; Checkstyle and SpotBugs both report zero findings.
The source-bound evidence is `dist/harness-evidence/latest.json`, and the full
log is `dist/harness-evidence/ui-polish-quality.log`. The tested source digest
is `1a5a797bcd4296cc7d8e9fbc5e712c4919ab764e6312df3ab1a4e494f2ac5f3b`.
README Dashboard and Knowledge Log screenshots reflect the refreshed layouts.

## Latest completed work: stable desktop selectors

Completed the interrupted selector update for Swing plugin 1.9. Snapshots prefer
globally unique component names and retain readable labels in `HarnessControl.label`.
Button labels follow visible text even when an explicit accessible name stays fixed.
Current human-label aliases still work when unique. Typed actions now reject
cross-control ambiguities using the same address space as discovery. A stable
selector does not bypass fresh-context, deadline, access, or enabled-state checks.

New coverage exercises 17 selector lifecycle cases and five real Season Planner
workflow/guard cases, including changing save labels and retained-draft counts.
Existing table and file-dialog assertions now distinguish stable selectors from
readable labels. The new suites' AssertJ type-inference and Mockito varargs issues
are fixed. Both new suites have verification floors. All 27 focused selector,
workflow, plugin, and file-dialog cases passed.

Final clean isolated quality verification on Java 21.0.4 passed 1,936 tests plus
two packaged-JAR checks, with zero failures/errors/skips. All 180 suite/phase
floors passed; coverage is 87.23% lines and 70.45% branches. Checkstyle and
SpotBugs have zero findings, and both SBOM formats were generated. The 72 Node
worker/preview/launcher/packaging checks and 18 Python verifier checks also passed.
Logs: `dist/harness-evidence/sync-quality.log`, `sync-node-tests.log`, and
`sync-python-tests.log`. Source/report evidence is `dist/harness-evidence/latest.json`.

Continue on `codex/recover-unpushed-work` / draft PR #21. The branch includes all
of `main`; local and remote main were both `54d41f7` during this sync. GitHub Actions
is separately blocked before execution by account payments/spending limits; local
success does not establish remote CI or native installer acceptance.

The recovery-output discovery work and other CSRBT ideas below remain follow-ups.
The September 22 research describes the source as it stood before this change.

## Latest research: CSRBT harness ideas

The owner also requested another look at the CSRBT ecosystem. The updated
`docs/HARNESS_ECOSYSTEM.md` compares CSRBT's clean local/GitHub HEAD
`80c3f2c250fb43e67b6970d49c1be58c95d30136` (eight commits after the prior review)
with FlowersForever `4c74375`, using pinned source references and concrete
acceptance criteria. This is research/documentation; no new harness feature or
CSRBT sweep result is claimed.

Stable advertised selectors are now implemented above. The next harness follow-up
is observable recovery outputs through the public gateway: discovery still filters
ambiguous supplemental outputs. Preserve context-token and sensitive-read protections
while auditing those cases. Other scoped ideas: targeted mutation proof with current
catalogues, Java/Node/Python evidence scope, and semantic persistence/export checks
with controlled clocks. Existing floors, hashes, deadline/replay handling and
semantic journeys are credited rather than proposed again.

This adds a harness workstream alongside the Knowledge Log draft candidate below.
The retained unknown-execution/data-version/diff/cross-transport protocol backlog
still needs separately defined semantics. Read-only CSRBT review did not reproduce
its committed browser/mutation results or launch any sibling-engine checks.

## Latest completed work: Season Planner recovery

Season Planner now retains exact raw planting forms independently for saved-record
edits and new planting drafts. Failed saves, rejected stale decisions, refreshes,
other records, and unrelated successful writes do not replace them. Add resumes a
new draft for the selected season; Review drafts lists all retained work and offers
review or confirmed single-draft discard with Keep working / Keep draft defaults.
Cancelled retry forms retain corrections, including invalid and unnormalized text.
Missing-record, other-season, failed-refresh, and VIEWER recovery opens copyable
read-only details. A failed edit retains the original full record snapshot; changed
refreshed values require a current-versus-draft comparison and explicit Use draft
values before the ordinary form review. Both approvals recheck current context.

All planting mutation entry points check role/host access, running work, and failed
reads. Form/delete decisions capture refresh/write/modal revisions, season, and the
full selected record; targeted actions also capture selection revisions. Refreshes
requested during writes coalesce behind the result. Acknowledged create/update/delete
results remove only their applicable drafts and remain visibly saved when follow-up
reads fail. Host callbacks cannot hide the write outcome or prevent the independent
local reload. Failed reads lock writes until fresh loading succeeds. The shell now
includes retained planting drafts and pending mutations in exit/sign-out decisions.

Drafts remain session-only. Cancel of a fresh ordinary form still abandons that form.
The new snapshot checks do not add database record-version preconditions against
another client writing after review. No native app/installer was built or launched.

Focused suites passed: 73 decision cases,
27 recovery cases, 52 shell cases (14 new), and the 69 existing user journeys plus
planner/visual contracts. The compact planner render was inspected. Test floors are
updated to match. Final log: `dist/harness-evidence/season-planner-quality.log`;
source/report evidence: `dist/harness-evidence/latest.json`.

Full clean isolated verification passed 1,914 tests plus two packaged-JAR checks,
zero failures/errors/skips, and all 178 suite/phase floors. Coverage is 87.24% lines
and 70.47% branches; Checkstyle and SpotBugs report zero findings. Both SBOM formats
were generated. A separate `--check` confirmed source/report consistency for
`4b040f43eb7dd18d379c326fbd456fc532b6734f51a8748e1b56c6b396a3756b`.

Continue on `codex/recover-unpushed-work` / PR #21 with local/GitHub synchronization.
Check the latest Actions annotations for the separate account payment/spending-limit
block before describing remote CI. Native acceptance remains separate.

Next concrete candidate: `KnowledgeLogTab.startSave` still puts failed new/edit
lesson drafts into one `retrySaveId` / `retrySaveDraft` slot. Fail an edit to lesson A,
then submit lesson B: B replaces A's retained draft regardless of its outcome.
Bound the next pass to independent lesson/new drafts, exact raw values, review/discard
and shell warning integration, with an A/B replacement regression. Existing user
journeys cover only one failed new-lesson retry.

Historical continuation notes follow.

## Latest completed work: unfinished customer details

The owner's next continuation addressed the customer-only draft gap below.
CRM now keeps an exact raw-field/edit-identity clean baseline. Failed saves stay
unfinished even when the submitted values equal that baseline. Explicit load,
clear, and acknowledged save/deletion reset it; search, refresh, and linked
account review preserve the existing form and baseline.

Load selected, double-click, and Clear form ask **Discard customer changes?**
only when entered work would be lost. **Keep editing** is the default; affirmative
actions are **Discard and load** / **Discard and clear**. Cancel/X preserves all
fields and the account being edited. The approval captures raw form, form/write
revisions, refresh-failure state, and the complete selected load target. It is
rechecked after the dialog, including access and pending work. Newer nested
actions or changed-then-restored fields invalidate an older decision.

Customer-only drafts join the existing shell close/sign-out warning. A loaded
unchanged account remains clean; pending writes retain **Exit before result**
wording. No shell production change was needed. Drafts remain session-local and
are not restored after exit. This does not add database record-version checks
against another client changing a customer while its editor is open.

New regression suite: `CrmCustomerDraftSafetyTest` passed all 53 cases. Eighteen
new real-CRM shell cases bring `ProductionReadinessUiTest` to 38; all passed in
its isolated focused run. A draft test's initial broad exit assertion was narrowed
to preserve an independent order warning after customer deletion; no production
failure remained. Source edits are frozen and all agents have finished editing.
Final clean isolated verification log: `dist/harness-evidence/customer-drafts-quality.log`;
evidence: `dist/harness-evidence/latest.json`.
Final clean isolated verification passed 1,800 tests plus two packaged-JAR checks,
zero failures/errors/skips, and all 176 suite/phase floors. Coverage is 87.11% lines
and 70.24% branches; Checkstyle and SpotBugs report zero findings. Both SBOM formats
were generated. A separate `--check` confirmed source/report consistency for
`6b233d2afee7b84063381d433b0991eb886e7194e40d8b9812ffaf42d7b014d7`.

Continue on `codex/recover-unpushed-work` / PR #21 with local/GitHub synchronization.
Native apps/installers were not built or launched. Check the latest GitHub Actions
annotations for the separate payment/spending-limit block. The customer draft
replacement/exit candidate from the previous pass is resolved.

Next concrete candidate: `SeasonPlannerTab` still has a single retry slot, lacks
shell exit protection, and does not recheck access/record state after planting
edit/delete dialogs. Failed refreshes re-enable writes, and host-refresh exceptions
after acknowledged writes need explicit saved-result recovery. Review that workflow
with focused stale-dialog, failed-read, and retained-draft tests before extending it.

Historical continuation notes follow.

## Latest completed work: customer and packing action decisions

The owner's next continuation reviewed other desktop action boundaries after
inventory draft recovery. Customer Enter could submit while its button was
disabled after a failed/running CRM refresh, and double-click loading could
overwrite a form during a pending save. Customer action entry points now share
access, busy, queued/running refresh, and saved-result guards; writes also stay
blocked after a failed CRM refresh until it succeeds. Exact entered fields survive
blocked paths.

Customer deletion uses the injected dialog and captures all displayed account
fields, selection, edit identity, raw form, and a write revision. It rechecks
these plus host/current role before dispatch. Unchanged successful refreshes
remain valid; newer writes invalidate older approvals even if they failed.
The confirmation explains loss of the loaded account's unsaved form on deletion.

Market Day captures an immutable reviewed plan and exact packed-check signatures
with a decision revision. Scope, check, role, refresh, export, and submission
changes invalidate old modal approvals. Direct submission shares the freshness,
access, pending-result, and packing checks. A stale service preflight rejection
now actually locks completion until a successful rebuild; failed reads and role
changes cannot bypass it. Refreshes requested during completion coalesce and
wait for its result. Service preflight and transactional per-order safeguards
remain in place; this is not a general database-versioning change.

Focused verification passed 38 new CRM cases, then 48 packing cases (22 new and
26 existing). New suites are `CrmCustomerDecisionSafetyTest` and
`MarketDayCompletionDecisionTest`, with floors 38 and 22. Source edits are frozen
and agents have finished editing. Full clean isolated log:
`dist/harness-evidence/action-decisions-quality.log`; evidence:
`dist/harness-evidence/latest.json`.
Final clean isolated verification passed 1,729 tests plus two packaged-JAR checks,
with zero failures/errors/skips and all 175 suite/phase floors met. Coverage is
87.08% lines and 70.15% branches; Checkstyle and SpotBugs report zero findings.
Both SBOM formats were generated. A separate `--check` confirmed source/report
consistency for `c72e1d0bc1997d580844af053bdc5d1afd5c7a6fb8335653b75d40307d2510ff`.

Continue on `codex/recover-unpushed-work` / PR #21 with local/GitHub synchronization.
No native installer/app was rebuilt or launched. Check the latest GitHub Actions
annotations for the separate account payment/spending-limit block.

Concrete follow-up candidates found during this pass, outside its action-guard scope:

- Customer-only draft loss: `CrmTab.applicationExitWarning()` omits the customer
  form. Loading another account or Clear form also replaces its unsaved fields
  when no operation is running. Add clean raw-form/edit-identity baselines,
  explicit replacement decisions, and close/sign-out coverage without treating an
  unchanged loaded account as a draft. Preserve failed saves as unfinished work.
- `SeasonPlannerTab` still uses a single retry slot, lacks shell exit protection,
  and does not recheck role/record state after planting edit/delete dialogs.
  Failed refreshes re-enable writes, and host-refresh exceptions after an
  acknowledged write are not handled as saved-result recovery. Treat this as a
  separate bounded workflow review with failure and stale-dialog regressions.

Historical continuation notes follow.

## Latest completed work: per-product inventory drafts

The owner's "next" continued the retained-draft limitation identified in the
previous pass. Count and edit drafts now use canonical product-ID/operation
keys, preserving other products and the other operation when one save succeeds.
Failed/rejected work survives search, refresh, renames, missing products, and
same-name replacements with different IDs. Raw edit fields are preserved exactly.
Idless legacy entries use name/category/unit as their fallback identity.

**Review retained drafts…** lists each operation and product ID, shows copyable
entered values and current saved details, and opens the exact product without
submitting changes. Missing products remain inspectable. A separate confirmation
discards exactly one local draft; VIEWER can review/discard local work. Reopened
drafts retain corrections on Cancel/X, while cancelling a new entry still closes
it without retention. Saved-baseline changes and original count units are visible
before retry. Duplicate-name conflicts stay with their edit and disappear when
the requested name changes.

Reference checks prevent stale modal Save/Discard decisions from overwriting
newer retained work. Deleting a product describes its draft consequences and
clears only captured drafts after acknowledgement. Exit warnings cover all
retained work, including filtered/missing products. Drafts remain session-local;
they are not persisted through restart. Desktop snapshot checks still do not
provide transactional record-version preconditions against other clients.

New suites contain 18 per-product/nested-modal cases, 12 review/discard/conflict
cases, and four light/dark rendered cases (580×300 and 700×380). The first focused
run passed 83 cases; the next recovery/render run passed 50. The final two conflict
cases are included in the clean full run. Screenshots are under
`target/ui-previews/inventory-drafts`. Verification log:
`dist/harness-evidence/inventory-drafts-quality.log`; evidence:
`dist/harness-evidence/latest.json`.
Final clean isolated verification passed 1,669 tests plus two packaged-JAR checks,
zero failures/errors/skips, and all 173 suite/phase floors. Coverage is 86.99% lines
and 69.96% branches; Checkstyle and SpotBugs report zero findings. Both SBOM formats
were generated. Source hash:
`6bc40b12b5b93dbc070c75988508dd8c91f4840b13eb4cf3d466e5166fe500c1`.
A separate `--check` confirmed matching source/report hashes.

All agents have finished editing. Continue on `codex/recover-unpushed-work` / PR #21.
The prior single-slot draft candidate is resolved. Next shipping review can focus
on another desktop workflow's saved-result/draft boundaries; no further concrete
blocking defect was found in the changed inventory paths. Native platform/signing
acceptance and GitHub billing remain separate; check the latest run annotations.

Historical continuation notes follow.

## Latest completed work: inventory saved-result recovery and release isolation

The owner's "continue flowers forever" resumed the two concrete handoff
candidates below. Inventory counts, edits, and deletions now keep acknowledged
writes distinct from failed readback. A saved action cannot become a retry draft
because inventory reload or host refresh failed. Count/edit/delete actions require
a fresh successful inventory read; selection, search, and role updates preserve
the warning. Refreshes requested during writes queue until the write resolves.

Modal decisions recheck the reviewed product fields, current selection, and access.
Rejected count/edit decisions retain their entered values for the reviewed SKU.
Count-next waits through the host's inventory refresh and rechecks identity and
visible workspace before opening. Inventory drafts and pending results now join
the shell's close/sign-out guard. These are desktop checks, not database version
preconditions against concurrent clients.

Compact renders found a collapsed stock table beneath the new wrapping status.
The final layout gives status/report feedback dedicated rows, a vertically
scrolling workspace, a usable table height, and separate stock/report action rows.
Four light/dark cases at 850×500 and 1180×760 verify complete messages and real
scroll access to controls/table. Baseline renders are preserved in
`dist/harness-evidence/inventory-recovery-baseline`; final images are in
`target/ui-previews/inventory-recovery`.

Release tooling uses fresh `target/release-<version>-<GUID>` and
`dist/release-<version>-<GUID>` folders instead of clearing shared outputs. Local,
candidate, private-preview, and signed Windows builds share a fresh-marker plus
zero-exit desktop gate with 90-second timeout and process cleanup. Thirteen new
simulated packaging/desktop checks and seventeen existing launcher checks passed.
No native application/installer was launched or built.

Java verification adds 33 cases: 14 recovery, 10 modal/count-next, four visual,
and five shell cases. The first focused run passed 81 tests. A second 102-case
run reproduced two compact-layout failures; after the correction all 33 targeted
dialog/render/UI checks passed. The full verification result is recorded in
`dist/harness-evidence/inventory-quality.log` and `dist/harness-evidence/latest.json`.
Final clean isolated verification passed 1,635 tests plus two packaged-JAR checks,
zero failures/errors/skips, and all 170 suite/phase floors. Coverage is 86.97% lines
and 69.89% branches; Checkstyle and SpotBugs report zero findings. Both SBOM formats
were generated. Source hash:
`93824fd73331d65eb787007eaf7897a8e83b35bd20629e4f886bb4b7f6ee33d0`.
A subsequent `--check` confirmed source/report hashes still match.

Continue on `codex/recover-unpushed-work` / PR #21. All agents have finished editing.
Earlier no-commit/no-GitHub instructions remain superseded by the synchronization
direction recorded below.

Next concrete candidate: inventory still stores only one failed count and one
failed edit draft. An unrelated successful save now preserves the retained draft,
but a second different product's failed/rejected attempt can replace the first.
Use per-product retention or an explicit replacement decision, with corresponding
exit-warning and selection/refresh tests. Signing/platform acceptance remains
external; check the newest GitHub run annotations for the billing limitation.

Historical continuation notes follow.

## Latest completed work: safer Harvest entry and native readiness

The owner's "next" continues shipping readiness with human-interface priority.
Harvest now protects complete quick-entry drafts before load/copy/clear, keeps
failed and copied entries dirty, suppresses crop-preset writes during refresh,
and blocks double-click replacement during pending mutations. Modal decisions
recheck captured draft/record identity. Sample batches ask before replacing lines;
delete consequences mention local edits. The close guard includes Harvest, and
pending CRM/packing/Harvest/Studio operations use exit-before-result wording.

Compact renders exposed hidden inputs and history, squeezed fields, and clipped
filters/status. The final layout uses two-column quick entry, full-width wrapped
status rows, reachable vertical workspace scrolling, explicit filter rows, and
readable history columns. README's real harvest screenshot is updated.
An additional rendered new-SKU/queued-refresh/report-error state reproduced header
overflow; the corrected header separates title/actions, wrapping status rows, and
conditional inventory setup. All four render cases now include that state. The
first full run was deliberately interrupted for this fix; the final run below
restarts clean and is the only full evidence to accept for this pass.

New checks: 16 Harvest draft cases, four rendered theme/size cases, and six shell
exit cases. The broad focused run passed 138/144; the six layout/legacy-layout
failures then passed targeted reruns. All existing counts remain protected;
test floors now include 167 suite/phase entries. Final clean isolated verification
passed 1,602 tests plus two packaged-JAR checks, zero failures/errors/skips, and
all quality gates. Coverage is 86.91% lines / 69.72% branches. A subsequent
`--check` revalidated source/report hashes. Log:
`dist/harness-evidence/harvest-quality.log`; evidence:
`dist/harness-evidence/latest.json`. All build processes and agent edits are done.

Native helpers no longer hardcode 33 tools. Both validate the doctor's required
capabilities and missing-operation list. Seventeen simulated launcher/contract
tests passed, including eleven Windows JSON-report scenarios. No installer or
native app was built/launched. GitHub billing remains a separate blocker.

Next concrete candidates from this review:

- Inventory stock-count and item-edit workers commit, then read all items in the
  same worker result. A readback failure can report "not saved" and retain a retry
  although the write committed. Separate saved outcome from refresh failure and
  gate subsequent affected writes on fresh inventory. See `InventoryTab`'s
  `recordStockCount`, `startItemEditSave`, and associated failure handlers.
- Local `build-release.ps1` deletes all `dist` and omits desktop smoke; Windows
  CI desktop smoke checks its marker without checking clean process exit. Keep
  any new packaging output isolated from evidence and existing native binaries.
- GitHub's eight default-branch dependency alerts already have patched versions
  in this branch's verified BOM; do not blindly bump dependencies to fix stale
  default-branch alerts. Signing/platform acceptance and billing remain external.

Continue on `codex/recover-unpushed-work` / PR #21. This pass is committed and
pushed with the updated guide, audit, README screenshot, and PR description.
Earlier no-commit instructions remain superseded.

## Latest completed work: README marketing and Production Studio shipping usability

The owner requested stronger README marketing, then the next significant shipping
issue, emphasizing human-interface polish. README now leads with grower value,
two clear CTAs, actual app screens, and a shorter connected-work story. It retains
private-preview/distribution limits and labels the illustrative hero. A new real
Production Studio screenshot shows the current field-work editor with sample data.

The selected shipping issue was Production Studio: compact layouts hid forms and
tables, while refresh/navigation replaced frost settings and task drafts. It now
has separate Plan plantings/Field work views, full-width scrolling forms, readable
tables, human labels, field validation, wrapped status messages, and selected-task
context next to actuals. Discovery now has 36 leaf routes.

Full refresh, frost reload, and actuals save run off the EDT with captured inputs.
Generation/query checks discard superseded reads. Frost baselines and task-ID
draft maps preserve unsaved edits; changed/missing tasks and failed/interrupted
reads require review before further task writes. Explicit reload/discard actions
and Studio exit protection complete the recovery path. Successful writes remain
clearly saved if follow-up reads fail. Exiting mid-save cannot imply rollback.
Other template, recipe, and handoff writes remain synchronous.

Independent review corrected two premature task-write enablement paths. Visual
tests found and corrected actual Swing label width inflation collapsing a table.
Focused verification passed 42 tests, including 15 new safety cases and four
rendered cases covering eight views at two sizes in both themes. The final visual
cases also assert complete wrapped recovery messages and task identity. Test
floors now include 165 suite/phase entries. Full clean isolated quality verification
passed 1,576 tests plus two packaged-JAR checks, with no failures, errors, or skips.
Line coverage is 86.88%, branch coverage 69.62%, and all quality gates passed.
A subsequent `--check` confirmed source/report evidence still matches.
Log: `dist/harness-evidence/ship-ui-quality.log`; evidence:
`dist/harness-evidence/latest.json`. Agents have finished editing.

Keep using `codex/recover-unpushed-work` and PR #21. This pass does not rebuild
native installers or resolve the GitHub account's CI billing block.

## Latest completed work: harness and application wiring gaps

The owner's follow-up was: "now that the machine and github is updated. work on
wiring gaps". This pass connects MCP resource discovery/read to the authenticated
gateway manifest and live observations, including observation-only plugins.
Sensitive snapshots use current disclosure policy; current-protocol reads are
private with zero cache lifetime. Legacy and current envelopes remain supported.

Farm-data is now version 1.10 with 38 tools (68 combined with Swing). New actions
are `get-season-settings`, `list-order-handoffs`, `count-inventory`,
`complete-order-handoff`, and `get-operation-receipt`. Counts and handoffs reuse
the worker service's reviewed quantity/token checks and durable receipts shared
with the phone API. Worker writes retain their own transaction/lock boundary.
Gateway request replay and `output.operation.replayed` are distinct; after gateway
cache loss, execution requires current mutation permission and honors any supplied
deadline. Explicit receipt lookup requires sensitive-read permission.

Skipped-task preview uses a pure state validator repeated under lock on commit.
An initial transactional preflight caused a caught conflict to mark the preview
rollback-only; focused tests caught and corrected it. Receipt-backed imports still
recover after a task is subsequently skipped. Swing rechecks expiry at the queued
EDT callback before starting work. Its frozen accepted receipt remains replayable;
`action-status` reports the eventual expiry failure. Started callbacks are not
interrupted. Production Studio refreshes dependent cost/availability/handoff views
without replacing unrelated drafts and distinguishes saved writes from failed
follow-up reads. Doctor readiness requires a usable directory and all 38 tools.

This pass adds 52 Java cases. Focused verification passed 102 cases after the
transaction correction and a JSON test wire-comparison correction. Independent
reviews found no remaining blocking issue. Final online
`python -B scripts/verify_harness.py --isolated` passed 1,557 tests plus two
packaged MCP checks with no failures/errors/skips. All 163 suite/phase floors,
Checkstyle, SpotBugs, and coverage gates passed (86.72% lines / 69.39% branches).
Both SBOM formats were generated. Eighteen Python evidence-gate tests passed.
Source/report hashes are in `dist/harness-evidence/latest.json`; the full log is
`dist/harness-evidence/wiring-quality.log`. All agents are done editing and the
verification process has exited successfully.

Use the existing `codex/recover-unpushed-work` branch and PR #21. Source changes
belong in the same local/GitHub continuation; earlier no-commit instructions remain
superseded. Native installers have not been rebuilt in this wiring pass.

## Latest completed work: GitHub sync and CSRBT-inspired harness hardening

The owner explicitly requested local/GitHub synchronization and continued harness
work informed by the CSRBT ecosystem. This supersedes the older entries' instruction
to leave work uncommitted or avoid GitHub. The 88 previously dirty/untracked files
were preserved, verified (1,394 tests plus two packaged MCP checks), committed as
`d615837`, and pushed to `codex/recover-unpushed-work`, updating existing PR #21.
The branch already contained every commit from `origin/main`.

The new continuation adds optional deadlines across REST/MCP/embedded commands,
bounded/redacted JSON-RPC protocol errors, strict mapped numeric punctuation and
exponent/range handling, and 111 Java regressions. CSRBT local and GitHub HEAD
matched `2a80194777f159cc4111467a282c199ff174effe`; source links and next-step
acceptance criteria are in `docs/HARNESS_ECOSYSTEM.md`.

New `scripts/verify_harness.py` performs clean quality verification, checks
`config/harness-test-floors.json`, and stores source/report hashes and per-class
counts in `dist/harness-evidence/latest.json`. Eighteen Python fault-injection
tests pass. The JDK 17 CI job uses this entry point; JDK 21 remains advisory.
Use `--isolated` locally: an older native executable under `target` prevented
ordinary Maven clean. Isolated output is `target/harness-verification`, selected
through the new optional `flowerfarm.build.directory` Maven property. Default
Maven output remains `target`. No app process was stopped or installer rebuilt.

Final online `python -B scripts/verify_harness.py --isolated` passed 1,505 tests
plus two packaged MCP checks with zero failures/errors/skips, Checkstyle/SpotBugs
with zero findings, and coverage gates at 86.33% lines / 68.98% branches. Both
SBOM formats were generated. `--check` revalidated the exact source/report hashes.
Full log: `dist/harness-evidence/robustness-quality.log`; baseline log:
`dist/harness-evidence/baseline-quality.log`. The focused run found two assumptions
in new numeric fixtures (BigDecimal exponent boundary and immediate defaults
validation), both corrected before the successful final build. Independent
reviews found no remaining blocking defect. All agents are done editing.

Evidence review caught stale XML for old FarmProfileTest/InstalledDesktopSmoke
and Surefire header counts collapsing equal method names in nested classes.
The clean runner rejects stale reports and counts distinct testcase identities;
do not regenerate floors from arbitrary accumulated `target` reports.

GitHub Actions jobs for `d615837` could not start: account payments/spending limit
block all jobs before any steps. CI run `35599303740` and release run `35599304181`
carry the explicit billing annotation. Local success is not remote CI success.
The PR description is updated with this limitation and the new validation.
No stable release, native signing, external-account or physical-hardware work.

Historical continuation notes follow; their uncommitted/no-GitHub statements
describe earlier work and no longer govern the current request.

## Latest completed work: exact numeric requests and interrupted recovery

User said "next" after the recovery-contract pass below. This continuation is
complete. All agents are done and no Maven jobs remain active. Final offline
`mvn -o -Pquality verify` passed 1,394 tests plus two packaged-JAR MCP checks,
with zero failures/errors/skips. Evidence:
`target/harness-numeric-recovery-final-quality.log`. All earlier dirty/untracked
changes remain necessary and uncommitted.

This pass adds 59 regressions: `HarnessNumericTransportTest` (16),
`HarnessNumericMigrationTest` (7), `FarmDataMappingPrecisionTest` (8),
`FarmDataMappingPrecisionIntegrationTest` (2), `SwingPasswordCompositionTest`
(15), `OptimusReplayCleanupTest` (10), and one JPA conditional-delete test in
`OptimusHarnessRestIntegrationTest`.

REST/MCP now preserve decimal precision. `HarnessCommand` normalizes BigDecimal
scale. MCP preserves ordinary legacy numeric spelling only where exact,
restores explicit negative zero using a second constrained streaming token scan,
and probes supported lossy historical identities through replay only. An
existing ambiguous receipt causes an error before a fresh execution. Retained
receipt/known formatter limits still apply. Mapping JSON cells use BigDecimal;
numeric spreadsheet dates use an exact integer/range check. No punctuation-grammar
fix here: malformed numeric text such as `1,5` becoming 15 is a documented next
candidate, not part of this completed scope.

New `SwingPasswordGuard` rejects masked renderer data and password combo editors
through parent controls. Snapshots retain known protected combos as unsupported
without reading drafts. These fixtures cover extension controls; no current
production screen uses those compositions.

Optimus 0.23 adds locked request/fingerprint conditional claim cleanup, reports
cleanup persistence on durable replay, and lets subsequent lifecycle commands
retry cleanup after an earlier response was cached. New staging/safe-pose work
first journals an observed terminal operation so its completion cannot disappear
when the adapter replaces its current operation. Emergency-stop remains unchanged.
Independent reviews found no additional defect in those changes.

Red evidence: `harness-numeric-red.log` (11/14 failed),
`harness-recovery-password-red.log` (16/19 failed),
`harness-mapping-precision-red.log` (7/24 failed),
`harness-recovery-cached-red.log` (1/1 failed), and
`harness-terminal-history-red.log` (2/2 failed), all under target.
An isolated migration red inadvertently used an older review handler due classpath
shadowing; do not count its sixth failure as a new defect. A clean isolated
seven-case migration run passed. Maven focused runs passed 74 and 82 cases
(`harness-precision-focused.log`, `harness-numeric-migration-focused.log`).
Optimus-focused run had 40/41 pass; its new JPA fixture compared nanoseconds
against database-rounded timestamps. Root fixed that fixture to use aligned
milliseconds before the successful full build. No source edits are pending.

Harness, audit, release, and journey docs are finalized with behavior, evidence,
and compatibility limits. Coverage gates passed at 86.71% lines and 69.27%
branches. Checkstyle has zero violations; Medium-or-higher SpotBugs has zero
findings/errors. Final build took 5:17 and finished September 13 at 22:03:20
local time. Offline mode skipped SBOM generation. `git diff --check` passed.
No native installer, external service, hardware, GitHub, commit, or push work.

## Latest completed work: harness/API recovery contracts

User said "next" after the deep pass below. This continuation is complete; no
GitHub/native installer work. All earlier local changes must be preserved.
New `McpFrameReader.java` strictly decodes bounded UTF-8 frames and recovers
after malformed input; `McpUtf8FramingTest.java` has seven cases. Two initial
malformed-frame cases failed before the fix (`target/harness-next-utf8-red.log`).
Existing `McpPackagedProcessSmokeIT` now sends a malformed raw-byte inventory
request, checks parse rejection, and verifies no record was created.

API agent finished the fresh/historical order schema split and missing-target
source-receipt conflict translation in `FarmDataHarnessPlugin`. New tests are
`FarmDataOrderSchemaTest` (13 cases) and `FarmDataImportResumeIntegrationTest`
(five record types). Order red had four failures in 17 tests including journal
coverage (`target/harness-next-contract-red.log`). Resume red had five corrected
retry 404 failures plus three locale export failures in 13 tests
(`target/harness-next-resume-red.log`).

`SwingActionJournalBoundaryTest` adds three passing behavioral tests for 16 pending
actions, preserving a pending modal through eviction at 256 receipts, and interrupted
caller retry identity. No journal production change needed. Independent UTF-8
review found no defect and led to extra large-frame/buffer-boundary tests.

Replay agent finished `FarmDataExportService.java` and its tests. New ASCII
filenames and exact legacy decimal-digit names are supported. Fixed metadata
serialization and at most eight streamed raw-node checksum variants recover
supported older formats without rewriting files. Normalized requests are compared
after validation. Formatting red reproduced six failures in eight cases
(`target/harness-next-export-format-red.log`); all 16 export cases then passed.
Independent review found integer timestamp misinterpretation; red showed year 58671
instead of 2026 (`target/harness-next-export-millis-red.log`). The fixed metadata
reader now uses standard Jackson integral milliseconds and fractional seconds;
all 17 export cases passed in the final full run. Custom integer epoch-second
serializers and historical pretty output using a different host newline style
remain outside the legacy compatibility contract. Agents are done editing.

A 21-test framing/journal run passed
(`target/harness-next-framing-journal-green.log`); combined focused green passed
47 tests at `target/harness-next-focused-green.log`. Final offline
`mvn -o -Pquality verify` passed 1,335 tests plus two packaged-JAR MCP checks,
with zero failures, errors, or skipped tests. Evidence:
`target/harness-next-final-quality.log`. Checkstyle and Medium-or-higher SpotBugs
report zero violations/findings/errors. Coverage gates passed at reported
86.65% lines and 69.04% branches. Offline mode skipped SBOM generation.
There are 41 new regression cases in this pass. Audit, release, harness, and
journey docs are updated. All changes remain uncommitted locally; no agents
are editing and no Maven jobs remain active. No native installer, external
service, GitHub, or hardware acceptance work was performed.

## Completed local work: deep harness and API review

The owner asked to focus very deeply on the harness and API. This continuation
adds 32 regressions and fixes MCP receipt replay after physical permission
revocation, stable new MCP identities across expiry spelling/JSON formatting,
scoped legacy receipt lookup, safe REST/MCP error messages, strict JSON cells,
mapped harvest domain preflight, single-connection export transactions, snapshot
context consistency, screenshot attachment/root consistency, and visible table
header reads/sorting.

New files to preserve: `FarmDataCommandTransactions.java`,
`FarmDataHarnessTransactionTest.java`, `McpTransportReplayContractTest.java`,
`HarnessTransportErrorSafetyTest.java`, `SwingSnapshotConsistencyTest.java`,
`SwingCaptureConsistencyTest.java`, and `SwingTableHeaderTest.java`. Earlier
untracked files and all existing changes remain necessary; do not reset them.

Failure logs: `target/harness-deep-red.log`,
`target/harness-deep-observation-transaction-red.log`, and
`target/harness-deep-harvest-red.log`. The initial transaction fixture failed
during Flyway setup; its corrected separate Flyway datasource reproduced the
actual export/catalog connection timeouts. Focused offline Maven validation
passed 110 tests with zero failures/errors/skips in
`target/harness-deep-focused.log`. The first full offline run completed 1,294
tests with one existing date-sensitive CRM fixture failure and no errors/skips
(`target/harness-deep-full-quality.log`). The fixture now captures a date seven
days ahead using the same default-zone clock as the static review helper; its
focused check passed. Final offline `mvn -o -Pquality verify` passed with 1,294
tests plus two executable-JAR MCP process checks, zero failures/errors/skips
(`target/harness-deep-final-quality.log`). All 69 worker journeys and the
35-route catalog passed. Checkstyle and Medium-or-higher SpotBugs each report
zero findings/errors. Reported coverage is 86.44% lines and 68.81% branches,
above the configured gates. Offline mode skipped SBOM generation.

This continuation is complete and remains uncommitted locally. No agents are
editing and no Maven jobs remain active. No GitHub or native installer work
was performed. Agents completed independent transaction/replay reviews.
Remaining explicit limits: exact old envelopes only for bounded legacy lookup
(no durable canonical alias, arbitrary prior formatter/changed old expiry spelling
cannot be reconstructed); singleton gateway serializes commands; export test
covers H2 repeatable-read, not unsupported SQLite isolation or an external caller's
outer transaction. Snapshot alias probe uses a custom synchronous Swing getter,
not an identified production getter. See `docs/HARNESS_AUDIT.md` for details.
The follow-up above resolves the previously identified order-schema mismatch
and journal boundary coverage gaps. Native OS, external services, and hardware
acceptance remain separate.

## Completed local work: harness and API boundaries

The owner requested continued harness and API work. This pass fixes full-width
attachment and target IDs in `FarmDataHarnessPlugin`, document replacement
during `edit-text`, and combo-model refresh during `choose-option` renderer
matching. Eight new regressions failed before those fixes: six assertion
failures and two expected old-ID range errors in 33 selected tests
(`target/harness-api-boundary-red.log`).

REST command parsing now uses a `HarnessCommand`-scoped mapper to reject
duplicate keys, trailing input, unknown envelope fields, and coercion of
command names/request IDs. Expiry timestamps must be ISO strings; numeric and
stringified epoch values are rejected while offset timestamps and null/missing
optional fields remain supported. API errors now distinguish unsupported
request media, missing multipart parts, and unacceptable response media.

The first focused Maven run passed 54 tests with zero failures/errors/skips
(`target/harness-api-boundary-focused.log`). The broader harness, controller,
and attachment run passed 515 tests, including all 69 journeys and 35 routes
(`target/harness-api-boundary-validation.log`). That run compiled just before
the last stringified-epoch expiry guard; the final recompiled API suite passed
32 tests, including all 18 strict-body cases, actual HTTP converter registration,
attachment allowance, and Optimus REST integration
(`target/harness-api-boundary-final-api.log`). Checkstyle and Medium-or-higher
SpotBugs passed with zero violations/findings/errors
(`target/harness-api-boundary-quality.log`). This pass adds 31 regressions.

This continuation is complete and remains uncommitted locally. No agents are
editing or running Maven. Preserve the new
`src/test/HarnessCommandJsonBoundaryTest.java` and all earlier local files.
No GitHub or native installer work was performed. Native OS input and external
service acceptance remain separate checks.

## Completed local work: consistent reads and replay accounting

The newest continuation adds closed-menu accelerator fallback to
`invoke-shortcut`, before/after consistency checks for all four structured
reads, and JSON byte accounting for replayed entity/DTO payloads. New files to
preserve are `SwingShortcutResolver.java`, `SwingShortcutResolverTest.java`,
`SwingReadConsistencyTest.java`, `HarnessPayloadSizer.java`, and
`src/test/HarnessReplayBudgetTest.java`. The gateway retains completed effects
before fallible accounting and uses the application's ObjectMapper; existing
2-argument and Clock constructors remain available.

The September 8 fixes passed 84 focused tests and a 371-test wider run, but
two final receipt-retention regressions were added after that run compiled.
Resuming on September 14 UTC reproduced both failures: completing an older
receipt could evict that same receipt when sizing failed or the recovered
snapshot exceeded the budget, allowing a completed effect to execute again.
`HarnessGateway.cache` now moves completion updates to the newest position
before eviction while preserving byte totals. A further behavioral regression
confirms ordinary response replay keeps its original eviction position.

The resumed focused run passed 87 tests, zero failures/errors/skips. Evidence:
`target/harness-resume-replay-red.log` (12 tests, two reproduced failures) and
`target/harness-resume-focused.log` (87 passed). Final wider validation passed
374 tests with zero failures/errors/skips, including all 69 journeys, the
35-route catalog, application context, REST/MCP, and Optimus simulator
integration (`target/harness-resume-validation.log`). Checkstyle and the
Medium-or-higher SpotBugs gate passed with zero violations/findings/errors
(`target/harness-resume-quality.log`). Independent shortcut and cache reviews
are complete. This continuation is complete and remains uncommitted locally;
no agents are editing or running Maven. Preserve all earlier local changes.
No GitHub or native installer work was performed. Native OS input and external
service acceptance still require their separate checks.

The preceding continuation verifies the results of existing desktop commands.
SwingHarnessBridge now checks retained option/tab/selection/slider/sort state;
combo comparisons permit fresh equal CRM choice records. Double-click checks
the requested selection, original model-row mapping, and any intervening model
notification before delivering a mouse event. This prevents opening another
record after selection listeners redirect or refresh the table.
New `SwingSpinnerActions` parses and validates standard-model input before
changing drafts, rejects unusable editors, uses normal commit listeners, and
checks the final model value. Observation binds effective table renderer
identities so replacing the default renderer cannot mix paged cell output.
Exact integral preflight also rejects the JDK formatter's silent wraparound,
fractional truncation, and partial numeric parsing, while preserving localized
decimal formats and explicitly overridden parsers.

Preserve new `SwingSpinnerActions.java`, `SwingSpinnerActionsTest.java`,
`SwingActionOutcomeTest.java`, and `SwingRowActivationTest.java`. There are 29
new regressions in this batch. Focused offline validation passed 85 tests with
zero failures/errors/skips (`target/harness-outcome-focused.log`). The first
wider run passed 305 tests (`target/harness-outcome-validation.log`). After
adding the exact integer guard and its three regressions, final offline
validation passed 308 tests with zero failures/errors/skips, including all 69
journeys and the 35-route catalog (`target/harness-outcome-final-validation.log`).
Checkstyle and the Medium-or-higher SpotBugs gate passed with zero violations,
findings, or errors (`target/harness-outcome-quality.log`). This continuation is
complete and remains uncommitted locally; no agents are editing or running Maven.
No GitHub or native installer work was performed. Native OS/external-service
acceptance limitations from the preceding work remain unchanged.

The preceding continuation expands the runtime desktop harness to plugin 1.8 and
30 commands. Added scroll/set-divider/set-toggle/choose-option-index/
clear-selection/read-screen/read-layout/read-item/edit-text; paged read-control;
selector-or-menu-path targeting for read-control/set-toggle; read-only/layout
discovery; expected-context on four structured reads; scrollbar/viewport context
revisions; and exact text edit postconditions. New helpers are
`SwingControlActions`, `SwingControlReader`, `SwingScreenReader`, and
`SwingMenuTarget`, with corresponding direct and integration tests. Preserve all
untracked files in `src/main/java/com/flowerfarm/gui/automation` and
`src/test/com/flowerfarm/gui/automation`.

Focused validation passed 93 tests including the extended 35-route read/layout
catalog, with zero failures/errors/skips (`target/harness-capability-focused.log`).
The first failures were fixture/type-inference issues and an old menu diagnostic
contract, all corrected. The broader 277-test run passed all but one obsolete
manifest tool-count assertion (54 vs the new 63), now updated alongside discovery
and default policy assertions. Final review also added clear-selection
postconditions and two regressions for listeners restoring row/column/list
selections, including recovery. The final offline regression passed 279 tests
with zero failures/errors/skips, including all 69 journeys and the expanded
35-route catalog (`target/harness-capability-validation.log`). This continuation
adds 57 regressions. Checkstyle and the Medium-or-higher SpotBugs gate passed
with zero violations/findings/errors (`target/harness-capability-quality.log`).
This continuation is complete and remains uncommitted locally. No agent is
editing or running Maven. GitHub work remains stopped; native installers were
not rebuilt or launched. Native OS input, external accounts, and assistive
technology still require their separate acceptance checks; these local passes
do not claim universal platform coverage.

The preceding continuation implemented optional observation-bound desktop actions.
Swing plugin 1.7 adds `metadata.contextToken` to ready snapshots and the optional
`expected-context` argument to all 18 queued actions. A changed visible tree,
Swing model revision, modal, or attachment rejects the callback with
`DesktopContextChangedException`. An execution-scoped bridge pins the accepted
attachment and active root to prevent a concurrent attach from retargeting it;
no lock is held through modal callbacks. Replay remains before validation.
Unchanged observations reuse the token; listeners are pruned/cleared with the
active tree/attachment. Normal Swing notifications and EDT UI mutation are
required; this is not a transaction over hidden domain state.

New files to preserve:
- `src/main/java/com/flowerfarm/gui/automation/SwingObservationContext.java`
- `src/main/java/com/flowerfarm/gui/automation/DesktopContextChangedException.java`
- `src/test/com/flowerfarm/gui/automation/SwingObservationContextTest.java`
- `src/test/com/flowerfarm/gui/automation/SwingContextBindingTest.java`

There are 24 new regressions (10 helper, 13 plugin/gateway, 1 REST wire), including
a deterministic narrow attachment race. The focused 30-test pass succeeded in
`target/harness-context-focused.log`. Broader validation passed 222 tests with
zero failures/errors/skips, including all 69 journeys and 35 runtime routes;
see `target/harness-context-validation.log`. Checkstyle and the Medium-or-higher
SpotBugs gate passed with zero violations/findings/errors in
`target/harness-context-quality.log`. This batch is complete and remains
uncommitted locally; native installers were not rebuilt or launched.
The contract and limitations are documented in `docs/AI_HARNESS.md` and
`docs/HARNESS_AUDIT.md`. GitHub work remains stopped as directed below.

The preceding continuation added spinner model-bound/type validation in the human
driver, atomic first-published desktop action receipts for concurrent callers,
and an explicit retry error for non-advancing live farm-data pages. Six new
regressions were added. Offline validation passed 190 tests including all 69
journeys and 35 routes; logs are `target/harness-continuation-red.log` and
`target/harness-continuation-validation.log`. The new regression file
`src/test/com/flowerfarm/gui/automation/SwingActionJournalTest.java` is untracked
and must be preserved alongside all earlier local changes. Checkstyle and the
Medium-or-higher SpotBugs gate passed with zero findings; static-gate output is
`target/harness-continuation-quality.log`.

The owner asked to work on FlowersForever's harness. Local changes now fix
hidden-ancestor visibility in the human test driver, reject disabled tab
navigation, and make runtime discovery check every accepted selector alias.
Ambiguous accessible labels use an existing unique component name when one is
available; three Activity History filter names were added. The all-route audit
now reports selector defects across every route in one failure. Its stricter
checks exposed six real label collisions and a Market Day order handoff that
left the CRM Customers workspace active; the handoff now selects Orders. The
Trend Analysis journey explicitly opens Model details before reading it.

Seven new focused regressions were added. The final offline Maven run passed
366 tests with no failures/errors/skips, including 69 user journeys and all 35
runtime routes. Log: `target/harness-local-final-validation.log`. Checkstyle and
the Medium-or-higher SpotBugs gate both passed with zero findings; results are
recorded in `target/harness-local-quality.log`. Changes
remain uncommitted locally; native installers have not been rebuilt. Read the
September 7 follow-up in `docs/HARNESS_AUDIT.md` for details. Preserve all work.

## GitHub work remains stopped

The owner said to stop worrying about GitHub and attached an Actions storage-limit notice. GitHub work is stopped; the CI-review subagent was interrupted. Do not resume GitHub checks, cleanup, CI runs, or PR work from the historical instructions below unless the owner asks. Further FlowersForever work should stay local.

Before that direction, the prior cleanup was found already complete: 265 report artifacts / 673,838,666 bytes removed, zero journal errors. The finalizer rehashed every recovery ZIP successfully and created `public-artifacts/report-backup-manifest.json`, `report-cleanup-receipt.json`, and `CLEANUP-RESULT.md` under the audit root below. Independent comparison confirmed exact plan/deletion ID equality and preservation of every other prior ID. Six new LoadBalancerPro uploads account for 7,714,706 additional bytes. The cleanup paragraph in REPORT.md was updated; other PR/CI status paragraphs still reflect the earlier audit and were not fully refreshed before the owner stopped GitHub work. ComputerPets PR #676 was observed closed and unmerged; LoadBalancerPro's previously pending run was observed failed, with investigation stopped. No new cleanup execution, CI rerun, merge, or publication occurred during this continuation.

Local FlowersForever HEAD is `b4cd9d1dba5d5e953c34fd3a14637126abe7eced`, which adds `docs/images/farm-day-flow.svg` to the README. The earlier handoff's `f3ca640` HEAD is superseded. Only this handoff file was untracked at continuation start.

# Historical handoff — September 5, 2026 UTC

The owner requested these notes immediately because usage is at 3%. Read this first, then the audit report. Do not restart completed work or ask for permissions already granted.

## Current task and authorization

The owner authorized inspecting GitHub and fixing bloat problems in other repositories. We audited all 100 RicheyWorks repositories, prepared three draft PRs, and are performing an authorized, backed-up cleanup of old public test reports. Finish the running cleanup, verify receipts, update the report, and give a concise final outcome. The latest user request is to preserve handoff notes so another model can finish.

Audit root: C:/Users/730ri/GitHub-maintenance/2026-09-05-storage

Combined report: C:/Users/730ri/GitHub-maintenance/2026-09-05-storage/REPORT.md

PowerShell 7; gh is authenticated as RicheyWorks. Danger-full-access, approval policy never: do not pass sandbox_permissions. No formal goal or scheduled automation was created. Keep progress updates concise; do not create a new user-owned task unless requested.

## FIRST: running cleanup — do not interrupt or replay

A Python cleanup process continues independently in unified exec session **4993**. Both monitoring agents have stopped their model turns to conserve usage; they did NOT cancel the process or GitHub CI.

- At 2026-09-05 06:52:11 UTC, the root independently read 111 deleted reports /410,166,114 bytes; journal status running, errors empty. This is a snapshot, not the final total.
- Expected complete scope: 265 old reports /673,838,666 bytes (642.62 MiB): LoadBalancerPro 151 JaCoCo reports /552,332,824 bytes; CSRBT 114 JDK reports /121,505,842 bytes.
- Poll with tools.write_stdin({session_id:4993, chars:"", yield_time_ms:1000, max_output_tokens:1000}) through functions.exec. If session IDs are unavailable to the new model, read the journal and inspect the running process instead. Do not start a second cleanup.
- Script: public-artifacts/cleanup-stale-reports.py (currently running with --execute).
- Immutable reviewed plan: public-artifacts/report-cleanup-plan.json.
- Live atomic journal: public-artifacts/report-cleanup-journal.json.
- Recovery ZIPs: public-artifact-backups/ under the audit root.
- Before/after inventory names: public-artifacts/RicheyWorks__LoadBalancerPro.cleanup-before.json and .cleanup-after.json; equivalent CSRBT files.

Each exact artifact is freshly checked, downloaded to an exclusive ZIP, size/SHA-256 verified against GitHub, then deleted sequentially with HTTP 204 recorded. Allowed names only: LoadBalancerPro jacoco-coverage-report; CSRBT reports-jdk17, reports-jdk21, test-reports-jdk17, test-reports-jdk21. Protects reports younger than 14 days, newest artifact per name, latest successful main CI per workflow, open-PR runs/branches. Other artifact classes, releases, runs, caches, and private repositories are excluded. Root and packaging_fix reviewed the script and plan. There is no need to ask permission again.

After execution finishes:

1. Require journal status deletions_complete, errors empty, and after_verification showing no unexpected_missing_prior_ids or deleted_ids_still_present. Natural expiration and new uploads are recorded separately.
2. Run: python C:/Users/730ri/GitHub-maintenance/2026-09-05-storage/public-artifacts/finalize-cleanup-receipts.py
3. This rehashes every backup and writes report-backup-manifest.json, report-cleanup-receipt.json, and CLEANUP-RESULT.md in public-artifacts/.
4. Update combined REPORT.md with ACTUAL removed counts/bytes and reconciliation. Its cleanup paragraph is currently provisional.

If status is stopped_on_anomaly, preserve all files and inspect errors; do not blindly resume, delete more, or overwrite the plan/journal. If an interrupted DELETE is ambiguous, reconcile that exact ID with its verified backup before deciding anything. The script intentionally refuses duplicate execution when a journal already exists.

## Draft fixes already committed and pushed

### CSRBT — fully verified

https://github.com/RicheyWorks/CSRBT/pull/2

Head a628de1eab5d762034259e0e867dd972da63765d, branch codex/limit-ci-report-retention.

Isolated checkout: audit-root/CSRBT-retention. Only adds retention-days: 7 to the existing report upload, keeping JDK 17/21 tests and every report path. actionlint and scalar-only diff validation pass. Hosted PR run 33950389293 passed both JDK jobs; report IDs 9964639901 and 9964636634 successfully uploaded with September 12 expiry. Receipt: public-workflows/CSRBT/implementation.json. Original C:/Users/730ri/projects/CSRBT has an unrelated modified tools/verify/verify_organism.py; preserve it.

### LoadBalancerPro — retention verified; full CI pending at handoff

https://github.com/RicheyWorks/LoadBalancerPro/pull/583

Head 41df52e41ff34b33539ddb0b4414ab7948c37424, branch codex/limit-ci-report-retention.

Isolated checkout: audit-root/LoadBalancerPro. Only seven ordinary CI retention scalars change 30 to 7 days. Scheduled benchmark/soak reports remain 30 days; releases remain 90 days. actionlint 1.7.12 and 11 existing workflow contract tests pass. Log: loadbalancer-workflow-contract-tests.log.

PR run 33950400974, build job 101263960645. Last snapshot: active-active topology smoke started 06:49:28 UTC, no failed steps. Tests, supervisor proof, package/Docker and loopback benchmark passed; CodeQL run 33950400976 and Dependency Review passed. Coverage, package-smoke, and SBOM uploads confirmed seven-day expiry (IDs 9964707203, 9964712115, 9964714319). Full CI is NOT yet confirmed. The previous successful baseline took 36m07s, including 19m40s Kubernetes smoke. Check naturally triggered CI once near final reporting; no duplicate rerun or merge. Receipt: public-workflows/LoadBalancerPro/implementation.json.

### ComputerPets — syntax/context fix; existing tests still fail

https://github.com/RicheyWorks/computerpets/pull/676

Head 3ac522766569ed4081fe69d544c54839e0e948bd, branch codex/computerpets-ci-storage.

Isolated sparse worktree: audit-root/computerpets/worktree. Changes only .github/workflows/ci.yml and .dockerignore: fixes malformed digest-echo YAML and output reference, consolidates Maven phases, cancels superseded runs, scopes existing Docker-push permissions, excludes unrelated web/desktop/client/cries-in assets from backend Docker context. Upload triggers and seven-day JAR retention remain intact. actionlint/SnakeYAML pass; Docker COPY inputs verified.

Approximately 2.975 GB excluded from eligible Docker context; this is NOT measured network savings or Git-history reduction. Sprite copies are intentional client-generator output and remain preserved.

Hosted run 33950482027 now starts, but finished failure in unchanged tests: backend 281 tests/5 catalog assertions failed; desktop 601 tests/2 hours.test.cjs assertions failed; PyQt 187 tests/1 GRID_KEYS count assertion failed (10 vs9). Local backend separately ran279/5fail. No skips or weakened assertions. PR body documents all results. Logs and details: computerpets/FINDINGS.md, hosted-backend-test.log, hosted-desktop-test.log, hosted-pyqt-test.log. These application failures are outside the storage patch; do not claim this repo green.

Original ComputerPets checkout remained at11412f0 with zero tracked changes and1,213 untracked user files. Preserve that checkout and open UI PR622.

## Audit findings and boundaries

- 98 public and2 private repos. Full public artifact inventory:1,353 nonexpired artifacts/1,121,019,605 bytes. Most in LoadBalancerPro and CSRBT. Expired descriptors are excluded from claimed savings.
- Total Actions caches across100 repos:1,218,892,921 bytes; no individual repo close to10GB. Do not claim cache deletion fixes artifact quota.
- ComputerPets has zero current artifacts/caches; its large tree is client assets. ProcessingMasterArchive's334.6MB is mostly intentional captured artwork; preserve it. BlackJackPro current tree1.41MB and LoadBalancerPro13.73MB have no large accidental build outputs. See public-tree-audit-summary.md.
- WholeHog CI fails from missing sibling Rub checkout; SmokeHouse has a concurrent-compaction test failure. Both have zero artifact storage. Evidence: public-workflows/findings.md. No changes made to these unrelated issues.
- Public artifact bytes are not a verified private billable quota balance. GitHub Packages inventory returned403 because read:packages is absent; do not expand auth scopes or change billing. Usage can lag6–12h; deletion does not erase accrued storage usage. Primary source linked in REPORT.md.
- The three PRs remain drafts, unmerged. Do not claim default branches already contain the fixes. No releases, user source assets, or Git history were deleted.

## FlowersForever earlier work — preserve

Current workspace C:/Users/730ri/projects/FlowersForever, branch codex/recover-unpushed-work, HEAD f3ca640872dd0717da46ebb3d0741ce104a8e9f7. README polish was committed and pushed after the initial handoff; only this handoff file remains untracked. Draft PR21: https://github.com/RicheyWorks/FlowersForever/pull/21

App source0859d40 passed1078 Java tests,2 packaged JAR integration tests and native Windows/macOS checks. Mac H2 startup fixed by default h2.bindAddress=127.0.0.1 while preserving explicit overrides and AUTO_SERVER. Latest f1cf924 is signing-runbook documentation only.

Successful private preview run33949094397; private unpublished draft release383152727: https://github.com/RicheyWorks/FlowersForever/releases/tag/untagged-9c40db0604429af9431d . All11 assets verified under C:/Users/730ri/FlowersForever-backups/preview-0859d40ff0b3. No real tag/published release was created.

The owner's explicit YES authorized deletion of old combined artifact9677394786 from run33149933877. DONE at06:31:05UTC after matching local ZIP size383,810,734 and SHA256 a31899e83175bd1ca31d20e8ad8b6b47fffd149d46f7106c105660836b4353ed. Recovery: C:/Users/730ri/FlowersForever-backups/preview-33149933877/9677394786.zip. All other102 artifacts preserved, totaling432,100,863 bytes. Receipts under FlowersForever-backups/preview-runs/actions-*9677394786.json. Do not delete other protected preview artifacts.

Latest ordinary CI33949966837 and Release33949967002 still fail only at artifact uploads with quota errors; other completed tests/build checks pass. Private preview remains available. Do not claim quota recovery or trigger redundant reruns solely to probe it.

The owner explicitly said Apple/Microsoft enrollment waits until funds arrive in about15days after September4 (~September19). This is already documented in docs/SIGNING_SETUP.md. DO NOT ask publisher identity/credentials again, enroll/pay, or create a reminder (none requested).

Norton IDP.Generic warning on a local FlowersForever executable is unresolved. Do not launch/restore/exclude that flagged executable, change antivirus settings, or submit a sample without explicit authorization. The preview MSI is unsigned, appropriate to the current private preview state, not signed customer distribution.

## Finish and report

Finish cleanup receipt verification first, update REPORT.md, optionally open that file in Codex, and report actual reclaimed storage plus the three draft PRs and important test/quota limitations. Do not ask the user to reconfirm already authorized work. No merge/publish was performed. Preserve these notes for the next model.

## Latest owner follow-up: README polish completed

The owner asked to polish the README with remaining usage. Completed and pushed commit f3ca640872dd0717da46ebb3d0741ce104a8e9f7 on codex/recover-unpushed-work (existing draft PR21). README now has a tighter hero, four spacious feature cards, clearer navigation, stronger screenshot captions, and a collapsible full feature guide. Existing feature claims, private-preview/unsigned-installer disclosures, and documentation are preserved. No new image assets or dependencies.

Validation: all33 existing local image/doc targets resolve; new navigation targets match retained headings; details/table tags balance; git diff --check passes. No full application suite rerun for this presentation-only edit. Naturally triggered CI for f3ca640 has not been checked; earlier private artifact quota failures may still apply. The application binary remains the verified0859d40 source; f1cf924 and f3ca640 change docs only. Continue cleanup session4993 and final receipt work from the notes above.
