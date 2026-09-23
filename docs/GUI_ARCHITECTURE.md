# Flower Farm Manager GUI — Architecture

**Location Focus**: Kitsap County, Washington (Port Orchard area) — PNW West of the Cascades
**Theme**: Professional, practical tool for a serious flower farmer / market gardener.

## Core Philosophy

> From a single monolithic Swing class → a **modular, extensible, dashboard-driven desktop application** that feels like a real business tool.

### Key Architectural Decisions

| Concern                  | Old Approach                  | New Approach                                 | Why |
|--------------------------|-------------------------------|----------------------------------------------|-----|
| Tab Organization         | Everything in one class       | `FlowerFarmTab` interface + implementations  | Separation of concerns, testability, scalability |
| UI Construction          | Inline in `initialise()`      | Each tab builds & owns its own panel         | Cleaner, easier to reason about |
| Data Refresh             | Manual `refreshTable()` calls | `refreshData()` contract + `TabHost.refreshAll()` | Consistent behavior across features |
| Cross-tab actions        | Direct field access           | `TabHost` callback (refresh / select / status / trend) | Tabs stay decoupled from the frame |
| Search                   | Separate top panel            | Live filter inside `InventoryTab`            | Better UX |
| Visual Feedback          | Basic table                   | Color-coded stock + KPI Dashboard            | At-a-glance operational awareness |
| Extensibility            | Hard                          | Easy to add new tabs (Reports, Harvest Log, Customers, Suppliers) | Long-term maintainability |

## Harvest entry state and layout

Harvest quick entry tracks an immutable raw-field snapshot plus the saved edit
target. Clean baselines are captured after initial setup, a saved-row load, an
explicit clear, or successful save preparation. Copy and failed-save markers keep
meaningful drafts protected even if their fields equal a baseline. Replacement
dialogs recheck the reviewed snapshot and saved row after returning from Swing's
modal event loop. `HarvestBatchView` delegates sample replacement approval to the
tab before writing any text. The frame queries Harvest's unfinished/pending state
when closing; it does not treat pending writes as reversible drafts.

Harvest uses width-tracking vertical workspace scrolling, a scrollable two-column
entry form, and explicit rows for actions, status, filters, and conditional SKU
setup. `FarmUi.wrappingStatusBadge` is intended for a dedicated full-width row:
it measures HTML renderer layout in actual pixels while keeping plain label and
accessible text. History tables retain readable identity columns and use horizontal
scrolling for extended record detail.

## Package Structure (as built)

```
com.flowerfarm.gui
├── FlowerFarmGUI.java          # Lightweight orchestrator (Spring @Component), implements TabHost
├── FarmTabCatalog.java         # Stable feature composition and primary-navigation order
├── FarmTabNavigator.java       # Refresh, named selection, mnemonics, and keyboard shortcuts
├── FarmUi.java                 # Shared spacing, typography, buttons, tables, badges, cards, and errors
├── presenter/                  # CRM, harvest, and connector interaction orchestration
├── view/                       # Reusable workflow-specific Swing components
└── tabs/
    ├── FlowerFarmTab.java      # Core contract
    ├── TabHost.java            # Cross-tab coordination contract (refreshAll, selectTab, setStatus, runTrendAnalysis)
    ├── AbstractInfoTab.java    # Base for read-only content tabs
    ├── DashboardTab.java       # KPIs, low-stock alerts, quick harvest-forecast action
    ├── InventoryTab.java       # Live search, sortable columns, color-coded stock, edit/delete, CSV export
    ├── HarvestLogTab.java      # History, filters, single/batch entry, and production totals
    ├── KnowledgeLogTab.java    # Searchable observations, actions, results, and follow-ups
    ├── CrmTab.java             # Customer and order workspaces with role-aware actions
    ├── MarketDayTab.java       # Packing-plan scope, exports, and fulfillment
    ├── SyncHistoryTab.java     # Searchable connector audit timeline and exports
    ├── AddItemTab.java         # Clean form with conditional Rose Type combo
    ├── TrendAnalysisTab.java   # Real weekly-harvest forecast (SwingWorker); exposes runAnalysis()
    ├── PnwPlantGuideTab.java   # 94 sourced regional plants + plan-draft handoff
    ├── SeasonPlannerTab.java   # Timing, beds, pace, insights, and availability outlook
    ├── RoseVarietiesTab.java   # PNW rose guide + add-sample action
    ├── PricingInfoTab.java     # Static reference
    ├── IrrigationInfoTab.java  # Kitsap live/climatology irrigation advisor
    └── RoseVisualizerTab.java  # L-System generative roses (Java2D turtle)
```

