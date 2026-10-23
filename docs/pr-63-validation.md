# PR #63 integration validation

Source: https://github.com/Easy-BPM/easyBPM/pull/63; SLA specification: [Epic 54](epic-54-spec.md), AC-1–AC-19. Validation integrates PR head `3a5c01d1a` (including merged DMN PR #64) with master `97ccc5070`. Existing DMN functional approval remains separate; this pass checks SLA/escalation and integration boundaries.

## Merge resolution and verified defects

- Admin imports retain SLA, decision operations and master's parallel-progress helper.
- Modeler retains SLA, DMN and form-preview test scripts.
- ProcessNavigator uses master's history filter, preserving BusinessRuleTask history while avoiding a second gateway evaluation that consumes parallel-join arrivals.
- BUG-2 (AC-7/10/13/19): completing an SLA human task followed by a BusinessRuleTask stranded the instance at the rule without execution or incident. Two real PostgreSQL regressions failed before the fix. TaskService now delegates that node to the existing ProcessExecutionEngine, preserving decision pinning, output mapping, boundary handling and recoverable incidents. An engine-recorded failure returns the existing committed-completion incident contract without recording a second incident. Regressions verify deadline retention, output archival, one successful execution, duplicate-completion rejection, failure persistence and retry against the pinned version after a newer decision is published.
- BUG-3 (AC-10): background SLA schedulers in cached test contexts processed historical clock-controlled fixtures against wall-clock time; permissive unassigned visibility also exposed unrelated fixtures to an unscoped summary assertion. Production polling retains its zero initial delay; the test profile sets SLA-only initial delay to 24 hours. Tests call process/poll explicitly, and query assertions scope the two fixture instances while retaining the hidden-owner check. A real poll regression checks persisted action discovery and repeated-poll idempotence. Other schedulers are unchanged.

## Acceptance evidence

| Criteria | Evidence |
|---|---|
| AC-1–7, AC-17–18 | TaskSlaIntegrationTest and SlaPolicyTest cover UTC/DST deadlines, snapshots, status boundaries, lifecycle preservation, ordered escalation/retry, PostgreSQL locking/rollback, metrics and explicit scheduler polling. |
| AC-8/15 | Real Chrome Portal harness: authoritative refresh with simulated time, countdown, edited form preservation, filters and offline error. |
| AC-9/14/16 | Real Chrome Admin harness: dimensions, date/status filters, task history, retry conflict/success and instance navigation. |
| AC-12/13 | Four Modeler SLA domain tests and real DOM XML import/edit/export plus editor validation; all passed on the integrated tree. |
| AC-7/10/13/19 | BUG-2 PostgreSQL regressions cover SLA human-task completion immediately before a decision and the failure/retry contract. |
| AC-19 | Existing V1–V46 migrations remain unchanged; only append-only V47 and V48 are added. Independent QA ran Flyway 46→47 and 47→48 on PostgreSQL 16. |

## SQL and upgrade safety

Independent QA also applied all 48 scripts in numeric order on isolated PostgreSQL 15.19, seeded an existing task before V47, and verified unchanged legacy local timestamps, null SLA fields and assignment revision zero. It verified eight indexes, action/version/pin uniqueness, protection against deleting a pinned definition, and task-action/instance-pin cascades. Injected failure after the actual V47/V48 scripts under a single transaction rolled back all changes; reapplication succeeded. No migration source was rewritten. Normal ALTER/index locks still require a suitable rollout window; production-scale migration duration was not benchmarked.

## Checks and handoff

- Final integrated frontend: Modeler SLA unit 4/4; Chrome SLA harness for Modeler, Portal and Admin passed.
- BUG-2 red reproduction: both new PostgreSQL regressions failed before the production fix; both passed afterward.
- Fresh independent full `gradlew.bat test`: 528 tests, 523 passed, zero failures/errors, five existing WorkerIntegrationTest skips. SLA: 15 tests including 11 lifecycle/poll integration tests; BusinessRuleTaskIntegrationTest: nine including both BUG-2 regressions. PostgreSQL Flyway V47/V48 upgrade suites passed.
- Independent frontend baseline: Modeler lint/build and form-preview browser regression, Admin build, Portal build, and all three SLA browser scenarios passed.
- `git diff --check` is clean. No worker protocol, BPMN serialization format, schema amendment or authentication/visibility rule was introduced by conflict resolution or defect corrections.

Next role: independent QA. This document is developer evidence, not an approval. No merge to master or deployment is part of this validation.
