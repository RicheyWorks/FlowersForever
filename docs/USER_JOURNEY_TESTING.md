# Desktop user-journey testing

See [HARNESS_AUDIT.md](HARNESS_AUDIT.md) for the current human-action matrix,
recursive surface inventory, findings, verification evidence, and explicit
OS-level smoke-test limits.

## Production Studio on a laptop

`ProductionStudioRefreshSafetyTest` covers delayed and failed reads, initial-load
edits, task drafts attached to stable IDs, reordered and missing tasks, explicit
reload/discard, exit warnings, and captured background task saves. A successful
write followed by a failed read keeps the saved result and blocks further task
writes until a fresh review succeeds. A superseded refresh cannot overwrite a
newer draft or silently re-enable task writes.

`ProductionStudioVisualContractTest` renders all eight Studio views at 850×500
and 1180×760, in light and dark themes. The smaller size represents the content
left after navigation and headers on a laptop. It checks real viewport geometry,
vertical scroll reachability, human labels and associations, button text, table
identity columns, and complete recovery/task-context messages. Screenshots are
written to `target/ui-previews/production-studio`. These are actual Swing component
renders using sample service fixtures, not native pointer/keyboard acceptance.

## Wiring regressions

`ProductionStudioDerivedRefreshTest` drives the real Production Studio tab and
reads its rendered cost/availability tables after writes. Related drafts remain
intact, and a failed read after a committed write reports saved work plus a
refresh instruction. `SwingActionDeadlineTest` holds the event-dispatch thread
busy and verifies that an expired queued callback never runs, while receipt
status and replay remain stable.

`FarmDataOperationsHarnessIntegrationTest` connects REST, MCP, worker services,
and the database for reviewed counts, order handoffs, cross-surface receipt
recovery, season-setting readback, policy gates, and skipped-task previews.
`McpResourceTest` covers observation-only plugins and live disclosure policy;
the packaged MCP process smoke also reads resources and replays a durable stock
count across two separate JVMs. `McpDoctorReadinessTest` covers the real farm
manifest plus unavailable data paths and missing production/worker tools.

## Phone and tablet entry checks

Run `node --test scripts/worker-ui.test.mjs` with Node.js 24. These dependency-free
tests execute the shipped worker script against the form elements declared in
the shipped HTML. They exercise draft persistence, farm-date defaults, offline
and uncertain-response replay, storage refusal, validation, and safe text
rendering. They use a simulated DOM and network; they do not replace browser
layout inspection. CI and release verification both run this check.

`WorkerWorkflowIntegrationTest` verifies the actual authenticated HTTP and
database side, including role denial, atomic note/receipt creation, exact-key
replay, changed-key conflicts, and input validation. `DatabaseMigrationTest`
checks the worker receipt action constraints through Flyway V21.

FlowersForever includes a headless Swing harness that enters values through the
same fields, selectors, buttons, tabs, table mouse listeners, keyboard bindings,
menus, sliders, and event listeners used by a worker. It waits for production
`SwingWorker` tasks and verifies the resulting screen state and service mutation.

The harness selects form controls by accessible name instead of private fields
or component position. A renamed or unlabeled control therefore produces a
useful product/accessibility failure instead of silently driving the wrong UI.

User actions and visible-text waits require the entire ancestor path to the
harness root to be visible. Controls and old success messages inside inactive
tabs cannot satisfy a journey, and disabled tabs cannot be selected through
the driver. The `named` helper remains available for structural assertions;
actions on a component found that way still check visibility and enabled state.
`SwingUserHarnessFailureTest` covers these boundaries with duplicate selectors,
hidden controls, nested tabs, and a success message revealed only by navigation.

The driver's Object-based `setSpinner` helper validates before changing the
model: numeric values must be exactly representable in the existing numeric
type and within its bounds; dates must respect their range; list values must
exist in the model. Invalid input does not change the editor or trigger change
listeners. This prevents programmatic `setValue` from bypassing restrictions
that the ordinary spinner editor enforces.

## Current journey coverage