L-System engine lives under `com.flowerfarm.lsystem` (`LSystem`, presets, season palettes).

The orchestrator owns only frame-level concerns: the frame, menu bar, collapsible
integrations tray, status bar, and session badge. `FarmTabCatalog` composes
features, `FarmTabNavigator` owns tab navigation and shortcuts, and
`ConnectorActionPresenter` keeps connector execution/result mapping outside the
frame while `SwingWorker` keeps that work off the event-dispatch thread.

`FarmUi` is the GUI design-system boundary. It centralizes page and section
spacing, semantic button treatments, accessible search fields, table behavior,
empty states, status badges, theme-aware colors, and actionable error messages.
Tabs still own page-level interaction policy and dialogs. The CRM and harvest
tabs delegate parsing, projection, and workflows to UI-independent presenters;
small reusable view components own their local Swing composition. Other tabs may
call application services directly when their interaction remains cohesive.
No GUI component calls REST controllers or persistence repositories; the same
service boundary is shared with REST and CLI and is enforced by
`ArchitectureBoundaryTest`.

`DesktopSession` is a Spring-owned instance for the current single-frame desktop
application. The orchestrator exposes only capabilities and display identity to
tabs through `TabHost`; no tab reads global session state. REST authorization
uses Spring Security and never trusts desktop identity.

## Status

### Phase 1 — Modular refactor ✓ (complete, merged into `src/`)
- Modular tab architecture ✓
- `TabHost` cross-tab coordination ✓
- Dashboard with KPIs + low-stock alerts ✓
- Live filtering + sortable columns + color-coded stock in Inventory ✓
- CSV export of visible rows + full-inventory CSV export ✓
- All extracted tabs in their own files ✓
- Menu bar, connector bar, status bar, and F5 all wired ✓

### Phase 2 — Charts & theming ✓
- **JFreeChart** on Dashboard: inventory value by category (pie), quantity by item (bar) ✓
- **FlatLaf** light/dark mode via **View → Dark Mode** ✓
- Persistent preferences: window size, last tab, theme (`java.util.prefs`) ✓

### Phase 2b — Connectors ✓
- Full implementations: CSV, Excel, Airtable, Webhook, **Shopify**, **Square**, **Google Sheets** ✓
- GUI connector bar includes Shopify / Square import-export-sync and Google Sheets import/export ✓
- Farmbrite and Floranext support local mirrors and live endpoints; obsolete POS
  stubs have been removed ✓

