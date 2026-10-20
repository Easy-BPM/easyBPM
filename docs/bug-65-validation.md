# Bug #65: required task form inputs

Source: https://github.com/Easy-BPM/easyBPM/issues/65 (BUG-1 / TASK-002).
Base: `easyBPM/master`, `fbcc6325e63037ebb9be61209d7332fda196e22e`.

## Contract and decisions

Validate the form version referenced by `Task.formId`, the database ID pinned at task creation. Later form deployments do not change existing task rules. An unavailable pinned form blocks completion with a safe HTTP 409 message.

Effective values are persisted task inputs/drafts overlaid by submitted fields. Omitted fields retain saved values; explicit null overrides them. For legacy duplicate task-variable rows the newest ID wins, matching output mapping. Process variables are not an implicit fallback: configured input mappings materialize task values.

Top-level required fields must be present, non-null and match their declared type without coercion. Required strings reject empty/whitespace-only values; required arrays reject empty arrays. Zero and false remain valid. Read-only is a UI hint, not a server-validation bypass. This explicit EasyBPM rule is stricter than JSON Schema `required`, which alone checks presence.

Task locking and authorization precede validation. Validation precedes automatic assignment, writes, output mapping, process advancement, metrics and completion events. Invalid values return HTTP 400 identifying the field without echoing input. Incomplete drafts and tasks without forms keep their existing behavior.

## Acceptance evidence

| Criterion | Evidence |
| --- | --- |
| AC-1 | A valid saved draft completes without resending fields even after a newer form version requires a different field. Invalid submitted overrides do not fall back to the valid draft. |
| AC-2 | Omitted, null, empty, whitespace and wrong-type required text are rejected. MockMvc verifies request deserialization and the HTTP 400 body using the real transactional service. |
| AC-3 | Rejection rereads task status, assignee, process status/position, task/process variables and timeline from PostgreSQL and verifies no Rabbit calls. An invalid shared-task submission does not claim it. |
| AC-4 | Incomplete drafts save successfully; valid new input and valid drafts complete while preserving numeric zero and boolean false. No-form completion is covered. |
| AC-5 | Isolated TASK-002 fixture uses BPMN XML, an empty process description and omitted required task input. On unpatched master five invalid-input cases returned HTTP 200 instead of 400; two positive cases passed. All pass after the fix. |

Added coverage: 13 PostgreSQL integration cases and 3 validator unit cases, including authorization denial and a missing pinned form.

Validation: `./gradlew.bat test` passed. Final test counts are recorded in the pull request. `git diff --check` passed.

## Scope and limits

This fix handles top-level required values and their declared types. General JSON Schema constraints, nested validation, numeric bounds, patterns and enums remain the separate scope of #71. No migration or BPMN contract change; already completed instances are not repaired.

The Portal already loads the pinned `formDbId`, displays the API error and preserves entered data on failure, so no frontend source change was necessary. Tests use PostgreSQL Testcontainers and mocked Rabbit publication. The HTTP case uses MockMvc without the security filter chain; service authorization is tested separately. No deployed browser or existing local Docker campaign was rerun or replaced.
