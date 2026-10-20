# Epic 55 implementation evidence

Source: https://github.com/Easy-BPM/easyBPM/issues/55. Specification: [epic-55-spec.md](epic-55-spec.md). User guide and runnable artifacts: [decision-rules.md](decision-rules.md).

## Delivered behavior

- Modeler visual decision-table editor with typed columns, ordered rows, UNIQUE/FIRST/COLLECT policies, draft validation/playground, immutable deployment, saved test cases, and version selection.
- Strict supported-subset DMN XML interchange; native BPMN Business Rule Tasks with exact or latest-at-first-entry durable version pins and typed variable/static mappings.
- PostgreSQL V48 schema, serialized version publication, secure bounded deterministic evaluation, precision-preserving decimal handling, saved-case comparison and version differences.
- Runtime mapping atomicity, sanitized incidents, redacted audits and metrics. Operator retries retain the pinned version, preserve failure history, resolve successful recovery, and reject stale/concurrent retries under the instance lock.
- Admin decision version history/differences, filtered/paginated execution inspection, condition explanations, and decision incident retry controls.

## Verification on 2026-10-20

| Check | Result |
| --- | --- |
| Java 21 `./gradlew.bat test`, with Docker/PostgreSQL | PASS; 455 tests, 0 failures/errors, 6 existing disabled tests |
| Modeler `npm run lint` | PASS |
| Modeler `npm run build` | PASS |
| Modeler `npm run build:desktop` | PASS |
| Admin `npm run build` | PASS |
| Modeler `npm run test:dmn:ui`, four browser harnesses including Admin | PASS; independently repeated by QA |
| Targeted Admin production types/service/DecisionOperations type check | PASS |
| `git diff --check` | PASS |

Backend tests include expression/types/policies/precision/limits, DMN parsing, BPMN configuration compatibility, mapping failure atomicity, immutable concurrent deployments, saved cases, real authorization filters and native API-client denial, redaction/metrics, real Flyway V47-to-V48 compatibility, first synchronous process failures, durable pinning, and competing incident retries. The documented DMN/BPMN files execute as an integration test.

The full run also exposed shared SLA fixture assumptions: the SLA migration test now explicitly targets its V47 scope; fixtures use valid BPMN rather than placeholder XML; task owners are isolated; new decision runtime tests clean up their process instances. These are test-only compatibility/isolation fixes.

## Limits and handoff

The supported DMN/FEEL subset and bounded overlap-analysis warning are documented in the guide. Full DMN and arbitrary script execution remain out of scope. V48 must be applied by normal Flyway startup/deployment before the new backend is used.

The six disabled backend tests predate this epic (one parallel-gateway scenario and five worker scenarios). The optional full Admin TypeScript check still reports existing missing Jest/testing-library declarations and unrelated SecurityAdminView typing errors; the required Admin build and new production-file type check pass. Browser tests require Playwright, an installed browser channel, and local loopback access as documented.

Existing user changes were preserved. No commit, push, pull request, or deployment was performed. Independent QA owns the final verdict in [epic-55-qa.md](epic-55-qa.md).
