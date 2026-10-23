# Epic 54 independent QA

Objective: https://github.com/Easy-BPM/easyBPM/issues/54. Reviewed against [approved specification](epic-54-spec.md), [implementation evidence](epic-54-implementation-evidence.md), and [configuration/deployment guide](human-task-sla.md).

Reviewer: root delivery coordinator, independent of application and test-source implementation. The dedicated QA agent could not be started because the session agent-thread limit was reached; this reviewer performed source/diff review and independent checks, and returned fixes to the developer. No application or test-source fixes were made by the reviewer.

## Findings

BUG-1 (P2, AC-12/AC-13), resolved and independently retested: Modeler validation permitted action IDs of 101–255 characters although backend/database permitted at most 100. Target and serialized-policy limits were also missing from Modeler validation. The developer aligned limits to 100 ID characters, 255 target characters and 32768 serialized JSON characters. Exact-limit acceptance and over-limit rejection have automated coverage; the browser exercises editor errors and recovery. No unresolved blocking defect remains.

Review observations on inactive-process transitions, metric rollback and counter snapshots were addressed with durable eligibility checks, after-commit metric/log publication, repeatable-read aggregate queries and regression evidence. These observations have no remaining release blocker.

## Evidence

- Independently executed complete backend/worker `gradlew.bat test` with Docker/PostgreSQL: BUILD SUCCESSFUL, 426 tests, 420 passed, zero failures, six skipped. Skipped cases are in existing ProcessIntegrationTest (one) and WorkerIntegrationTest (five); all 14 new SLA tests ran without skips. Worker has no unit-test source.
- PostgreSQL evidence covers concurrent replicas, rollback, completion locks, restart/missed alerts, FAILED deferral, CANCELLED/COMPLETED eligibility, target failure and retry, stale assignment revision, candidate unions/order, real deployed XML task creation/completion and legacy definitions, security/API visibility, aggregates and Flyway V1–V46 to V47 upgrade. Actual MeterRegistry checks cover counters, overdue gauge, rollback and bounded labels.
- Independently executed final Modeler lint and all three application builds successfully. After BUG-1, developer reran Modeler lint/build successfully; independent regression retest ran all four Modeler domain/XML/boundary tests successfully.
- Independently executed real Chrome/Playwright harness both before and after BUG-1: Modeler editor/validation/XML import-edit-export; Portal fake-clock authoritative refresh with unsaved inputs, countdown, persistent filters/sort and offline failure; Admin ranges/dimensions/history and retry conflict/success all passed.
- Source review verified additive task DTOs, existing task visibility/native scope boundaries, admin-only metrics/retry, UTC persisted instants, existing BPMN JSON extension compatibility, append-only migration, task/instance lock order, action uniqueness, audit correlation, stable null-last deadline sorting and preserved draft/completion clock.
- Final `git diff --check` passed. Existing generated/dependency files were preserved and not staged.

The AC-1–AC-19 mapping in the implementation evidence was reviewed against production paths and the checks above. All criteria have evidence. Existing Admin standalone TypeScript test-dependency issues and the existing Modeler bundle-size warning do not fail the configured gates; Admin build and real SLA browser tests passed.

## Deployment and limits

Apply V47 and deploy backend/frontends together. Alerts are durable reusable internal events; provider-specific notification delivery is outside the approved initial scope. SLA status is derived from persisted deadlines/lifecycle and durable transition history. No commit, push, pull request, production deployment or issue closure was performed.

APPROVED_FOR_HUMAN_REVIEW
