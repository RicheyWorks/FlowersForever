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
    └── IrrigationInfoTab.java  # Static reference
```

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

### Phase 2 (next, high impact)
- Integrate **JFreeChart**: inventory value by category (pie), quantity trends (line), low-stock heatmap.
- Add **FlatLaf** theming + dark-mode toggle (hook already present in `FlowerFarmGUI.initialise()`).
- Persistent user preferences (window size, last tab, theme).

### Phase 3 (business features)
- Harvest logging tab; simple Customer / Order CRM.
- Financial summary (COGS, revenue estimates); PDF report generation.
- Weather-aware irrigation recommendations (local Kitsap data).

### Phase 4 (advanced / fun)
- Generative rose-variety visualizer (L-Systems / p5.js style).
- Local SQLite/H2 persistence instead of in-memory + CSV.
- More dynamic connector plugin system.

---

**This architecture respects the existing Spring Boot + service layer** while
giving the GUI room to grow into a tool you could actually run a flower farm
business with. Built for PNW flower growers in Kitsap County.
