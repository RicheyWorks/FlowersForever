# FlowersForever — Flower Farm Manager

[![CI](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml/badge.svg)](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/RicheyWorks/FlowersForever?include_prereleases&sort=semver)](https://github.com/RicheyWorks/FlowersForever/releases)

**Practical desktop + REST inventory for PNW flower growers**  
Port Orchard / Kitsap County · roses, stems, tools, supplies · west of the Cascades.

Spring Boot **3.5.16** · modular Swing GUI · dual-mode connectors (offline JSON mirrors **or** live REST) · fat JAR **1.0.18**.

---

## Features

| Area | Highlights |
|------|------------|
| **Inventory** | H2/SQLite stock; harvest ↑ / fulfill ↓; low-stock reorder + **price list** PDFs |
| **Harvest** | Log / edit / batch / filter; bed production + chronological **harvest log** PDFs |
| **CRM** | Customers & orders; confirm → pipeline; fulfill deducts stock; **invoice** + **statement** PDFs |
| **Dashboard** | KPIs, sparklines, realized vs pipeline revenue, PACK / WATER alerts, quick actions |
| **Market day** | Pack list vs stock, packing PDF, batch fulfill all CONFIRMED |
| **Day ops** | **Morning briefing** + **day closeout** sheets (pack, beds, water, stock, sales) |
| **Irrigation** | Kitsap Open-Meteo live or offline climatology |
| **Audit** | Connector · harvest · CRM history; filter, CSV, **audit PDF** (FAIL highlighted) |
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
| **Quality** | JaCoCo coverage report every build; SpotBugs + Checkstyle via `-Pquality` (report-only) |
| **Dependabot** | Weekly Maven + GitHub Actions PRs |
| **Supply chain** | CodeQL scanning, PR dependency review, CycloneDX SBOM on every build |
| **Releases** | Tag `v*` → fat JAR + SBOM on GitHub Releases, image to GHCR, MSI/DMG installers via jpackage |
| **Security** | Auth model & hardening — **[SECURITY.md](SECURITY.md)** |

Decisions and trade-offs: **[docs/adr/ADR-001](docs/adr/ADR-001-infrastructure-and-build-expansion.md)**.

### Docker (headless REST-only)

```bash
docker build -t flowersforever .
docker run -p 8080:8080 -v flowerfarm-data:/app/data flowersforever
# or from GHCR after a release:
docker run -p 8080:8080 ghcr.io/richeyworks/flowersforever:latest
```

```bash
git tag v1.0.18
git push origin v1.0.18
```

---

## Quick start

```bash
git clone https://github.com/RicheyWorks/FlowersForever.git
cd FlowersForever
mvn clean verify

# GUI + REST on :8080
mvn spring-boot:run
# or
java -jar target/flowerfarm-manager-1.0.18.jar

# CLI
java -jar target/flowerfarm-manager-1.0.18.jar --cli

# Profiles (combine with commas)
java -jar target/flowerfarm-manager-1.0.18.jar --spring.profiles.active=sqlite
java -jar target/flowerfarm-manager-1.0.18.jar --spring.profiles.active=auth
java -jar target/flowerfarm-manager-1.0.18.jar --spring.profiles.active=demo
java -jar target/flowerfarm-manager-1.0.18.jar --spring.profiles.active=demo,auth
```

| Profile | Purpose |
|---------|---------|
| *(default)* | H2 file DB + GUI + REST |
| `sqlite` | SQLite under `./data/flowerfarm.sqlite` |
| `auth` | HTTP Basic + GUI login — `farm/kitsap` · `hand/harvest` · `viewer/view` |
| `demo` | Non-destructive seed of CRM / harvest / today’s CONFIRMED orders when empty |
| `cli` | Interactive CLI only (no Tomcat) |

Empty DB seeds from `farm_inventory.csv` if present, else sample PNW SKUs. Default store: `./data/flowerfarm.mv.db`.

### Portfolio demo

Full runbook: **[docs/DEMO.md](docs/DEMO.md)**

```powershell
# App must already be running on :8080
powershell -File scripts/demo-rest.ps1
powershell -File scripts/demo-rest.ps1 -User farm -Pass kitsap   # with auth
```

**Happy path (~5 min)**

1. **Dashboard** — KPIs, sparklines, morning briefing / day closeout  
2. **Harvest Log** — log Nootka Rose stems → stock ↑; bed production + harvest log PDFs  
3. **CRM** — CONFIRMED order → invoice PDF; fulfill → stock ↓; customer statement PDF  
4. **Market Day** — packing list / PDF → fulfill all CONFIRMED  
5. **Inventory** — low-stock reorder + price list PDFs  
6. **Sync History** — HARVEST_LOG / ORDER_FULFILL; audit PDF  
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

```properties
flowerfarm.auth.users=farm:kitsap:OWNER,hand:harvest:HAND,viewer:view:VIEWER
```

GUI: **Account → Who am I? / Switch user**.  
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
├── auth/           # FarmSession, roles, directory
├── model/          # Item, Harvest, Customer, Order, SyncHistory
├── service/        # Inventory, Harvest, Order, Market Day, Briefing, Closeout, …
├── connector/      # DualModeCapable, LocalJsonMirror, registry, impl.*
├── controller/     # REST
├── gui/            # FlowerFarmGUI (TabHost) + tabs
└── lsystem/        # Rose generative rules
```

**Add a dual-mode connector:** implement `ExternalConnector` + `DualModeCapable`, use `LocalJsonMirror` for offline, `@Value` for credentials, register `@Component` or `@Bean`.  
GUI design notes: **[docs/GUI_ARCHITECTURE.md](docs/GUI_ARCHITECTURE.md)**.

---

## Status

**Current release: [v1.0.18](https://github.com/RicheyWorks/FlowersForever/releases/tag/v1.0.18)** — Spring Boot 3.5.16, offline-first dual-mode, full market-day ops loop with printable PDFs.

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
| [docs/GUI_ARCHITECTURE.md](docs/GUI_ARCHITECTURE.md) | GUI design history |
| [scripts/demo-rest.ps1](scripts/demo-rest.ps1) | REST smoke (Windows) |
| [scripts/demo-rest.sh](scripts/demo-rest.sh) | REST smoke (Unix) |

---

## License / credit

Built for PNW flower growers · Port Orchard, Kitsap County, WA.  
Roses, compost, and Cascades mist included free of charge.
