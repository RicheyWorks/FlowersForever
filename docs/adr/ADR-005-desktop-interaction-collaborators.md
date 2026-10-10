# ADR-005: Decompose desktop interaction shells

- **Status:** Accepted
- **Date:** 2026-08-23

## Context

`CrmTab`, `HarvestLogTab`, `FlowerFarmGUI`, and `FlowerFarmCLI` mixed rendering,
input validation, service orchestration, navigation, connector-result mapping,
and command dispatch. Changing a workflow required editing a large interaction
shell and testing it through Swing or the full CLI.

## Decision

Keep the existing Spring entry points, tab constructors, `FlowerFarmTab`, and
`TabHost` APIs, while extracting focused collaborators:

- `CrmPresenter` and `HarvestLogPresenter` own input parsing, date-range
  validation, table projection, summaries, and application-service delegation.
- `CrmCustomerSearchView` and `HarvestBatchView` own reusable Swing component
  composition while their parent tabs retain dialogs and page-level layout.
- `FarmTabCatalog` owns the stable feature list and navigation order.
- `FarmTabNavigator` owns tab population, refresh, named selection, mnemonics,
  F5, and Alt-number shortcuts.
- `ConnectorActionPresenter` executes connector workflows and converts result
  envelopes into view-ready feedback.
- `CliCommandRouter` owns the ordered 22-command menu and dispatch semantics.
- `CliReportCommands` owns interactive PDF/report handlers used by the CLI.

Business rules and transaction boundaries remain in application services.
Presenters do not query repositories, and view components do not invoke services.

## Consequences

The frame is now a composition shell instead of a connector/navigation
implementation. CRM and harvest views can test validation and row projection
without constructing Swing dialogs. CLI command identity and exit behavior are
explicit data rather than a large switch, and report prompts are independently
testable handlers.

Some page layout and confirmation dialogs intentionally remain in their tabs.
Those dialogs are interaction policy, and extracting them would add abstraction
without improving the current single-frame application.

## Verification

- `InteractionPresenterTest` covers CRM, harvest, and connector presenters.
- `InteractionRoutingTest` covers tab navigation, shortcuts, CLI routing, and
  extracted view components in headless mode.
- `CliReportCommandsTest` covers report-handler defaults and validation.
- `PriorityTabsUiTest` preserves the existing CRM and harvest UI contracts.
- `ArchitectureBoundaryTest` prevents direct service workflow calls and command
  dispatch from returning to the interaction shells.

