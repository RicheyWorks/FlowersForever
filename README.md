# FlowersForever — Flower Farm Manager

[![CI](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml/badge.svg)](https://github.com/RicheyWorks/FlowersForever/actions/workflows/ci.yml)

**Practical desktop + REST inventory tool for PNW flower farmers**  
Port Orchard / Kitsap County · roses, stems, tools, supplies · west of the Cascades.

Spring Boot **3.5.16** + modular Swing GUI + dual-mode connectors (offline JSON mirrors **or** live REST).

---

## Features

| Area | What you get |
|------|----------------|
| **Inventory** | H2/SQLite stock; harvest ↑ / fulfill ↓ / harvest-delete undo |
| **Harvest log** | Add / edit / batch / filter (crop·bed·notes·dates) / CSV export; inventory + audit |
| **CRM** | Customers + orders; confirm → pipeline; fulfill deducts stock; filters + CSV |
| **Dashboard** | 5 KPIs, sparklines, cost basis, **realized** week revenue + pipeline, ops alerts |
| **Reports** | Branded weekly harvest + sales PDF |
| **Sync history** | Full audit (connectors · harvest · CRM); filter / failures / CSV |
| **Connectors** | CSV, Excel, Airtable, Webhook, Shopify, Square, Sheets, Farmbrite, Floranext — **dual-mode** |
| **Rose visualizer** | L-Systems: grow, mutate, save/load rulesets, inventory link, PNG |
| **Auth** | Optional OWNER / HAND / VIEWER barn roles (`auth` profile) |
| **CLI** | Inventory + harvest log/export (`--cli`) |

---

## Requirements

- **JDK 17+**
- **Maven 3.9+**

**CI:** every push/PR to `main` runs `mvn clean verify` on Ubuntu + Temurin 17 (see `.github/workflows/ci.yml`).  
**Dependabot:** weekly PRs for Maven + GitHub Actions.  
**Releases:** push a tag `v*` → Actions builds the fat JAR and attaches it to a GitHub Release.  
**Security:** barn auth model, defaults, and hardening — **[SECURITY.md](SECURITY.md)**.

```bash
# Create a release from main (after CI is green)
git tag v1.0.3
git push origin v1.0.3
```

---

## Quick start

```bash
git clone https://github.com/RicheyWorks/FlowersForever.git
cd FlowersForever

mvn clean verify

# GUI + REST (:8080)
mvn spring-boot:run
# or
java -jar target/flowerfarm-manager-1.0.3.jar

# CLI
java -jar target/flowerfarm-manager-1.0.3.jar --cli

# SQLite
java -jar target/flowerfarm-manager-1.0.3.jar --spring.profiles.active=sqlite

# Shared barn login (GUI + HTTP Basic)
java -jar target/flowerfarm-manager-1.0.3.jar --spring.profiles.active=auth
# farm/kitsap (OWNER) · hand/harvest (HAND) · viewer/view (VIEWER)
```

### Persistence

| Mode | Flag | File |
|------|------|------|
| **H2 (default)** | — | `./data/flowerfarm.mv.db` |
| **SQLite** | `sqlite` profile | `./data/flowerfarm.sqlite` |

Empty DB seeds from `farm_inventory.csv` if present, else PNW sample SKUs.

### Demo (5–12 minutes)

Full portfolio runbook: **[docs/DEMO.md](docs/DEMO.md)**  
REST smoke (app running):

```powershell
powershell -File scripts/demo-rest.ps1
# with barn auth:
powershell -File scripts/demo-rest.ps1 -User farm -Pass kitsap
```

**Happy path:**

```text
1. Dashboard — sparklines, week harvest / realized revenue
2. Harvest Log — Nootka Rose 120 stems (or batch lines) → stock ↑
3. CRM — customer + CONFIRMED order → Fulfill → stock ↓
4. Sync History — HARVEST_LOG / ORDER_FULFILL rows; export audit CSV
5. Connectors — Export Farmbrite/Shopify (local mirrors, no keys)
6. Reports — weekly PDF
7. Optional auth profile — switch farm → viewer (read-only connectors)
```

---

## Connectors (dual-mode)

**Default:** local JSON mirrors under `data/` (offline demos / CI).  
**Live:** clear `local-file` and set credentials in `application-local.properties` (gitignored).

| Name | Directions | Offline | Live keys |
|------|------------|---------|-----------|
| **csv** | I/E/S | file paths | — |
| **excel** | I/E | `.xlsx` file | — |
| **farmbrite** | I/E/S | `data/farmbrite-mirror.json` | api-key + account-id |
| **floranext** | I/E/S | `data/floranext-mirror.json` | api-key + store-url |
| **shopify** | I/E/S | `data/shopify-mirror.json` | shop-name + api-token |
| **square** | I/E/S | `data/square-mirror.json` | access-token (+ location) |
| **google-sheets** | I/E/S | `data/google-sheets-mirror.json` | spreadsheet-id + key/token |
| **airtable** | I/E/S | `data/airtable-mirror.json` | api-key + base-id |
| **webhook** | E | `data/webhook-last-payload.json` | url (+ secret HMAC) |

Discovery includes `mode` / `localMode`: `GET /api/connectors`  
Sandbox details: [`docs/CONNECTORS_SANDBOX.md`](docs/CONNECTORS_SANDBOX.md).

---

## Auth (optional)

| Role | Write ops | Clear audit | Typical use |
|------|-----------|-------------|-------------|
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

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/dashboard` | Combined inventory + harvest week + revenue KPIs |
| GET | `/api/inventory` | List / search / kpis |
| GET/POST | `/api/harvest` | Harvest CRUD |
| POST | `/api/harvest/batch` | Batch log |
| GET | `/api/harvest/week` | 7-day total + daily series |
| GET | `/api/harvest/filter` | crop/bed/notes/dates |
| GET/POST | `/api/customers` | CRM customers |
| GET/POST | `/api/orders` | Orders |
| GET | `/api/orders/week` | Realized + pipeline + sparklines |
| GET | `/api/orders/filter` | status/customer/dates |
| POST | `/api/orders/{id}/fulfill` | Deduct inventory |
| POST | `/api/orders/{id}/confirm` | → CONFIRMED pipeline |
| GET | `/api/connectors` | Registry + dual-mode flags |
| GET | `/api/connectors/history` | Audit filter (`connector`, `operation`, `success`, `q`) |
| POST | `/api/connectors/history/export` | Server-side audit CSV |
| GET | `/api/auth/me` | Session / role |
| GET | `/api/reports/weekly.pdf` | Trailing week PDF |
| GET | `/actuator/health` | Health |

---

## Architecture (short)

```
com.flowerfarm
├── FlowerFarmApp
├── auth/                  # FarmSession, roles, directory
├── model/                 # Item, Harvest, Customer, Order, SyncHistory
├── service/               # Inventory, Harvest, Order, Report, Trend, SyncHistory
├── connector/             # DualModeCapable, LocalJsonMirror, registry, impl.*
├── controller/            # REST
├── gui/                   # FlowerFarmGUI (TabHost) + tabs
└── lsystem/               # Rose generative rules
```

**Add a dual-mode connector:** implement `ExternalConnector` + `DualModeCapable`, use `LocalJsonMirror` for offline, `@Value` for credentials, register `@Component` or `@Bean`.

More: [`docs/GUI_ARCHITECTURE.md`](docs/GUI_ARCHITECTURE.md).

---

## Project status

### Done (recent)

- Harvest production path (edit, batch, filter, export, CLI, API)  
- Dashboard: sparklines, realized vs pipeline revenue, WoW, alerts  
- Dual-mode: Farmbrite, Floranext, Shopify, Square, Sheets, Airtable, Webhook  
- CRM: search, filter, confirm/fulfill/cancel, notes, CSV  
- Audit history: multi-filter + CSV export  
- Auth UX: login, session badge, switch user, VIEWER write guards  
- Maintenance: Spring Boot **3.5.16**, release action softprops v3, Dependabot, `SECURITY.md`  
- VIEWER UX: form fields + write buttons disabled across inventory, harvest, CRM, connectors  

### Later ideas

- Spring Boot **4.x** migration (deliberate; 3.5.16 is final 3.5 OSS)  
- Weather-aware irrigation tips (Kitsap)  
- Deeper POS adapters (only with real APIs)

---

## Docs

| File | Role |
|------|------|
| [README.md](README.md) | Setup, features, API |
| [SECURITY.md](SECURITY.md) | Auth model, defaults, vulnerability reporting |
| [docs/DEMO.md](docs/DEMO.md) | **Portfolio / demo runbook** |
| [docs/CONNECTORS_SANDBOX.md](docs/CONNECTORS_SANDBOX.md) | Offline mirrors + live keys |
| [docs/GUI_ARCHITECTURE.md](docs/GUI_ARCHITECTURE.md) | GUI design history |
| [scripts/demo-rest.ps1](scripts/demo-rest.ps1) | REST smoke (Windows) |
| [scripts/demo-rest.sh](scripts/demo-rest.sh) | REST smoke (Unix) |

---

## License / credit

Built for PNW flower growers · Port Orchard, Kitsap County, WA.  
Roses, compost, and Cascades mist included free of charge.