`HarnessNumericTransportTest` exercises real REST binding and MCP framing with
precise decimals, numeric request IDs, overflow/underflow, replay, and changed
arguments. `HarnessNumericMigrationTest` seeds historical physical receipts to
verify safe rejection of ambiguous rounded identities and exact negative-zero
recovery. `FarmDataMappingPrecisionTest` and its HTTP integration counterpart
verify fractional fields fail before records or source receipts are saved.

`OptimusReplayCleanupTest` verifies accepted receipts, cleanup outages, cached
responses, lifecycle retries, and newer-plan preservation with the simulator.
`OptimusHarnessRestIntegrationTest` also exercises conditional deletion against
the real persistence store. `SwingPasswordCompositionTest` covers masked
renderers and password combo editors, safe failed-action replay, and discovery.
Those Swing fixtures exercise supported extension controls; current production
screens do not use these password compositions.

`SwingActionJournalBoundaryTest` exercises queue saturation, preservation of a
pending modal during completed-receipt eviction, and interrupted caller retries
using EDT barriers and a secondary event loop. `McpUtf8FramingTest` checks strict
decoding and recovery across malformed, oversized, fragmented, and multibyte frames;
the process smoke verifies malformed input cannot create an inventory record.

`FarmDataOrderSchemaTest` separates fresh-order states from historical migration
states across discovery, catalog, and dispatch. `FarmDataImportResumeIntegrationTest`
checks missing-target conflicts for unchanged and corrected source rows across
five types, including preserved receipts. `FarmDataExportServiceTest` now covers
host locale changes, supported old metadata encodings, timestamp precision, and
tampered-manifest rejection without checksum rewriting.

`SwingSnapshotConsistencyTest` and `SwingCaptureConsistencyTest` verify that
control metadata/tokens and captured pixels cannot silently survive a changed
attachment or active root. The alias-capture test explicitly uses a custom
getter callback as an invariant probe. `SwingTableHeaderTest` covers renamed
and reordered visible column titles, sorting, and duplicate-title rejection.

`McpTransportReplayContractTest` checks equivalent expiry instants, stable JSON
identity, exact legacy recovery without fresh execution, policy revocation, and
snapshot filtering. `HarnessTransportErrorSafetyTest` checks bounded/redacted
REST and MCP errors. `FarmDataHarnessTransactionTest` uses a one-connection H2
pool to verify exports and database-free reads alongside atomic write rollback.
Mapped-import tests reject ambiguous JSON cells before writing any row and use
the same harvest date/quantity rules during preview and import.

Runtime capability tests include `SwingControlActionsTest`,
`SwingControlReaderTest`, `SwingScreenReaderTest`, `SwingRuntimeCoverageTest`,
`SwingTextEditingTest`, `SwingMenuTargetTest`, and `SwingMenuRuntimeTest`. They
exercise long documents/collections, real Event Services layout and output,
toggle/no-op behavior, renderer labels, input bounds, policy gates, and closed
menu paths through MCP. The all-route catalog also reads each published
read-only output and layout selector on all 36 routes.

`SwingActionOutcomeTest`, `SwingSpinnerActionsTest`, and
`SwingRowActivationTest` cover application listeners that refuse or redirect an
action, invalid spinner input preserving a draft, failed receipt replay, and
recovery. Double-click checks prevent loading a different record after selection
changes the target. Runtime paging also rejects an effective table-renderer
replacement between pages without inspecting table records during observation.
Text-edit regressions also replace the field's document from a listener and
verify failed receipt replay and recovery. Option-choice regressions refresh
the model from a renderer and verify rejection before the harness selects from
the changed options, including editable selectors and a stable retry.

`HarnessCommandJsonBoundaryTest` checks malformed REST envelopes, strict expiry
types, both JSON media types, and unchanged ordinary API binding.
`HarnessEnabledIntegrationTest` checks command parsing on the running server.
`FarmDataHarnessPluginTest` verifies full-width attachment IDs, rejected invalid
IDs, and exact REST/MCP round trips and replay. `ApiExceptionHandlerTest` covers
unsupported content types, missing upload parts, and response negotiation.

