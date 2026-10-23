# Epic 54 implementation evidence

Source objective: https://github.com/Easy-BPM/easyBPM/issues/54. Scope/decisions: [approved specification](epic-54-spec.md). Runtime/API/deployment examples: [human-task-sla.md](human-task-sla.md). This is developer evidence, not an independent approval.

| AC | Production behavior and automated evidence |
|---|---|
| AC-1 | `TaskSlaService.configure`, immutable XML policy snapshot and UTC deadline; `TaskSlaIntegrationTest` deployed dynamic XML creation, persisted fixed deadline and DST-zone snapshot. |
| AC-2 | Additive `TaskResponseDto`; `TaskSlaApiIntegrationTest` list/detail/history UTC fields and authorized visibility, plus draft/claim mutation regression. |
| AC-3 | `SlaPolicy.status` exact boundaries/no alerts/no-SLA/completed; `SlaPolicyTest` clock boundaries and actual completion test within-SLA flag. |
| AC-4 | Durable event ledger/action uniqueness/task locking; real PostgreSQL competing replicas, rollback, repeat execution and missed-alert recovery tests. |
| AC-5 | Ordered reassignment/candidate unions, runtime target resolution; PostgreSQL dynamic retry, same-time user/group actions and unchanged process/deadline assertions. |
| AC-6 | Same task/instance locking authority; PostgreSQL completion-versus-scheduler blocking test and two-replica concurrency test. |
| AC-7 | Existing claim/unclaim/draft/reassign/complete paths preserve clock/snapshot; persisted draft and deployed completion regressions. |
| AC-8 | Query-time SQL status, inclusive range validation, null-last stable sort, current visibility and repeatable summary; PostgreSQL queries plus HTTP native/participant isolation and browser filters. |
| AC-9 | Action/event/timeline/incident evidence in one transaction; rollback/idempotent-history assertions, retries and browser history/recovery. |
| AC-10 | Targeted Gradle tests and complete affected checks recorded below; real browser harness and Modeler domain tests. |
| AC-11 | Fixed/dynamic XML, DST/no-SLA/restart/late-alert/target/retry examples in human-task-sla.md. |
| AC-12 | Backend independent static validator, node-specific deployment errors and no dangling invalid runtime task; policy/deployment/incident and Modeler browser validation tests. |
| AC-13 | Existing `easy:node` JSON remains contract; real browser DOM import/edit/export fixture preserves SLA/action IDs, vendor extensions, flow IDs and legacy nodes; deployed legacy definition executes. |
| AC-14 | Original failed action incident correlation, authenticated actor, success-only resolution, stale assignment revision; HTTP forbidden/missing + PostgreSQL repeat/manual reassignment conflicts + Admin 409/retry browser test. |
| AC-15 | Authoritative 15-second Portal metadata polling/countdown without resetting form; real-browser fake clock, edited form, offline failure, selection/filter/sort preservation. |
| AC-16 | Paginated PROCESS/TASK/USER/GROUP aggregates and histories; PostgreSQL nullable averages/rates/action counts, HTTP admin/native scopes and real-browser dimensions/range/history/retry. |
| AC-17 | Elapsed duration/IANA timezone/overflow and task/instance eligibility; policy tests, FAILED deferral recovery, CANCELLED/COMPLETED process skip and completion race. |
| AC-18 | Low-cardinality overdue gauge/action counters, after-commit metrics/logs and correlated durable audit; actual MeterRegistry success/failure/overdue and bounded-tag assertions, no rollback counter increment, ledger tests and event data without process-variable dumps. |
| AC-19 | Fresh PostgreSQL full Flyway V1–V46 migration then V47 upgrade preserves legacy timestamps/null fields; action uniqueness/cascade and four supporting indexes verified. |

## Checks

- Targeted `gradlew.bat test --tests '*SlaPolicyTest' --tests '*TaskSlaIntegrationTest' --tests '*TaskSlaApiIntegrationTest' --tests '*TaskSlaMigrationTest' --tests '*IncidentControllerTest'`: 18 tests passed, including actual PostgreSQL locking/rollback and fresh Flyway upgrade.
- Modeler `npm run lint`, `npm run build`, `npm run test:sla`: passed; 4 domain/XML export/bounds tests.
- Portal `npx tsc --noEmit`, `npm run build`: passed.
- Admin `npm run build`: passed. Separate unrestricted TypeScript invocation still encounters pre-existing Jest test files with missing test dependencies; it is not the configured build gate.
- Real browser `npm run test:sla:ui`: Modeler, Portal and Admin scenarios passed with installed Chrome and Playwright module provided by the workspace runtime. See `easy-bpm-modeler/tests/sla-ui.mjs`; discovery/environment inputs are documented in the script, no machine-specific paths committed.
- `git diff --check`: passed.
- Complete backend/worker `gradlew.bat test`: passed independently (426 tests, 420 passed, 6 skipped, zero failures; BUILD SUCCESSFUL in 1m24s).

## Independent review defect correction

BUG-1 (AC-12/13): Modeler bounds differed from backend. Corrected action IDs to 100 characters, targets to 255 and policy to 32768 serialized JSON characters. Exact valid/invalid boundary unit regressions and browser editor error/recovery assertions pass; Modeler lint/build and all three browser scenarios passed again. Fresh independent review and retests passed; see epic-54-qa.md for APPROVED_FOR_HUMAN_REVIEW.

## Review hotspots and limits

Review SQL-derived current status versus event transitions, task/instance lock order, action transaction/uniqueness, assignment revision retry conflict, current identity validation, repeatable summary snapshot, GROUP non-additivity, policy/XML preservation and Portal metadata updates without input reset. Internal alert publication is durable database history; provider-specific delivery is deliberately outside scope. SLA status is derived from persisted clocks/lifecycle, with transitions represented in the durable ledger. No worker protocol change, branch, commit, push or PR was made. Pre-existing untracked generated legacy Modeler assets were preserved.

Next role: independent QA. All verdicts and newly verified defects must come from that role/root independent review; developer does not approve its own work.
