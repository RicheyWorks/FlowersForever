# ADR-004: Separate report rendering from workflow services

- **Status:** Accepted
- **Date:** 2026-08-23

## Context

`HarvestService`, `InventoryService`, and `OrderService` combined farm workflows,
transactional queries, PDF layout, CSV escaping, and filesystem output. Their
public methods were used by Swing, REST, and CLI, so moving those APIs would have
created avoidable compatibility risk.

## Decision

Keep the existing public service methods as transactional compatibility façades,
but delegate output work to stateless Spring collaborators:

- `HarvestReportGenerator` renders bed-production and harvest-log PDF/CSV data.
- `HarvestCsvExporter` writes harvest-history snapshots.
- `InventoryReportGenerator` renders low-stock and price-list PDFs.
- `InventoryCsvExporter` writes inventory snapshots.
- `OrderReportGenerator` renders invoices and writes accounting CSV.

Workflow services still build report models from repository-backed data. They
own validation, mutation rules, and transaction boundaries. Renderers receive
complete models and never query repositories or mutate farm state.

## Consequences

The three workflow services shed about 780 lines while retaining their public
APIs and read-only transaction annotations. Formatting can now be unit tested
without persistence or controllers. The collaborators remain in the service
package because they are application output ports, not HTTP adapters.

This introduces constructor dependencies and leaves report DTO records nested in
the compatibility services. Moving those DTOs is unnecessary until another
consumer needs an independent report-model package.

## Verification

- `ReportCollaboratorTest` exercises each renderer/exporter directly.
- `ReportDownloadCompatibilityIT` uses real renderers through REST and H2.
- `ArchitectureBoundaryTest` prevents PDF/file-writing code returning to the
  three workflow services.
- existing service, controller, GUI, profile, persistence, and concurrency tests
  preserve the prior contracts.

