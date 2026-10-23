# Epic 55 independent QA

Source: [Visual Business Rules Builder with DMN](https://github.com/Easy-BPM/easyBPM/issues/55). Reviewed against [epic-55-spec.md](epic-55-spec.md), the final application diff, [implementation evidence](epic-55-implementation-evidence.md), and repository invariants on 2026-10-20.

## Findings

No unresolved blocking defect remains. QA did not edit production or test source. The developer corrected the following defects and QA reviewed the corrections and fresh regression evidence:

| ID | Severity / AC | Observed failure and reproduction | Correction and retest |
| --- | --- | --- | --- |
| BUG-1 | P1 / AC-7 | Map a named output from COLLECT: the runtime treated the list as a single object and failed. | Ordered projection, including empty arrays; handler tests and browser mapping checks pass. |
| BUG-2 | P1 / AC-7 | Map `customer.age` from a nested process variable: exact-key lookup reported missing input. | Dotted traversal distinguishes missing values and explicit null; unit and real process integration tests pass. |
| BUG-3 | P1 / AC-10 | Import unsupported DMN semantic children such as outputValues: the editor silently discarded them. | Closed XML subset and structural checks reject unsupported semantics; prefix, escaped-text, custom-ID and supported DMN/BPMN round trips pass. |
| BUG-4 | P1 / AC-2 | Use adjacent high-precision decimal literals or playground inputs: ordinary floating parsing could collapse distinct values. | Exact backend decimal parsing and raw playground/saved-case JSON requests; unit, actual HTTP and browser request-token tests pass. Static UI values that cannot round-trip exactly are rejected. |
| BUG-5 | P2 / AC-6 | Deploy a decision key longer than 128 characters: the backend accepted it but a referencing BPMN task did not. | Shared 128-character constraint in backend/runtime/Modeler. |
| BUG-6 | P2 / AC-7 | Map an ordinary output named result: a whole-result shortcut intercepted that name. | Reserved `$result` selector; ordinary result column regression passes. |
| BUG-7 | P2 / AC-8, AC-9, AC-12 | Fail a runtime mapping/evaluation: failure audit duration was always zero and available diagnostic context was dropped. | Measured elapsed duration, safe code, redacted available inputs/result and match metadata; final runtime/integration evidence passes. |
| BUG-8 | P1 / AC-6, AC-8 | Retry a failed decision through the actual incident API: only worker/SLA retry existed. | Dedicated DECISION_TASK recovery, Admin action, locked status recheck and persistent pin; failed retry increments occurrence, successful recovery resolves incident, concurrent retry produces one advancement and one conflict. Real PostgreSQL/filter-chain and browser regressions pass. |
| BUG-9 | P2 / AC-2, AC-7 | Enter JSON string `"0.1000000000000000001"` in a static mapping: precision validation reparsed the string contents and display lost JSON quotes. | Raw drafts/errors separated from typed values. Fresh browser tests preserve numeric-text strings, null and nested JSON through export/reopen while retaining precision rejection for actual numbers. |

## Acceptance evidence

| Criterion | Evidence reviewed and result |
| --- | --- |
| AC-1 | Browser editor adds/removes/edits inputs, outputs and rules, reorders rows and changes policies: PASS. |
| AC-2 | DecisionEvaluatorTest covers strict types, date validity, nested JSON, missing/null, precision, supported grammar and limits. HTTP precise decimal and typed static mapping browser regressions: PASS. |
| AC-3 | Deterministic UNIQUE/FIRST/COLLECT, document order, overlap diagnostics and no-match unit tests: PASS. |
| AC-4 | Draft validation/playground UI, explanation display, saved-case persistence and expected/actual comparison against selected versions: PASS. |
| AC-5 | Concurrent PostgreSQL deployments create unique immutable versions; previous XML is unchanged; history/diff service and Admin browser tests: PASS. |
| AC-6 | Native BPMN codec/validation tests, first-entry pin, later deployment/re-entry, exact-version instance and actual retry retaining the pin: PASS. |
| AC-7 | Typed variable/static mappings, nested input, COLLECT projection, reserved full-result selector, complete mapping validation before writes and one advancement under concurrent recovery: PASS. |
| AC-8 | First synchronous failures, missing definition, no-match and mapping errors persist failed audits/incidents without rolling back process creation; actual recovery and duplicate conflict: PASS. |
| AC-9 | Redaction canaries, safe failure context, Admin execution details/explanations, filters, pagination, empty/error states: PASS. |
| AC-10 | Closed-subset DMN, external-resource rejection, namespace prefix/escaped text/custom-ID preservation and native BPMN mapping round trips: PASS. |
| AC-11 | Actual Spring security filter tests cover anonymous, portal, Modeler/Admin authorities, real native API-client credentials and privileged retry denial. Browser Admin gating: PASS. |
| AC-12 | Meter registry success/failure/volume/duration/matched-rule assertions and inspection for payload-free tags: PASS. |
| AC-13 | The documented expense DMN and BPMN execute without modification in BusinessRuleTaskIntegrationTest; guide covers language, policies, mappings, pins, operations and privacy: PASS. |
| AC-14 | Required final checks below pass; existing disabled tests and supplemental check limitations are disclosed. |

## Final verification

QA independently executed the final Modeler lint, web build and desktop build; Admin build; and all four browser harnesses via `npm run test:dmn:ui`. All passed. The final browser run includes BUG-9 string/null/nested-value and precision regressions, XML compatibility, decision lifecycle and Admin incident recovery.

The developer's final Java 21 `./gradlew.bat test` run completed successfully with Docker/PostgreSQL. QA independently inspected the fresh XML reports: **455 tests, 0 failures, 0 errors, 6 skipped**. All 29 tests in the new decision/evaluator/runtime/security/migration suites ran and passed. Reports are available under `build/test-results/test` and `build/reports/tests/test`.

Full-suite failures found during iteration were addressed with test-only compatibility/isolation corrections: scope the SLA migration fixture to V47, use valid BPMN instead of placeholder XML, isolate task owners, and clean up newly created process instances. No production XML validation was weakened.

## Observations and limits

- The six disabled backend scenarios predate this epic: one parallel-gateway scenario and five worker scenarios. They are not counted as passing tests.
- The required Admin build passes. The developer reports that an optional broad Admin TypeScript check still encounters existing test-library/Jest declarations and unrelated SecurityAdminView typing errors; targeted new production-file type checks pass. QA does not claim the broad optional check passed.
- Browser tests use real React/DOM/XML behavior with mocked HTTP responses. Real API authorization, persistence, migration, runtime failure and concurrency behavior are covered separately by PostgreSQL/filter-chain integration tests.
- Sandboxed Chrome initially could not reach loopback; the permitted elevated local rerun passed. Docker access likewise required the environment's approved access. No environment-limited test is reported as passed.
- The documented constrained DMN language and bounded overlap witness analysis are intentional initial scope. Runtime UNIQUE enforcement remains mandatory. Full DMN/FEEL, arbitrary scripts and graphical DRD editing remain out of scope.
- V48 must be applied through normal Flyway deployment. No deployment, commit, push or PR was performed by QA.

Next role: human reviewer. Review the V48 migration, the documented supported language, version pinning/recovery behavior and UI together.

APPROVED_FOR_HUMAN_REVIEW
