# FlowersForever — Demo runbook (portfolio / farm show-and-tell)

**Audience:** interviewers, growers, and you on a market-morning coffee.  
**Time:** ~8–12 minutes full pass, or ~5 minutes “happy path only.”

---

## 0. Build once

```bash
cd FlowersForever
mvn clean verify
```

All tests green → fat JAR under `target/flowerfarm-manager-1.0.3.jar`.  
GitHub Actions runs the same command on every push to `main` (see CI badge in the README).  
Tagged releases (`git tag v1.0.2 && git push origin v1.0.2`) publish the fat JAR on the GitHub Releases page.

---

## 1. Start (pick a profile)

| Goal | Command |
|------|---------|
| Default GUI + REST + H2 | `mvn spring-boot:run` |
| Same via JAR | `java -jar target/flowerfarm-manager-1.0.3.jar` |
| SQLite file DB | `java -jar … --spring.profiles.active=sqlite` |
| Barn multi-user | `java -jar … --spring.profiles.active=auth` |
| Auth + SQLite | `java -jar … --spring.profiles.active=auth,sqlite` |
| CLI only | `java -jar … --cli` |

App opens Swing GUI and Tomcat on **http://localhost:8080**.

---

## 2. Happy path (~5 min) — “stem cut → sale”

Narrate as Kitsap / Port Orchard grower:

1. **Dashboard**  
   - Point at 5 KPIs: SKUs, inventory value (+ cost basis), low stock, **Week Harvest**, **Week Revenue**.  
   - Hover sparklines → day-by-day tooltip.  
   - Note **realized revenue** = FULFILLED only; pipeline under the card.

2. **Harvest Log**  
   - Date today, crop `Nootka Rose`, qty `120`, unit `stems`, bed `Bed A`.  
   - **Add harvest** → inventory increases; status mentions `HARVEST_LOG`.  
   - Or **Batch log**:  
     ```
     Nootka Rose,40
     Damask Rose,25,bunches
     Dahlia mix,15,stems,Bed C
     ```  
   - Double-click a row → tweak qty → **Save edit** (`HARVEST_EDIT`).  
   - Filter crop/bed/notes; **This week**; **Export filtered CSV**.

3. **Dashboard again**  
   - Week Harvest KPI / sparkline updated.

4. **CRM**  
   - Add customer `Kitsap Blooms` (type WHOLESALE).  
   - Create order: product `Nootka Rose`, qty `10`, price `12`, status CONFIRMED.  
   - **Confirm** if DRAFT; **Fulfill** → inventory drops; audit under CRM.  
   - Filter **Pipeline only**; export orders CSV.

5. **Sync History**  
   - Show HARVEST_LOG / ORDER_FULFILL / connector rows.  
   - Failures only filter; export audit CSV.

6. **Reports**  
   - Weekly PDF (GUI or `curl -o weekly.pdf http://localhost:8080/api/reports/weekly.pdf`).

7. **Connectors (offline dual-mode)**  
   - **Export Farmbrite** → `data/farmbrite-mirror.json`.  
   - **Sync Shopify** / **Export Square** with default local mirrors (no API keys).  
   - Same for Sheets / Airtable / Webhook dry-run payload file.

8. **Irrigation & Care**  
   - **Refresh (try live weather)** → Open-Meteo for Port Orchard (or climatology offline).  
   - Priority badge + 7-day precip / ET when live; active beds from recent harvests.  
   - Dashboard may show a **WATER** alert in dry months.

9. **Rose Visualizer**  
   - Grow L-System, mutate, save ruleset, optional PNG — “fun technical depth.”

---

## 3. Auth demo (optional, ~2 min)

```bash
java -jar target/flowerfarm-manager-1.0.3.jar --spring.profiles.active=auth
```

| User | Password | Show |
|------|----------|------|
| `farm` | `kitsap` | OWNER — full; clear audit allowed |
| `hand` | `harvest` | HAND — write ops; cannot clear history |
| `viewer` | `view` | VIEWER — connectors + harvest/CRM/inventory writes blocked |

**Account → Who am I?** / **Switch user…**  
REST: `curl -u farm:kitsap http://localhost:8080/api/auth/me`

---

## 4. REST smoke (PowerShell)

```powershell
# Health
Invoke-RestMethod http://localhost:8080/actuator/health

# Dashboard snapshot
Invoke-RestMethod http://localhost:8080/api/dashboard | ConvertTo-Json -Depth 5

# Harvest week series
Invoke-RestMethod http://localhost:8080/api/harvest/week

# Order week revenue (realized + pipeline)
Invoke-RestMethod http://localhost:8080/api/orders/week

# Connectors + dual-mode flags
Invoke-RestMethod http://localhost:8080/api/connectors | ConvertTo-Json -Depth 4

# Audit (failures)
Invoke-RestMethod "http://localhost:8080/api/connectors/history?success=false&limit=20"

# Kitsap irrigation advice (climatology = no network)
Invoke-RestMethod "http://localhost:8080/api/irrigation/advice?live=false" | ConvertTo-Json -Depth 4

# Weekly PDF
Invoke-WebRequest http://localhost:8080/api/reports/weekly.pdf -OutFile weekly.pdf
```

With auth:

```powershell
$pair = "farm:kitsap"
$b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
Invoke-RestMethod http://localhost:8080/api/auth/me -Headers @{ Authorization = "Basic $b64" }
```

Or use `scripts/demo-rest.ps1` from the repo root (app must already be running).

---

## 5. Talking points (why this exists)

- **Real farm loop:** harvest ↑ stock, fulfill ↓ stock, undo reverses, audits everywhere.  
- **Dual-mode connectors:** demo offline with JSON mirrors; flip to REST with credentials.  
- **Ops KPIs:** week harvest + **realized** revenue (not inflated by drafts).  
- **Barn roles:** OWNER / HAND / VIEWER without a separate identity product.  
- **Stack:** Spring Boot 3, JPA, Swing + FlatLaf, JFreeChart, OpenPDF, Weka — one runnable JAR.

---

## 6. Data files after a full demo

| Path | Created by |
|------|------------|
| `data/flowerfarm.mv.db` | H2 default |
| `data/*-mirror.json` | Dual-mode connector exports |
| `data/webhook-last-payload.json` | Webhook dry-run |
| `weekly.pdf` | Report download |

Wipe `data/` for a clean first-run seed next time.

---

## See also

- [README.md](../README.md) — setup & API  
- [CONNECTORS_SANDBOX.md](CONNECTORS_SANDBOX.md) — local mirrors + live keys  
- [GUI_ARCHITECTURE.md](GUI_ARCHITECTURE.md) — tab design history  