The runtime plugin's optional observation binding is covered separately by
`SwingContextBindingTest` and `SwingObservationContextTest`. These use EDT
barriers and model notifications to check queued replacements, row changes,
modal and attachment changes, unchanged-token reuse, subscription cleanup,
and replay/recovery behavior. They do not add native pointer/keyboard coverage.

`SwingShortcutResolverTest` and the MCP menu runtime tests cover closed-menu
accelerators, explicit input-map priority, disabled sources and menu ancestors,
ambiguous paths, and active-modal isolation. `SwingReadConsistencyTest` checks
refreshes during rendering, mixed-page rejection, stable first reads, and
same-request recovery without caching a partial page. `HarnessReplayBudgetTest`
uses populated farm-record pages to verify byte eviction, configured serializers,
and receipt protection if accounting fails.

`UserJourneyHarnessTest` currently exercises:

- researching a wet-site native by accessible search/type/origin controls,
  sorting the visible table through its production row sorter,
  selecting Pacific ninebark by visible table value, checking the source-backed
  field note, switching to the Kitsap Master Gardener resource, and clearing
  back to all 94 profiles;
- applying a flower-product starter, entering price/cost/stock, reviewing live
  economics, and saving the captured inventory item;
- correcting an invalid price, keeping repeated setup, and submitting with the
  Enter key;
- running a named boundary-data matrix across blank names, non-finite prices,
  text costs, fractional quantities, and valid Unicode/punctuation input;
- entering a harvest, persisting every field, waiting for inventory-aware save,
  and retaining context for the next cut;
- retaining a complete multi-crop morning batch after a background inventory
  failure, restoring its action after history refresh, and retrying the exact
  date, defaults, notes, per-row overrides, and quantities once;
- filtering harvest history to one crop, loading that exact row, correcting its
  quantity and notes, then duplicating the saved values into today's add form;
- cancelling a filtered Harvest CSV destination, refusing an existing-file
  replacement without changing its bytes, explicitly approving the captured
  visible-row export, and cancelling then confirming the same inventory-reversing
  harvest deletion once;
- correcting an invalid Season Planner schedule without re-entering the modal
  draft, creating its exact 14-day succession, restoring the newly saved block
  instead of the prior selection, and cancelling then confirming removal;
- recording every Knowledge Log field—including renderer-labeled type and
  confidence choices, tags, follow-up, and linked farm records—then filtering,
  editing, and safely removing the exact saved lesson;
- changing a Knowledge query while a background refresh is blocked and proving
  the latest request is queued and applied instead of leaving stale rows beside
  the new search text;
- opening an empty Season Planner and Knowledge Log, locking each primary action
  during a slow first save, retaining the complete modal draft after a database
  failure, and retrying that exact planting block or farm lesson only once;
- previewing a valid zero-activity report range with explicit neutral guidance
  and actionable Product and Customer empty states instead of blank grids;
- selecting a report period, previewing it, and navigating the product and
  customer performance tables;
- choosing an explicit crop/unit and history window, running trend analysis,
  and inspecting forecast, reliability, freshness, and weekly evidence;
- sweeping a completely empty farm across Inventory, Harvest Log, CRM orders
  and customers, Market Day, Trend Analysis, and Dashboard: first-run copy is
  distinct from filtered-empty recovery, unsafe row/order actions remain
  locked, the first saved or refreshed record replaces onboarding cleanly,
  forecasts never invent evidence, and Dashboard routes the first useful task;
- searching inventory, clearing the search, opening the low-stock queue, and
  verifying that the scarcest product is prioritized;
- creating a complete florist customer account and waiting for the asynchronous
  CRM table refresh, then loading it through the table's real double-click listener;
- creating a pickup order with two products and verifying its exact lines,
  notes, handoff, and total;
- detecting a market-day stock shortfall, following the inventory handoff,
  changing the packing window, rebuilding a healthy plan, and packing its order;
- locking repeat submission during a slow inventory save, preserving the full
  draft after a failed save, and retrying it successfully;
- preserving inventory rows and search context through a failed background
  refresh, then applying the recovered result;
- starting from populated Dashboard, Harvest, CRM, and Market Day snapshots,
  forcing each refresh to fail, proving the last reviewed data and unfinished
  drafts remain visible, locking stale-row edits and order completion, retaining
  safe navigation and exports, then applying a successful authoritative retry;
