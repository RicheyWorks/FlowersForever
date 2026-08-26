# FlowersForever — Flower Farm Manager

[![CI](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml/badge.svg)](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/RicheyWorks/FlowersForever?include_prereleases&sort=semver)](https://github.com/RicheyWorks/FlowersForever/releases)

**Practical desktop + REST inventory for PNW flower growers**  
Port Orchard / Kitsap County · roses, stems, tools, supplies · west of the Cascades.

Spring Boot **3.5.16** · modular Swing GUI · dual-mode connectors (offline JSON mirrors **or** live REST) · fat JAR **1.0.19**.

---

## Features

| Area | Highlights |
|------|------------|
| **Inventory** | H2/SQLite stock; harvest ↑ / fulfill ↓; low-stock reorder + **price list** PDFs |
| **Harvest** | Log / edit / batch / filter; bed production + chronological **harvest log** PDFs |
| **Customers & orders** | Draft and confirm orders; completion deducts stock; **invoice** + **statement** PDFs |
| **Dashboard** | Weekly harvest and sales overview, plain-language alerts, and quick actions |
| **Market day** | Packing list vs stock, packing PDF, and confirmed-order completion |
| **Day ops** | **Morning briefing** + **day closeout** sheets (pack, beds, water, stock, sales) |
| **Irrigation** | Kitsap Open-Meteo live or offline climatology |
| **Activity history** | Imports, exports, harvests, and orders; filter, CSV, and printable PDF |
| **Connectors** | CSV, Excel, Airtable, Webhook, Shopify, Square, Sheets, Farmbrite, Floranext — dual-mode |
| **Auth** | Optional OWNER / HAND / VIEWER barn roles (`auth` profile) |
| **Demo** | `demo` profile seeds Kitsap CRM + harvest + today’s CONFIRMED orders when empty |
| **Reports / CLI** | Weekly harvest + sales PDF; full ops menu via `--cli` |
| **Rose visualizer** | L-Systems: grow, mutate, save rulesets, inventory link, PNG |

---

## Requirements

- **JDK 17+**
- **Maven 3.9+**

| Automation | Behavior |
|------------|----------|
| **CI** | Every push/PR to `main` → `mvn clean verify` on JDK 17 + 21 (advisory), Docker build, static-analysis job |
| **Quality** | JaCoCo report + 51% line / 41% branch gates; Medium+ SpotBugs gate + Checkstyle report via `-Pquality` |
| **Dependabot** | Weekly Maven + GitHub Actions PRs |
| **Supply chain** | CodeQL scanning, PR dependency review, CycloneDX SBOM on every build |
| **Releases** | Tag `v*` → fat JAR + SBOM on GitHub Releases, image to GHCR, MSI/DMG installers via jpackage |
| **Security** | Auth model & hardening — **[SECURITY.md](SECURITY.md)** |

System map: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**. Decisions and
trade-offs: **[ADR-001](docs/adr/ADR-001-infrastructure-and-build-expansion.md)**
and **[ADR-003](docs/adr/ADR-003-layered-application-boundaries.md)**.
Report rendering boundaries are recorded in
**[ADR-004](docs/adr/ADR-004-report-rendering-collaborators.md)**, and desktop
interaction decomposition in
**[ADR-005](docs/adr/ADR-005-desktop-interaction-collaborators.md)**.
Everyday desktop workflows, role behavior, keyboard use, and mistake recovery
are covered in the **[desktop user guide](docs/USER_GUIDE.md)**.
Farm rollout and recovery are covered by the **[deployment checklist](docs/DEPLOYMENT.md)**
and **[backup/restore procedure](docs/BACKUP_RESTORE.md)**. Review the
**[known limitations](docs/KNOWN_LIMITATIONS.md)** before approving a workstation.

Native MSI and DMG installers are currently unsigned. Windows SmartScreen or
macOS Gatekeeper may show an unknown-publisher warning; verify that the download
comes from this repository's GitHub Releases page. See
**[ADR-002](docs/adr/ADR-002-code-signing.md)** for the signing decision and
adoption trigger.

### Docker (headless REST-only)

```bash
docker build -t flowersforever .
docker run -p 8080:8080 -v flowerfarm-data:/app/data \
  -e FLOWERFARM_AUTH_USERS='owner:replace-with-a-long-password:OWNER' \
  flowersforever
# or from GHCR after a release:
docker run -p 8080:8080 \
  -e FLOWERFARM_AUTH_USERS='owner:replace-with-a-long-password:OWNER' \
  ghcr.io/richeyworks/flowersforever:latest
```

The image activates the fail-closed `container` profile: HTTP Basic is mandatory,
every password must contain at least 12 characters, and at least one account must
have the `OWNER` role. `/actuator/health` remains public for container health checks.

```bash
git tag v1.0.19
git push origin v1.0.19
```

---

## Quick start

```bash
git clone https://github.com/RicheyWorks/FlowersForever.git
cd FlowersForever
mvn clean verify

# GUI + REST on loopback :8080
mvn spring-boot:run
# or
java -jar target/flowerfarm-manager-1.0.19.jar

# CLI
java -jar target/flowerfarm-manager-1.0.19.jar --cli

# Profiles (combine with commas)
java -jar target/flowerfarm-manager-1.0.19.jar --spring.profiles.active=sqlite
FLOWERFARM_AUTH_USERS='owner:replace-with-a-long-password:OWNER' \
  java -jar target/flowerfarm-manager-1.0.19.jar --spring.profiles.active=auth
java -jar target/flowerfarm-manager-1.0.19.jar --spring.profiles.active=demo
java -jar target/flowerfarm-manager-1.0.19.jar --spring.profiles.active=demo,auth
```

| Profile | Purpose |
|---------|---------|
| *(default)* | H2 file DB + GUI + REST |
| `sqlite` | SQLite under `./data/flowerfarm.sqlite` |
| `auth` | HTTP Basic + GUI login; requires strong credentials from environment |
| `demo` | Non-destructive seed of CRM / harvest / today’s CONFIRMED orders when empty |
| `cli` | Interactive CLI only (no Tomcat) |
| `container` | Headless external bind + mandatory non-demo HTTP Basic credentials |

Empty DB seeds from `farm_inventory.csv` if present, else sample PNW SKUs. Portable JAR store: `./data/flowerfarm.mv.db`.
The packaged Windows app uses `%LOCALAPPDATA%\\FlowersForever\\data`; override either mode with
`FLOWERFARM_DATA_DIR`. On its first packaged launch, legacy `./data` content is copied forward
without deleting the source.
Open desktop mode listens only on `127.0.0.1`; the `auth` profile enables LAN binding. REST-triggered CSV exports are written beneath `./data/exports`.

### Portfolio demo

Full runbook: **[docs/DEMO.md](docs/DEMO.md)**

```powershell
# App must already be running on :8080
powershell -File scripts/demo-rest.ps1
powershell -File scripts/demo-rest.ps1 -User farm -Pass kitsap   # with auth
```

**Happy path (~5 min)**

1. **Dashboard** — weekly overview, morning briefing / day closeout
2. **Harvest Log** — log Nootka Rose stems → stock ↑; bed production + harvest log PDFs
3. **Customers & Orders** — confirm an order → invoice PDF; complete it → stock ↓
4. **Market Day** — packing list / PDF → complete confirmed orders
5. **Inventory** — low-stock reorder + price list PDFs
6. **Activity History** — review successful work or items needing attention
7. **Connectors** — Export Farmbrite/Shopify (local mirrors, no API keys)
8. Optional **auth** — switch to `viewer` (writes blocked)

---

## Connectors (dual-mode)

**Default:** local JSON under `data/` (offline demos / CI).  
**Live:** clear `local-file` and set credentials in `application-local.properties` (gitignored).

| Name | Directions | Offline mirror | Live keys |
|------|------------|----------------|-----------|
| **csv** | I/E/S | file paths | — |
| **excel** | I/E | `.xlsx` | — |
| **farmbrite** | I/E/S | `data/farmbrite-mirror.json` | api-key + account-id |
| **floranext** | I/E/S | `data/floranext-mirror.json` | api-key + store-url |
| **shopify** | I/E/S | `data/shopify-mirror.json` | shop-name + api-token |
| **square** | I/E/S | `data/square-mirror.json` | access-token (+ location) |
| **google-sheets** | I/E/S | `data/google-sheets-mirror.json` | spreadsheet-id + key/token |
| **airtable** | I/E/S | `data/airtable-mirror.json` | api-key + base-id |
| **webhook** | E | `data/webhook-last-payload.json` | url (+ secret HMAC) |

`GET /api/connectors` includes `mode` / `localMode`. Details: **[docs/CONNECTORS_SANDBOX.md](docs/CONNECTORS_SANDBOX.md)**.

---

## Auth (optional)

| Role | Write | Clear audit | Typical use |
|------|-------|-------------|-------------|
| **OWNER** | yes | yes | Farm manager |
| **HAND** | yes | no | Harvest crew |
| **VIEWER** | no | no | Office / guest |

```bash
export FLOWERFARM_AUTH_USERS='owner:replace-with-a-long-password:OWNER,hand:another-long-password:HAND'
java -jar target/flowerfarm-manager-1.0.19.jar --spring.profiles.active=auth
```

GUI: **Account → My access / Switch user**.
REST: `GET /api/auth/me` (Basic), `GET /api/auth/accounts` (usernames only).

---

## REST API (selected)

### Core

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/dashboard` | Inventory + harvest week + revenue KPIs |
| GET | `/api/inventory` | List / search / kpis |
| GET/POST | `/api/harvest` | Harvest CRUD |
| POST | `/api/harvest/batch` | Batch log |
| GET | `/api/harvest/week` | 7-day total + daily series |
| GET | `/api/harvest/filter` | crop / bed / notes / dates |
| GET/POST | `/api/customers` | CRM customers |
| GET/POST | `/api/orders` | Orders |
| GET | `/api/orders/week` | Realized + pipeline + sparklines |
| POST | `/api/orders/{id}/confirm` | → CONFIRMED pipeline |
| POST | `/api/orders/{id}/fulfill` | Deduct inventory |
| GET | `/api/auth/me` | Session / role |
| GET | `/actuator/health` | Health |

### Printables & ops sheets

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/inventory/low-stock/report.pdf` | Low-stock reorder (`threshold`) |
| GET | `/api/inventory/price-list/report.pdf` | Price list (`inStockOnly`) |
| GET | `/api/harvest/beds/report.pdf` | Bed / field production |
| GET | `/api/harvest/log/report.pdf` | Chronological harvest log |
| GET | `/api/orders/{id}/invoice.pdf` | Wholesale invoice |
| GET | `/api/customers/{id}/statement.pdf` | Account statement (`from` / `to`) |
| GET | `/api/market-day/packing.pdf` | Market packing list |
| POST | `/api/market-day/fulfill` | Fulfill all CONFIRMED in window |
| GET | `/api/briefing/report.pdf` | Morning briefing |
| GET | `/api/closeout/report.pdf` | Day closeout |
| GET | `/api/reports/weekly.pdf` | Trailing week report |
| GET | `/api/connectors/history/report.pdf` | Audit history |
| GET | `/api/irrigation/advice` | Kitsap water plan (`live=true\|false`) |

Most printable routes also expose JSON and plain-text siblings (`/text` or bare path without `.pdf`).  
Full market-day, filter, and connector endpoints: see controllers under `com.flowerfarm.controller`.

---

## Architecture

```
com.flowerfarm
├── FlowerFarmApp
├── auth/           # DesktopSession, roles, directory
├── model/          # Item, Harvest, Customer, Order, SyncHistory
├── service/        # Inventory, Harvest, Order, Market Day, Briefing, Closeout, …
├── connector/      # DualModeCapable, LocalJsonMirror, registry, impl.*
├── controller/     # REST
├── gui/            # FlowerFarmGUI (TabHost) + tabs
└── lsystem/        # Rose generative rules
```

**Add a dual-mode connector:** implement `ExternalConnector` + `DualModeCapable`, use `LocalJsonMirror` for offline, inject credentials with `@Value`, and register it with `@Component`.
Full dependency, data-flow, transaction, profile, and deployment map:
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**. GUI design notes:
**[docs/GUI_ARCHITECTURE.md](docs/GUI_ARCHITECTURE.md)**.

---

## Status

**Current release: [v1.0.19](https://github.com/RicheyWorks/FlowersForever/releases/tag/v1.0.19)** — Spring Boot 3.5.16, offline-first dual-mode, full market-day ops loop with printable PDFs, hardened build pipeline (coverage, SBOM, CodeQL, Docker/GHCR, native installers — [ADR-001](docs/adr/ADR-001-infrastructure-and-build-expansion.md)).

| Theme | Shipped (highlights) |
|-------|----------------------|
| **Core farm ops** | Harvest path, CRM pipeline/fulfill, dashboard KPIs, weekly PDF |
| **Market day** | Pack list, packing PDF, batch fulfill (1.0.5–1.0.10) |
| **Day bookends** | Morning briefing, day closeout (1.0.9, 1.0.12) |
| **Printables** | Beds, harvest log, invoices, statements, low-stock, price list, audit (1.0.6–1.0.18) |
| **Platform** | Dual-mode connectors, VIEWER locks, `demo` profile, Dependabot, SECURITY.md |

### Later ideas

- Spring Boot **4.x** migration (deliberate; 3.5.16 is final 3.5 OSS)  
- Deeper POS adapters (only with real merchant APIs)

---

## Docs

| File | Role |
|------|------|
| [README.md](README.md) | Setup, features, API |
| [SECURITY.md](SECURITY.md) | Auth model, defaults, vulnerability reporting |
| [docs/DEMO.md](docs/DEMO.md) | Portfolio / demo runbook |
| [docs/CONNECTORS_SANDBOX.md](docs/CONNECTORS_SANDBOX.md) | Offline mirrors + live keys |
| [docs/BACKEND_ARCHITECTURE.md](docs/BACKEND_ARCHITECTURE.md) | Transactions, validation, API errors, and connector safety |
| [docs/GUI_ARCHITECTURE.md](docs/GUI_ARCHITECTURE.md) | GUI design history |
| [scripts/demo-rest.ps1](scripts/demo-rest.ps1) | REST smoke (Windows) |
| [scripts/demo-rest.sh](scripts/demo-rest.sh) | REST smoke (Unix) |

---

## License / credit

Built for PNW flower growers · Port Orchard, Kitsap County, WA.  
Roses, compost, and Cascades mist included free of charge.
