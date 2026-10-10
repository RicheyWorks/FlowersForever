# ADR-003: Enforce layered application boundaries

- **Status:** Accepted
- **Date:** 2026-08-23

## Context

FlowersForever exposes the same farm workflows through Swing, REST, and CLI and
supports both JPA persistence and multiple vendor connectors. Direct Spring Data
dependencies in services and connector result types in `SyncHistoryService`
created an adapter cycle and made use cases harder to test independently.
Controllers also repeated attachment construction and could drift in filename
and header policy.

## Decision

- Application services own business rules and transaction boundaries.
- Services depend on repository ports expressed in farm-domain terms.
- JPA adapters own Spring Data repositories, locks, pagination, and query
  translation.
- Connector result interpretation remains in the connector layer; audit services
  receive neutral values.
- REST controllers delegate attachment construction to one controller adapter.
- A build-time architecture test rejects forbidden package imports and any
  Spring Data import from a service.

The dependency direction is:

```text
GUI / REST / CLI -> services -> repository ports <- JPA adapters
connectors ------> services
```

## Consequences

Services can be tested with small port doubles and no Spring Data types. JPA
details can change without altering use-case signatures. Package cycles and
accidental presentation-to-persistence shortcuts fail during tests. Download
policy is consistent across controllers.

This adds thin adapter classes and delegation tests. Report rendering and CSV
serialization have since moved into dedicated collaborators under ADR-004;
the service entry points remain compatibility façades for existing callers.

## Verification

- `ArchitectureBoundaryTest` checks dependency direction.
- `RepositoryAdapterTest` checks port-to-JPA translation and locking delegation.
- service tests use repository ports.
- `ApplicationContextSmokeTest` checks complete Spring wiring.
- `BackendTransactionIT` checks real H2 atomicity and concurrency behavior.
- `ProfileConfigurationTest` and `ContainerProfileTest` check runtime profiles.