- mixing current Dashboard core metrics with failed bed-production, irrigation,
  and Market Day context: every unavailable source is named, last-good context
  is visibly marked, stale optional priorities are suppressed, and a clean
  refresh removes the warning without hiding current inventory or sales data;
- changing a report range during an active background preview and proving only
  the latest range is applied, plus labeling retained metrics with their exact
  previous range when a later preview fails before recovery;
- accepting newer order rows from one CRM source only after every customer,
  order, confirmed-queue, and inventory source succeeds; a failed source is
  named, every partial response is discarded, the reviewed snapshot and draft
  remain visible, stale writes stay locked, and an authoritative retry recovers;
- changing both crop series and history window while Trend Analysis is running,
  visibly queuing the latest choice, discarding the superseded forecast or
  failure, and automatically analyzing only the final requested evidence;
- correcting invalid customer email and order-date input without losing any
  entered account, product, handoff, or instruction data;
- retaining report ranges and trend selections through background-service
  failures and successful retries;
- enforcing VIEWER read-only behavior across Add Item, Inventory, CRM, and
  Market Day while keeping search, filters, refresh, reports, and exports usable,
  then restoring write controls after a role switch;
- entering a physical stock count through the production modal form and saving
  the exact counted quantity;
- editing named fields inside the production inventory modal, detecting an
  invalid price, preserving the rest of the draft, and correcting it in place;
- retaining every edited field after a duplicate-SKU rename fails, then changing
  only the conflicting name and successfully retrying the same captured item;
- cancelling a filtered inventory CSV export at the native-file boundary, then
  selecting a destination and verifying the exact captured rows on disk;
- refusing to replace an existing CSV and proving its bytes remain unchanged,
  then explicitly approving and verifying the replacement export;
- reading the saved CRM customer, product, total, instructions, and inventory
  consequence copy before cancelling and then confirming the same order once;
- reviewing cancellation consequences before leaving an order unchanged, then
  cancelling that exact captured order once without inventory movement;
- reviewing completion quantities and inventory deductions before leaving an
  order unchanged, then completing it once and verifying the non-repeatable receipt;
- cancelling invoice destination selection with no service call, refusing an
  existing-file replacement without changing its bytes, then explicitly
  approving and byte-verifying the selected order invoice;
- closing a rendered customer-statement review without exporting, refusing an
  existing-file replacement, then reopening the same account range and
  explicitly approving its byte-verified reviewed PDF;
- following a visible Market Day order card into the exact CRM order despite
  competing rows, returning to the retained packing list, then following its
  unit issue back to the exact saved product line and correction fields;
- completing two checked Market Day loads with one success and one failure,
  proving the completed order cannot be retried while the failed load keeps its
  packed check, then completing only that retained order after refresh;
- addressing repeated Dashboard labels through unique accessible selectors and
  proving each metric-card and Today’s Work action reaches its intended workflow;
- laying out Harvest at the compact 1050×700 desktop contract and measuring
  effective visibility through every ancestor and split-pane boundary, so a
  present-but-clipped history table, crop input, quantity input, or primary save
  action fails the journey;
- cancelling inventory deletion and market-day completion before explicitly
  confirming each destructive action once;
- keeping activity-history clearing unavailable to HAND while requiring an
  OWNER to confirm it, with exports and the empty timeline still usable;
- recursively cataloging all 36 reachable leaf routes across every primary and
  nested tab—including Dashboard charts, all Harvest workbenches, all Season
  Planner views, CRM Customers and Orders, report evidence tabs, trend model
  details, and every Reference view—and auditing at least 180 visible fields,
  selectors, buttons, tables, spinners, sliders, and tab sets for missing or
  ambiguous selectors and unwired production listeners;
- composing that same production catalog behind the live runtime plugin,
  publishing all 36 exact paths, opening each path through `navigate-route`, and
  rejecting any visible control whose selector is blank, ambiguous across
  control kinds, or unsupported;
