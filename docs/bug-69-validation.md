# Bug #69: parallel join completion

Source: https://github.com/Easy-BPM/easyBPM/issues/69

Branch: `codex/fix-69-parallel-join`, based on remote `easyBPM/master` at `fbcc6325e63037ebb9be61209d7332fda196e22e`. Scope is this defect only.

History collection no longer evaluates gateways or consumes join arrivals. Exclusive routing no longer increments a downstream parallel join's counter; the join itself owns arrival counting.

Transitions consume the node of the advancing branch and preserve the other active nodes. A join that is waiting remains active. Reaching an EndEvent finishes that branch; the process completes and archives its variables only when no active branch remains. Message, timer and worker callbacks carry the source node into the same transition logic.

No BPMN XML format, database migration or permission rule changes are required. The Admin display follow-up is documented below. Existing process-instance locking and task-completion locking remain in place. This change does not reconstruct instances that were already completed incorrectly.

## Acceptance evidence

| Criterion | Evidence |
|---|---|
| AC-1: wait for all branches | PostgreSQL integration tests cover either completion order and three branches; remaining task and runtime variables stay available. |
| AC-2: advance once | The final node and PROCESS_COMPLETED event are asserted once; internal service-task split/join regression was reactivated. |
| AC-3: concurrency and duplicates | Two threads synchronized before execution cover different branch completions and duplicate completion of the same task. |
| AC-4: no early archival | Waiting assertions verify live process variables, ACTIVE status and no premature PROCESS_COMPLETED event. |
| AC-5: persistence/locking compatibility | Tests run against PostgreSQL Testcontainers, with real transactions for concurrency. Additional cases cover worker callbacks, messages, timers, exclusive routing into a join and independent branch endings. |

The two-order human-task regression failed with premature completion before the fix. `ParallelJoinIntegrationTest` now has ten passing cases.

Final validation: `./gradlew.bat test` completed successfully: **422 total, 417 passed, 5 skipped, 0 failures/errors**. The remaining skips are the pre-existing disabled WorkerIntegrationTest class. `:worker:test` has no source tests. These results describe this branch from master, not the different feature-branch checkout used for the earlier QA campaign. Rabbit publishing is mocked in the integration fixtures; broker redelivery and multi-node load are not part of this validation.

The correction is published in PR #73; no merge or deployment was performed. Other reported bugs remain out of scope. Next step is human review.


## Admin display follow-up (2026-10-21)

The screenshot exposed a separate presentation assumption: the Admin treated consecutive nodeHistory entries as a single path. Interleaved parallel branches therefore highlighted fork -> right but not fork -> left, despite both tasks being active. The canvas now supplements ordered history with reached parallel branches and active-token state. Waiting joins highlight arrivals only from completed branches; their outgoing edge remains unhighlighted. Passed gateways omitted from history are inferred conservatively from finished incoming nodes and an already reached outgoing destination. This is visual inference from node history/current state, not a newly persisted sequence-flow audit; it does not reconstruct exact loop traversal counts.

The __pg_<gatewayId>_arrivals variable is the existing engine-managed join counter stored transactionally with process state, not a form/business variable. It is retained for synchronization and archival. The Admin separates these names into a collapsed, read-only Runtime diagnostics panel and blocks creating/editing them through its business-variable controls. Backend storage, APIs and permission rules are unchanged; this UI restriction is not a new backend authorization boundary.

Admin Chrome regression: both active branches, each order of join arrival, no premature join exit, completed flow, unchanged exclusive-gateway selection, the real InstanceExplorerView variable panel and blocked reserved-name assignment. API fixtures are isolated, while the real Admin components and BPMN XML parser run in Chrome. No live environment is replaced. Run npm run test:parallel-progress:ui with Playwright installed or provided via NODE_PATH, plus Chrome. Admin production build and git diff --check pass. Backend code was unchanged by this follow-up; the passing backend suite above remains its validation evidence.
