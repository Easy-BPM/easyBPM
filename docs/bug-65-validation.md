# Bug #65: form validation belongs to the frontend

Source: #65 / PR #74. Product decision: form schemas describe human interaction, not API completion requirements. Process business rules must be modeled explicitly and remain independent of the form.

## Contract

- Portal checks required fields, rejects blank/whitespace required text, and keeps existing field constraints, messages and entered values. Zero and false remain valid. Invalid forms must not send a completion request.
- API completion does not validate required fields, types, min/max, lengths, patterns or enums against the form schema. An authorized caller may omit form variables, override a saved draft with null/empty values, or send values outside presentation constraints.
- API completion does not require loading the form. Missing form metadata does not prevent API completion.
- Locking, task ownership/candidates, permissions, task state, duplicate prevention, transactional variable persistence, output mapping and process execution remain enforced. Modeled process rules can still reject or fail downstream execution.
- Drafts remain saveable before the form is valid. Existing form-backed output mappings and pinned rendering versions are unchanged.

The required-field and constraint validators were removed from TaskService and deleted as unused backend code, including validation introduced by #71. Their server-rejection tests now assert API acceptance and preserved values; frontend rules stay active.

## Validation

RequiredFormCompletionIntegrationTest exercises omitted, null, empty, whitespace and wrong-type form values, drafts, zero/false, shared task claim, forbidden ownership, missing forms, no-form completion, HTTP success and duplicate rejection. FormConstraintsIntegrationTest exercises direct completion outside min/max, integer/multiple, length/pattern, type and enum presentation constraints.

The Chrome form regression checks empty/whitespace required text and missing required number before any request; verifies zero/false and existing constraints, drafts, read-only controls and submission types. It uses isolated API fixtures. Backend tests use PostgreSQL with mocked Rabbit publication. No live deployment or identity-provider E2E is claimed.

Final test/build results are recorded in the PR. No migration or BPMN persistence change.