- running a recursive whole-catalog button storm across those routes: at least 65
  distinct enabled controls are discovered and at least 55 are activated through their production
  listeners, modal and save boundaries cancel safely, no discovered control is
  skipped, and any exception escaping an asynchronous Swing callback fails the
  journey instead of merely printing to the console;
- running an exact route-aware option matrix before the button storm: every
  current combo item, bounded spinner value, slider/scroll position, table row,
  table sort direction, list entry, toggle/radio state, editable-text boundary,
  Enter action, and tab choice must be exercised, reported, and restored; a
  separate non-empty fixture proves every supported control family rather than
  relying on whichever rows happen to be returned by service doubles;
- auditing every installed-shell menu item for a unique path, mnemonic,
  accessible description, role state, and live action listener; safely activating
  all non-terminal/local actions while explicitly excluding process exit and
  externally coordinated imports from the in-process sweep;
- opening and closing the integrations tray through its production toggle and
  activating all 21 connector actions through their real listeners with safe
  file and connector results;
- addressing the login username and password by accessible name and verifying
  their keyboard mnemonics and label associations;
- combining multiple event-service rows through the same multi-selection model
  advertised to workers, building the resulting brief, and verifying exact
  clipboard payloads through the replaceable system-clipboard boundary;
- invoking CRM's installed F6 focus-cycle and Ctrl+Enter product-submit key
  bindings rather than bypassing their action maps;
- moving all four Rose Visualizer sliders through their production change
  listeners and verifying the displayed values;
- requiring every enabled worker-facing control on all recursive routes to
  accept keyboard focus, which caught and corrected four mouse-only Dashboard
  metric actions;
- running an opt-in installed-desktop smoke in a real always-on-top window:
  native pointer events type and clear a search, native Tab/Shift+Tab and
  composite-control Ctrl+Tab walk the Event Services focus loop, Home/Down/Enter
  filters memorial photography, the header is physically sorted, both rows are
  Ctrl/Cmd-clicked, and the build/copy/CRM actions are physically clicked before
  an actual screen capture is inspected;
- opening the real installed dialog and save-file chooser boundaries, proving
  focus transfers into each modal, inspecting the suggested filename, physically
  dismissing them through OK/Cancel, verifying cancellation creates no file,
  restoring application focus, and inspecting full-window captures.

The driver supports normal text entry, Enter-key submission, selections,
checkbox/toggle changes, spinner and slider changes, visible-label and accessible-name
button clicks, tab selection, table sorting, single- and multi-row selection,
real table double-click dispatch, installed Swing key bindings, and
table-row selection by index or exact visible column value, and
asynchronous waits for visible text, options, action enablement, table rows, and table values. It
can also inspect retained field values, current selector choices, checked state,
field editability, button availability during slow or failed work, and the exact
visible extent of named controls after forcing a production surface to a target
desktop size. The extent calculation intersects every ancestor rather than
trusting a component's nominal bounds, which exposes split-pane and viewport
clipping that ordinary component assertions miss.
Named `typeCase` matrices preserve each boundary case in the action transcript,
so failures identify the exact malformed or valid data row being exercised.
`SwingDialogHarness` scripts native option, confirmation, and message decisions
by title and visible option text. It asserts rendered dialog copy and can edit
named text fields, selectors, and spinners inside the real production content
before answering. Selector matching follows custom renderer text, so journeys
use the same option labels a worker sees rather than internal enum names.
`SwingFileHarness` queues cancel/save outcomes, verifies the
chooser title and suggested filename, and returns a test-owned destination.
`SwingMenuHarness` inventories the entire menu tree, proves each enabled command
has a mnemonic and listener, and records intentional exclusions for terminal or
externally coordinated actions. `FarmClipboard` makes exact clipboard success
and unavailable-clipboard recovery deterministic without replacing the installed
application's system clipboard behavior.
`InstalledDesktopHarness` is the complementary opt-in layer. It mounts a
production component tree in a real `JFrame`, keeps the test window foregrounded,
sends OS-level `Robot` input, proves the intended component received each mouse
event, matches visible combo renderer text, records native focus owners, drives
header sorting and multi-row selection, transfers focus into titled modal
windows, inspects a real `JFileChooser`, and captures complete native-window
pixels. Its one bounded retry records and recovers transient OS focus/key loss;
repeating the same failure still fails the smoke.
All harness layers fail immediately when the product contract changes.

