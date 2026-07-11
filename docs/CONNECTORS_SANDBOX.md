# Connector sandbox & live credentials

FlowersForever connectors support **offline local mirrors** (default for Farmbrite / Floranext) and optional **live REST** credentials.

Prefer secrets in `src/main/resources/application-local.properties` (gitignored).

---

## Offline demo (no accounts required)

Default `application.properties`:

```properties
connector.farmbrite.local-file=data/farmbrite-mirror.json
connector.floranext.local-file=data/floranext-mirror.json
```

| Step | Action |
|------|--------|
| 1 | Start app — connectors are available immediately |
| 2 | **Export Farmbrite** — writes `data/farmbrite-mirror.json` from local inventory |
| 3 | Harvest or edit stock |
| 4 | **Sync Farmbrite** — rewrites the mirror with current inventory |
| 5 | **Import Farmbrite** — reloads mirror into inventory |
| Same | Floranext uses `data/floranext-mirror.json` |
| Same | Shopify uses `data/shopify-mirror.json` |
| Same | Square uses `data/square-mirror.json` |
| Same | Google Sheets uses `data/google-sheets-mirror.json` |
| Same | Webhook dry-run writes `data/webhook-last-payload.json` |
| Same | Airtable uses `data/airtable-mirror.json` |

This is the recommended path for demos, CI, and portfolio videos.

**Discovery:** `GET /api/connectors` includes `mode` (`local` | `rest` | `unconfigured`) and `localMode` for dual-mode connectors.

---

## Farmbrite (live REST)

When `local-file` is **blank/removed** and credentials are set, the connector uses HTTP.

```properties
# Clear local mode for live API
connector.farmbrite.local-file=

connector.farmbrite.api-key=YOUR_API_KEY
connector.farmbrite.account-id=YOUR_ACCOUNT_ID
connector.farmbrite.base-url=https://www.farmbrite.com/api/v1
```

| Field | Notes |
|-------|--------|
| `api-key` | Bearer token / API key from Farmbrite account settings |
| `account-id` | Farm account identifier (path segment) |
| `base-url` | Override if using a proxy or alternate host |

**Expected endpoints (adapter shape):**

- `GET  {base}/accounts/{accountId}/inventory?limit=250` → list
- `POST {base}/accounts/{accountId}/inventory` → create/update payload `{ "item": {…} }`

Field mapping: `name`, `category`/`type`, `unit_price`, `quantity_on_hand`, `unit`, `notes`.

**Sandbox tip:** If Farmbrite does not offer a public sandbox, keep `local-file` for demos and point `base-url` at a local mock (e.g. WireMock) for integration tests.

Auth headers sent:

- `Authorization: Bearer {api-key}`
- `X-Account-Id: {account-id}`

---

## Floranext (live REST)

```properties
connector.floranext.local-file=

connector.floranext.api-key=YOUR_API_KEY
connector.floranext.store-url=https://your-shop.floranext.com
```

| Field | Notes |
|-------|--------|
| `api-key` | Store API key (`X-Api-Key` + Bearer) |
| `store-url` | Shop origin (https added if missing) |

**Expected endpoints:**

- `GET  {store}/api/products?limit=200`
- `POST {store}/api/products` with `{ "product": {…} }`

Field mapping: `name`/`title`, `category`/`product_type`, `price`, `stock`/`quantity`, `description`.

**Sandbox tip:** Use a staging shop URL if Floranext provides one; otherwise use the local JSON mirror for demos.

---

## Other live connectors (quick reference)

| Connector | Keys | Docs / notes |
|-----------|------|----------------|
| **Shopify** | `local-file` **or** `shop-name` + `api-token`, `api-version` | Dual mode; Admin REST needs `read_products` / `write_products` |
| **Square** | `local-file` **or** `access-token`, `location-id`, `environment` | Dual mode; Catalog + Inventory; `sandbox` or `production` |
| **Google Sheets** | `local-file` **or** `spreadsheet-id` + `api-key`/`access-token` | Dual mode; export/sync need OAuth access token |
| **Airtable** | `local-file` **or** `api-key` + `base-id` + `table-name` | Dual mode; personal access token for live |
| **Webhook** | `local-file` **or** `url`, optional `secret` | Dual mode dry-run; HMAC `X-FlowerFarm-Signature` |

---

## Safety

- Never commit real keys — use `application-local.properties`.
- Leave credentials blank to disable remote mode.
- Local mirror files under `data/` are gitignored with other runtime data.
