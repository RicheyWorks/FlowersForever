# Harness ideas from the CSRBT ecosystem

Reviewed September 21, 2026 against CSRBT commit
[`2a80194777f159cc4111467a282c199ff174effe`](https://github.com/RicheyWorks/CSRBT/tree/2a80194777f159cc4111467a282c199ff174effe).
Its local checkout and GitHub HEAD matched during the review. The useful ideas
are contract behavior and evidence quality; FlowersForever retains its Java
gateway, application services, Swing context binding, and existing receipt stores.

## Implemented in this pass

| CSRBT pattern | FlowersForever behavior | Verification |
| --- | --- | --- |
| Replay before freshness; honor supplied deadlines in [`_fresh`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_contract.py#L1423) | Optional deadlines now apply to REST, embedded, and MCP reads/writes. Exact receipt replay stays available after expiry; physical actions retain required expiry and a 30-second cap. | Fixed-clock before/equal/after boundaries, revoked-write replay, REST refusal/recovery, MCP schemas and equivalent timestamp identities. |
| Per-class evidence floors and source freshness in [`ecosystem.py`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/ecosystem.py#L136) | A clean verification runner checks every committed class floor, both test phases, source/report hashes, and fresh quality reports. Failed attempts invalidate previous success. | Fault injection for removed classes, stale/changed reports, ignored source files, missing quality output, failures, skips, flaky retries, and edits during verification. |
| Derived pass/fail status in [`harness_board.py`](https://github.com/RicheyWorks/CSRBT/blob/2a80194777f159cc4111467a282c199ff174effe/tools/harness_board.py#L126) | The evidence JSON becomes `passed` only after Maven and all evidence gates succeed. `--check` revalidates current files; no manually maintained green badge. | The same evidence validator runs locally and in the JDK 17 CI job. |

The same review also closed FlowersForever-specific gaps: malformed numeric
punctuation can no longer silently change imported values, exponent guards avoid
unbounded integer expansion, and JSON-RPC protocol errors now receive the same
credential redaction and length limit as tool errors.

## Next improvements, with acceptance criteria

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