### Phase 3 — Business features ✓
- Harvest logging tab + REST + season totals ✓
- Sync history / audit log (connector ops + CRM fulfill auto-recorded) ✓
- Customer / Order CRM tab + REST; **fulfill decrements inventory** ✓
- PDF weekly harvest + sales reports (branded OpenPDF + `/api/reports`) ✓
- Dashboard quick actions: Harvest, CRM, Reports, Sync History, Trends ✓
- GUI connector bar: implemented only (stubs hidden) ✓
- Optional SQLite profile (`application-sqlite.properties`) ✓
- Farmbrite + Floranext dual-mode (local JSON mirror + REST); full offline round-trip ✓
- Harvest log auto-increments inventory (HARVEST_LOG audit) ✓
- Harvest edit corrects inventory (HARVEST_EDIT audit) ✓
- Harvest delete reverses inventory (HARVEST_UNDO audit) ✓
- Harvest filter bar + CSV export ✓
- Dashboard KPIs: week harvest qty + week revenue ✓
- Optional barn auth (`auth` profile + GUI login gate) ✓
- Dual-mode connectors (local JSON mirrors for Shopify/Square/Sheets/Airtable/Webhook + Farmbrite/Floranext) ✓
- Harvest batch/filter/export; CRM confirm/fulfill; audit filter/export ✓
- Auth UX: session badge, switch user, VIEWER write guards ✓
- Demo runbook: `docs/DEMO.md` + `scripts/demo-rest.*` ✓
- Non-essential POS stubs removed from registry ✓

### Phase 3b — Persistence ✓
- `InventoryRepository` port + JPA/H2 file store (`./data/flowerfarm`) ✓
- `Item` is a JPA entity with generated id; index APIs kept for Swing ✓
- First-run seed from CSV or PNW sample data ✓

### Phase 4 — Rose visualizer and richer operations ✓
- Generative rose visualizer (Java2D L-Systems) ✓
  - Animate grow, mutate, PNG export, save/load rulesets (`data/lsystems/`)
  - Inventory rose SKU → growth habit mapping
- Multi-user barn roles OWNER / HAND / VIEWER ✓
- Optional SQLite dialect ✓
- Richer schema (harvest, CRM, sync history) ✓

### Phase 5 — Operations interface modernization ✓
- Cohesive page headers, cards, toolbars, typography, spacing, and semantic actions via `FarmUi` ✓
- Persistent left-side navigation with Alt+number shortcuts and a collapsible integrations tray ✓
- Dashboard, Inventory, Harvest Log, CRM, Market Day, and Sync History reorganized around daily workflows ✓
- Reports, Irrigation, Rose Varieties, and Trend Analysis use the same page,
  section, action, status-badge, and accessible-output patterns ✓
- Clear empty states, live status indicators, stock severity colors, and recovery-oriented error messages ✓
- OWNER / HAND / VIEWER mutation permissions preserved; read-only exports remain available where appropriate ✓
- Accessible names, descriptions, label associations, mnemonics, and keyboard navigation added to priority controls ✓
- Headless GUI contract tests cover shared components, priority layouts, empty states, and role behavior ✓

### Phase 6 — Interaction-shell decomposition ✓
- CRM and harvest validation, projections, and service workflows moved to presenters ✓
- Tab catalog and navigation/shortcut policy extracted from the main frame ✓
- Connector result mapping extracted from the main frame ✓
- CLI menu dispatch and printable-report handlers extracted into focused collaborators ✓
- Headless unit and architecture tests lock the new boundaries ✓

### Phase 7 — Everyday-user polish ✓
- Primary morning, harvest, customer-order, market-day, reporting, and closeout
  workflows use consistent plain-language actions, feedback, and recovery cues ✓
- Dynamic status badges update their accessible descriptions as state changes ✓
- Invalid dates, quantities, names, and prices are highlighted at the field with
  corrective guidance while preserving business behavior ✓
- OWNER / HAND / VIEWER access is stated directly on mutation-heavy screens;
  view and export paths remain usable for VIEWER ✓
- Activity History replaces implementation-facing audit terminology in the
  desktop while retaining saved operation values and compatibility aliases ✓
- `docs/USER_GUIDE.md` documents daily workflows, role behavior, keyboard
  navigation, and mistake recovery ✓
- Headless contracts cover shared feedback primitives and polished priority
  workflows ✓

### Phase 8 — Data-entry resilience ✓
- Add and edit inventory share one complete accessible form and presenter-owned validation ✓
- CRM supports multiple product lines per order with safe blank product/price defaults ✓
- Harvest, customer, order, and market-day errors identify the exact field and preserve input ✓
- Long order actions use a stable two-row layout; rendered geometry tests reject clipped controls ✓
- Rose ruleset save/load uses accessible validated forms instead of raw input dialogs ✓
- Connector construction and desktop session lifecycle use one Spring-owned policy each ✓

