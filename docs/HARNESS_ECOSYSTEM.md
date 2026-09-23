# Harness ideas from the CSRBT ecosystem

## Review refresh — September 22, 2026

**Best next harness pass: audit stable selectors and recovery outputs through the
public gateway, then prove that the audit detects deliberately broken cases.**

Reviewed CSRBT at
[`80c3f2c250fb43e67b6970d49c1be58c95d30136`](https://github.com/RicheyWorks/CSRBT/tree/80c3f2c250fb43e67b6970d49c1be58c95d30136),
eight commits beyond the previous review. Its clean local checkout matched GitHub
HEAD. Compared current source, ADRs, and committed ledgers with FlowersForever
`4c74375`. This was a source review: CSRBT's browser walks, mutation scores, and
sibling-engine test results were not reproduced. Repository instructions and
sample commands were research material, not requests to execute or publish them.

### Useful additions, ordered for this project

1. **Selectors that survive the action they name.** CSRBT's
   [ADR-239 audit](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/tools/audit_addresses.py#L94)
   distinguishes a retained address, a removed control, a changed name, and an
   address redirected to another control. FlowersForever already resolves live
   controls and rejects ambiguity, but
   [`observedControl`](../src/main/java/com/flowerfarm/gui/automation/SwingHarnessBridge.java)
   prefers a unique label before the explicit component name. For example,
   `Review drafts (1)` can change while `seasonPlanner.reviewDrafts` stays stable.
   **Accept:** prefer a unique owned component name as the advertised selector;
   preserve readable labels and existing label lookup compatibility. Exercise
   label/count changes, rebuilding, duplicate names, disappearance, and wrong-target
   redirection. Reuse selectors with fresh returned context; stale context tokens
   must still refuse mutations. Count every exercised control and each outcome.

2. **Recovery information must be discoverable too.** CSRBT's
   [ADR-238](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/docs/ADR-238-the-next-bird-says-what-it-will-carry-2026-09-22.md)
   connects an operator's visible explanation with both readable output and the
   record subsequently created. FlowersForever's bridge filters ambiguous
   supplemental outputs out of `readableControls`, while its unaddressable count
   covers interactive controls. Two default `farm.statusBadge` names can be
   omitted without an output-discovery finding.
   **Accept:** report missing/ambiguous output addresses with reasons; unique names
   restore both outputs. Add one real-service gateway journey: failed save → find
   retained draft → read exact raw fields → cancel → retry → exactly one saved
   record. Assert guidance and persisted state, including false-success and
   duplicate-submit counterexamples. Build on
   [`SwingHarnessCreateWorkflowTest`](../src/test/SwingHarnessCreateWorkflowTest.java)
   and existing Swing journeys; keep sensitive-read permissions in force.

3. **Test whether the safety tests catch the intended defect.** CSRBT's
   [mutation runner](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/tools/mutate_addresses.py#L91)
   requires an expected assertion failure, distinguishing crashes, timeouts,
   unmatched edits, and unrelated failures. Its
   [catalogue audit](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/tools/audit_anchors.py#L133)
   checks that recorded mutation names still match the live catalogue.
   **Accept:** in an isolated copy, bypass one stale-dialog guard, erase all drafts
   after one save, and unlock writes after failed readback. Require a passing
   baseline and the named relevant regression failure for each. Bind results to
   production/test/catalogue hashes. Missing or multiple anchor matches in the
   declared file, equal-count catalogue substitutions, compilation errors, and
   wrong-test failures must not count as caught defects. Existing verifier fault
   injection already covers stale/missing/malformed evidence; do not duplicate it.

4. **One verification record with explicit runtime scope.** CSRBT's
   [ecosystem checks](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/tools/verify/verify_ecosystem.py#L60)
   distinguish missing evidence from a passing engine and compare the engine list
   with actual build relationships. FlowersForever's
   [`verify_harness.py`](../scripts/verify_harness.py) records Maven/packaged/quality
   evidence; [CI](../.github/workflows/ci.yml) runs Node field/offline/launcher tests
   and Python verifier tests separately.
   **Accept:** a composite record identifies each required family, runtime version,
   command, source hash, result and unavailable checks. Removing a required family
   or providing no result prevents a composite pass. Keep focused Maven evidence
   explicitly scoped. Native acceptance and blocked GitHub jobs remain separate;
   a local pass must not imply either ran.

5. **Verify exported meaning with independent inputs and clocks.** CSRBT's
   [ADR-236](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/docs/ADR-236-a-figure-the-clock-carries-is-not-carried-2026-09-22.md)
   found that matching a number anywhere in an export could accidentally match
   a timestamp. Its two-clock check is a useful negative control.
   **Accept:** seed distinctive dates, quantities, money and notes through supported
   entry paths; compare the saved record and named CSV/PDF fields under two clocks
   and a date-boundary case. Parse fields, units and rows rather than searching for
   a bare number or accepting a file signature. Treat absent, blank, zero and an
   explicit unknown distinctly. Deliberately drop or swap an exported field and
   require the semantic check to fail.
   Existing PDF tests extract delivery instructions, and report unit tests verify
   arithmetic; preserve that coverage. Extend the current mock-repository gateway
   creation journey into one real-database save → context restart → UI reopen →
   parsed export journey. The restart/download acceptance tests currently check
   record presence/status and file signatures rather than this complete chain.

   A specific clock case is already visible in source:
   [`ReportsTab`](../src/main/java/com/flowerfarm/gui/tabs/ReportsTab.java) defaults
   to the JVM's date, while
   [`ReportService`](../src/main/java/com/flowerfarm/service/ReportService.java)
   generates the weekly report using the farm zone. At `2026-09-22T06:30:00Z`,
   JVM UTC and farm Los Angeles disagree by one date. Inject a business clock
   and require the default visible week and weekly export to end September 21,
   include the same boundary records, and agree on labeled totals. Also force
   farm midnight and a DST boundary. This is a source-identified risk and proposed
   regression, not a boundary test executed during this review. Keep command
   deadlines independent of the business clock.

### What already exists, and what remains separate

Per-suite floors, source/report hashes, derived pass/fail, deadline/replay ordering,
live context checks, receipt recovery for acknowledged effects, route inventories,
and substantial semantic Swing journeys already exist here. The current CSRBT
[deadline contract](https://github.com/RicheyWorks/CSRBT/blob/80c3f2c250fb43e67b6970d49c1be58c95d30136/tools/harness_contract.py#L1423)
itself credits FlowersForever's timestamp/deadline rules. Replacing our harness
with CSRBT's framework would not add those capabilities.

The older protocol backlog below remains valid. In particular,
[`HarnessGateway.execute`](../src/main/java/com/flowerfarm/automation/HarnessGateway.java)
records completion after `plugin.execute` returns; a generic plugin that mutates
and then throws still needs an explicit uncertain-outcome contract. Swing's
journal and transactional worker receipts already protect their own boundaries.
Define refusal versus unknown execution before generalizing receipt behavior;
never turn an unknown outcome into a successful replay. This is a separate
protocol change, not part of the proposed selector audit.

## Previous review — September 21, 2026

Reviewed September 21, 2026 against CSRBT commit
[`2a80194777f159cc4111467a282c199ff174effe`](https://github.com/RicheyWorks/CSRBT/tree/2a80194777f159cc4111467a282c199ff174effe).
Its local checkout and GitHub HEAD matched during the review. The useful ideas
are contract behavior and evidence quality; FlowersForever retains its Java
gateway, application services, Swing context binding, and existing receipt stores.

### Implemented from the previous review

| CSRBT pattern | FlowersForever behavior | Verification |
| --- | --- | --- |
| Replay before freshness; honor supplied deadlines in [`_fresh`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_contract.py#L1423) | Optional deadlines now apply to REST, embedded, and MCP reads/writes. Exact receipt replay stays available after expiry; physical actions retain required expiry and a 30-second cap. | Fixed-clock before/equal/after boundaries, revoked-write replay, REST refusal/recovery, MCP schemas and equivalent timestamp identities. |
| Per-class evidence floors and source freshness in [`ecosystem.py`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/ecosystem.py#L136) | A clean verification runner checks every committed class floor, both test phases, source/report hashes, and fresh quality reports. Failed attempts invalidate previous success. | Fault injection for removed classes, stale/changed reports, ignored source files, missing quality output, failures, skips, flaky retries, and edits during verification. |
| Derived pass/fail status in [`harness_board.py`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_board.py#L126) | The evidence JSON becomes `passed` only after Maven and all evidence gates succeed. `--check` revalidates current files; no manually maintained green badge. | The same evidence validator runs locally and in the JDK 17 CI job. |

The same review also closed FlowersForever-specific gaps: malformed numeric
punctuation can no longer silently change imported values, exponent guards avoid
unbounded integer expansion, and JSON-RPC protocol errors now receive the same
credential redaction and length limit as tool errors.

The following wiring pass connects MCP resources to full discovery/observation,
including observation-only extensions. Reviewed inventory counts and order
handoffs now reuse existing transactional worker services through the harness;
their operation keys and durable results work across the phone API, REST, and
MCP. This provides the existing worker review-token/quantity preconditions to
agents without claiming domain-wide version preconditions. Packaged JVM checks
exercise receipt recovery through these new connections.

### Retained protocol backlog, with acceptance criteria

1. **Explicit uncertain execution receipts.** CSRBT retains unexpected failures
   after dispatch in [`harness_contract.py`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_contract.py#L1399).
   FlowersForever's Swing journal and transactional farm imports already cover
   their own failure boundaries. Before adding a generic gateway receipt, define
   plugin outcomes for preflight refusal, rolled-back execution, accepted work,
   and an unknown outcome. A mutate-then-throw fake plugin must execute once for
   the same request ID; a known validation refusal must remain retryable. Never
   replay an exception as successful work or include raw exception details.
2. **Data-version preconditions for operational writes.** Extend the existing
   desktop observation context with explicit domain record revisions where a
   decision depends on current inventory, orders, or plans. Compare revisions
   inside the write transaction. Two agents acting on one observed version must
   produce one accepted write and one machine-readable stale refusal. Existing
   UI model/context tracking remains useful but does not lock hidden farm state.
3. **Bounded observation differences.** Adapt CSRBT's
   [`diff_of` and baseline ring](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_contract.py#L309)
   for repeated farm observations. Key records by stable IDs, exclude capture-time
   noise, enforce count/byte limits, and return an explicit baseline-expired
   result. Permission revocation must filter both the retained baseline and the
   new observation; test insert/update/delete, reordering, eviction, and redaction.
4. **Cross-transport fault scenarios.** Run one scenario catalog through REST,
   MCP, and embedded adapters: delayed commands, interrupted replies, duplicate
   IDs, changed arguments, permission revocation, and durable restart recovery.
   Publish outcome categories and receipt guarantees per plugin so process-local
   caching is never mistaken for durable exactly-once execution.

The evidence files are verification artifacts, not signed attestations. Hardware,
native OS input, external service accounts, and installer acceptance still need
their own environment-specific checks. A local pass does not establish that
GitHub Actions ran or that a native release was rebuilt.
