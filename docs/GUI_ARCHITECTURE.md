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

## Package Structure (as built)

```
com.flowerfarm.gui
├── FlowerFarmGUI.java          # Lightweight orchestrator (Spring @Component), implements TabHost
└── tabs/
    ├── FlowerFarmTab.java      # Core contract
    ├── TabHost.java            # Cross-tab coordination contract (refreshAll, selectTab, setStatus, runTrendAnalysis)
    ├── AbstractInfoTab.java    # Base for read-only content tabs
    ├── DashboardTab.java       # KPIs, low-stock alerts, quick "Run Trend Analysis" action
    ├── InventoryTab.java       # Live search, sortable columns, color-coded stock, edit/delete, CSV export
    ├── AddItemTab.java         # Clean form with conditional Rose Type combo
    ├── TrendAnalysisTab.java   # Weka LinearRegression forecast (SwingWorker); exposes runAnalysis()
    ├── RoseVarietiesTab.java   # PNW rose guide + add-sample action
    ├── PricingInfoTab.java     # Static reference
    ├── IrrigationInfoTab.java  # Static reference
    └── RoseVisualizerTab.java  # L-System generative roses (Java2D turtle)
```

L-System engine lives under `com.flowerfarm.lsystem` (`LSystem`, presets, season palettes).

The orchestrator owns only frame-level concerns: the `JTabbedPane`, menu bar,
connector button bar (14 import/export connectors wired through
`ConnectorRegistry` via `SwingWorker`), status bar, and the F5 "refresh all"
shortcut. Everything else lives in a tab.

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

### Phase 2b — Connectors (in progress)
- Full implementations: CSV, Excel, Airtable, Webhook, **Shopify**, **Square**, **Google Sheets** ✓
- GUI connector bar includes Shopify / Square import-export-sync and Google Sheets import/export ✓
- Stubs remain for Farmbrite, Floranext, other POS tools (gated by `isAvailable()`)

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

### Phase 4 (advanced / fun) — in progress
- Generative rose visualizer (Java2D L-Systems) ✓
  - Animate grow, mutate, PNG export, save/load rulesets (`data/lsystems/`)
  - Inventory rose SKU → growth habit mapping
- Multi-user barn roles OWNER / HAND / VIEWER ✓
- Optional SQLite dialect ✓
- Richer schema (harvest, CRM, sync history) ✓

---

**This architecture respects the existing Spring Boot + service layer** while
giving the GUI room to grow into a tool you could actually run a flower farm
business with. Built for PNW flower growers in Kitsap County.