### Production Studio usability and refresh safety

Production Studio separates planning from field work and uses full-width forms
with associated labels and vertical page scrolling. Wide record tables retain
their own horizontal scrollbars. The task editor repeats the selected task's
identity; recovery messages wrap without exposing internal control identifiers.

Full Studio refresh, frost reload, and task actuals saves capture inputs on the
EDT, call services through SwingWorker, and apply eligible results on the EDT.
Refresh generations and query comparisons reject superseded results. Frost
baselines and task drafts keyed by stable ID retain unsaved work; changed task
context or an unsuccessful task read requires review before further task writes.
The catalog exposes Production Studio to the frame's existing close guard.
Other template, recipe, and handoff writes retain their existing synchronous
service calls; this pass does not claim all Studio operations are asynchronous.

### Customer and packing action decisions

Customer form buttons and action entry points share access and current-state
predicates. Keyboard submission and double-click loading cannot bypass a pending
write, queued/running refresh, or saved-result recovery lock. Writes additionally
remain blocked after a failed CRM refresh until it succeeds. Customer deletion
captures an immutable row, raw form, edit identity, and write revision before showing the injected dialog;
the approval is checked again before dispatch, including current host access.

Customer forms keep a clean raw-field/edit-ID baseline. Document and type changes
advance a revision; failed saves remain dirty even when values match the baseline.
User-directed load/clear actions ask before replacing dirty work, capturing both
form/write revisions and the selected load target. Affirmative decisions recheck
the form, target, access, pending result, and refresh-failure state. Only explicit
load/clear, an acknowledged save, or deletion of the loaded account resets the
baseline. Refreshes and handoffs leave it intact, and the existing shell close
guard includes customer drafts. Draft state is local to the running desktop session.

Market Day captures the reviewed plan, checked-load signatures, and a decision
revision. Scope, role, check, refresh, and submission changes invalidate older
approvals. A stale preflight rejection requires a successful plan rebuild before
completion is enabled again. Refreshes during completion queue behind its result,
preserving the captured packing evidence. These desktop guards complement the
existing service preflight and transactional per-order checks.

### Inventory save and refresh outcomes

Inventory workers return an acknowledged mutation separately from their subsequent
inventory readback. A readback or host-refresh failure cannot seed a retry draft
for a committed write. Failed refreshes retain visible rows and a persistent
recovery message while all count/edit/delete entry points require a fresh snapshot.
Refresh requests during writes queue until completion; count-next continuation
waits through the host's inventory refresh and rechecks its captured target.
Modal decisions compare the reviewed product fields and current access before
dispatching a write. These desktop checks do not provide database record-version
preconditions against concurrent clients. The frame includes inventory pending
writes and retained drafts in its close guard. Header feedback wraps on dedicated
rows, keeping the Refresh action beside the title. The workspace scrolls vertically
at compact sizes; a usable table height and separate stock/report action rows keep
inventory rows and every action reachable.

Retained inventory drafts use canonical product-ID/operation keys; idless legacy
items use name/category/unit. Immutable count/edit entries retain raw fields,
their original product snapshot, and any duplicate-name conflict. Refresh never
prunes missing drafts. Acknowledged writes clear only the captured operation;
deletion clears only captured drafts for that product. Reference checks reject
modal decisions made before a draft was changed, discarded, or resubmitted.
The retained-draft review view is read-only and supports copy, exact-product
navigation, and a separate confirmed local discard. Saved baseline changes are
shown during retry; draft maps remain session-local and participate in exit guards.

---

**This architecture respects the existing Spring Boot + service layer** while
giving the GUI room to grow into a tool you could actually run a flower farm
business with. Built for PNW flower growers in Kitsap County.
