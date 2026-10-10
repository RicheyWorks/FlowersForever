# FlowersForever Backend Architecture

The backend is a Spring Boot service used by both the Swing application and the
REST API. Controllers translate HTTP requests, services own business rules and
transaction boundaries, and repositories isolate JPA persistence.

Services depend on domain-oriented repository ports rather than Spring Data.
`JpaCustomerRepository`, `JpaCustomerOrderRepository`, `JpaHarvestRepository`,
`JpaInventoryRepository`, `JpaKnowledgeRepository`, and `JpaSyncHistoryRepository` adapt those ports to
the low-level Spring Data interfaces. Pagination, query translation, and locking
remain persistence concerns. The executable rules in `ArchitectureBoundaryTest`
prevent adapter imports from leaking back into services.

The largest workflow services no longer own document layout or filesystem
serialization. They build report models and delegate to `HarvestReportGenerator`,
`InventoryReportGenerator`, `OrderReportGenerator`, `HarvestCsvExporter`, and
`InventoryCsvExporter`. Existing service APIs and read-only transaction
boundaries remain the stable entry points for Swing, REST, and CLI.

## Reliability boundaries

Farm harness batch commands run through `FarmDataCommandTransactions`, which
keeps every row and durable import receipt in one transaction. The dispatcher
acquires its command monitor before opening that transaction. Export creation
instead owns a dedicated repeatable-read transaction, avoiding a suspended
outer transaction that would consume another pool connection. Catalog discovery,
mapping suggestions, and completed export-file reads do not open transactions.

- Inventory, harvest edits, customer edits, and order state transitions lock the
  affected database rows before mutation.
- Order fulfillment runs in its own transaction. It is idempotent after an order
  reaches `FULFILLED`, requires a `CONFIRMED` order, and fails atomically when a
  SKU is missing or stock is insufficient.
- Market-day batch fulfillment deliberately runs each order in an independent
  transaction. One rejected order is reported without rolling back orders that
  already committed.
- Harvest edit/delete operations lock the harvest row before correcting stock,
  keeping the persisted harvest quantity and inventory contribution aligned.
- Connector imports upsert inventory by case-insensitive exact SKU name. Repeating
  the same import updates the matching SKU instead of creating another row, and
  partial-import responses count only records that actually persisted.

## Validation and API errors

Entity constructors and setters enforce the same limits as persistence columns.
Monetary values must be finite and non-negative; harvest and order quantities
must also be greater than zero and round to at least one inventory unit. Large
harvest batches, order line collections, market-day windows, ten-year database
query windows, and local connector mirrors have explicit safety limits. Harvest
batches are validated in full before the first row is written.

REST failures keep the legacy `error` property and add a stable envelope:

```json
{
  "error": "The request contains an invalid value.",
  "code": "INVALID_REQUEST",
  "status": 400,
  "path": "/api/example",
  "timestamp": "2026-08-23T21:00:00Z"
}
```

Unexpected exception messages and stack traces are logged server-side and are
not returned to clients. Persistence races map to `409 DATA_CONFLICT` with a
refresh-and-retry instruction. Malformed JSON maps to `400 MALFORMED_BODY`.
Unsupported request content types map to `415 UNSUPPORTED_MEDIA_TYPE`, and
missing required multipart parts map to `400 INVALID_REQUEST`. An unacceptable
response media type returns an empty `406` response, avoiding a second failed
attempt to negotiate a JSON error body or a false internal-error incident.

Enabled harness command endpoints use a scoped JSON mapper that rejects
duplicate fields, trailing content, unknown envelope fields, coerced command
names/request IDs, and non-string expiry timestamps before dispatch. Ordinary
API DTOs retain the application mapper's existing behavior. Both JSON media
types and vendor `+json` types retain the configured attachment string allowance.

Every PDF and CSV endpoint uses `DownloadResponse`, which applies one validated
attachment-filename policy and consistent media type, content length, and
content-disposition headers.

## Connectors and filesystem safety

- Live connector HTTP clients use a 10-second connection timeout and a 30-second
  response timeout.
- Failure details redact tokens, API keys, passwords, authorization headers, and
  URL credentials before entering API responses or sync history.
- Local JSON mirrors use same-directory temporary files and atomic replacement,
  preventing a failed write from truncating the last good mirror.
- REST CSV exports resolve through `ExportPathResolver` and accept only a simple
  `.csv` leaf name beneath `flowerfarm.exports.directory`.
- Offline local mirrors remain the default; live connectors activate only when
  their required configuration is present.

## Security and profiles

Open desktop mode binds to loopback. The `auth` profile enables HTTP Basic roles:
OWNER has full access, HAND can mutate farm data, and VIEWER is read-only. Secured
HTTP requests are stateless. Both `auth` and `container` refuse startup without
strong credentials and at least one OWNER; the container profile also binds
externally by default.

The default H2 file profile and optional SQLite profile share the same service and
repository contracts. SQLite retains a one-connection pool because it is a
single-writer database. The container profile keeps persistent data under the
mounted `data/` directory.

## Compatibility corrections

Successful response payloads and routes are unchanged. The following unsafe edge
cases intentionally fail earlier:

- fulfilling DRAFT, empty, missing-SKU, or under-stocked orders;
- non-finite/oversized numeric values or text longer than its database column;
- zero/sub-unit quantities, connector mirrors above 10 MiB, market windows above
  366 days, and database query windows above ten years;
- harvest batches or initial order payloads above 500 rows;
- REST export paths containing directories or non-CSV extensions.

These corrections prevent partial inventory deductions, silent overselling,
database truncation errors, path traversal, and unbounded resource use.

## Verification

Unit and integration coverage includes API error contracts, role enforcement,
H2 transaction isolation, concurrent harvest edits, idempotent imports, strict
stock deductions, connector timeout/redaction behavior, atomic mirrors, export
path confinement, repository adapters, package boundaries, report collaborator
contracts, real report downloads, profile composition, and container startup
policy. The release gate is:

```bash
mvn clean verify -Pquality
```