These test-only drivers are complemented by a customer-facing runtime SPI.
`HarnessGateway` discovers `HarnessPlugin` beans and applies an independent
token, plugin-declared risk policy, typed arguments, and replay-safe request IDs
before the built-in plugin reaches the live Swing event-dispatch thread. Runtime
observations expose selectors and state, not editable field or password values.
The REST transport is disabled by default; see [AI_HARNESS.md](AI_HARNESS.md).
For broad exploratory passes, their opt-in safe-cancel modes close unscripted
dialogs and file choosers without approving mutations while preserving a full
transcript. `clickEveryEnabledButton` takes a pre-action surface snapshot,
activates every application-owned button, waits for UI state to settle between
clicks, and reports discovered, clicked, and skipped controls. An
`AsyncFailureMonitor` turns otherwise-easy-to-miss AWT/background exceptions
into test failures.
`exerciseEveryOptionOnEveryTabSurface` recursively discovers the controls on
each leaf route, records every available/exercised choice, uses production
listeners and models, restores the original selection/value/viewport, and fails
the integration journey on any mismatch. It covers combos, bounded spinners,
sliders, scroll panes, tables, lists, toggles/radios, editable text, Enter, and
tabs. `SwingMenuHarness.exerciseEveryToggleState` applies the same exact-state
contract to shell menu toggles.

## Adding a journey

Build a normal tab with a deterministic service double, wrap its root component,
then interact only through user-facing selectors:

```java
SwingUserHarness user = SwingUserHarness.on(build(tab));
user.type("Report start date", "2026-08-01")
        .type("Report end date", "2026-08-28")
        .click("Preview range")
        .awaitText("Range preview ready")
        .selectTab("Products");

assertThat(user.tableRows("reports.productPerformance")).isPositive();
```

Use accessible names for fields, selectors, and toggles; visible text for
buttons and tabs; and stable production component names for result tables and
metrics. Assert both what the user sees and the exact model passed to the
service. Every journey keeps an action transcript for failure diagnosis.

Run only the journeys during development:

```text
mvn -Dtest=UserJourneyHarnessTest test
```

Run the full quality gate before merging:

```text
mvn verify
mvn -Pquality -DskipTests verify
```

Run the installed-desktop smoke separately on a workstation whose pointer and
keyboard may be temporarily controlled:

```text
mvn -Pinstalled-desktop-smoke test
```

Its screenshots are written below `target/installed-desktop-smoke/` as
`event-services.png`, `native-dialog.png`, and `native-file-chooser.png`. The
profile intentionally runs only the two native smokes so ordinary headless tests
do not compete with their windows.

## Expansion matrix

Add scenarios in this order so the harness grows around farm risk:

1. Extend the installed-app layer to multi-monitor high-DPI rendering, then add
   stateful rapid input sequences that alternate filters, refreshes, roles, and
   destructive review cancellation without bypassing production controls.

The zero-data sweep now covers Dashboard, Harvest, Inventory, CRM, Market Day,
Trend Analysis, Season Planner, Knowledge Log, and Reports. OWNER-only history
clearing, VIEWER/HAND write switching, repeat-click protection, and
representative slow/failing background services are also covered.
Populated-to-stale recovery is now covered end to end for Dashboard, Harvest,
Inventory, CRM, Market Day, Reports, and Trend Analysis.
Dashboard now has source-specific partial-update coverage, and Reports has
latest-range coalescing plus exact previous-range labeling after failure.
CRM now has source-specific atomic refresh coverage, and Trend Analysis has
latest-series/window coalescing with superseded-result rejection.

Dialog content, entered values, option selection, cancellation, clipboard
payloads, and resulting workflow/data behavior are deterministic harness
responsibilities. Native window focus, pointer delivery, typing, Tab traversal,
dialog/file-chooser cancellation, suggested-file inspection, and screen capture
have an automated installed-app layer. Native file approval through platform
chrome, screen-reader speech, and cross-monitor DPI remain explicit manual or
future-smoke responsibilities.
