# Bug #66: interactive form preview

Source: https://github.com/Easy-BPM/easyBPM/issues/66 (BUG-2 / UI-FORM-002).

Decision: retain Submit as a local validation demonstration. It never submits, deploys, saves or starts a process. Validation errors appear beside fields; the first invalid field receives focus and its tab opens. Preview values persist across tabs, and success displays their typed values. Changing input or the form clears stale success feedback.

## Acceptance criteria

- AC-1: local-only behavior is explained in the preview and the Submit action validates all tabs.
- AC-2: required short/long text, number, select, radio and date inputs show actionable inline messages. Empty/whitespace required text is rejected. Required checkboxes accept false, rather than requiring consent.
- AC-3: the browser regression submits number zero and unchecked boolean false and asserts their JSON types in the local result. No network writes occur.
- AC-4: validation uses the same field definitions as generateJsonSchema: required, text lengths/pattern, numeric bounds/multiples, enum choices, date/date-time bounds. Patterns use JSON Schema substring matching; invalid expressions produce feedback rather than crashing. File upload/download/PDF are display-only, excluded from local validation/result, with a visible limitation notice. This is not a general JSON Schema engine or server-validation replacement.
- AC-5: tests/form-preview-ui.mjs mounts the real FormModeler in Chrome via Vite. It fails on unpatched master because the required input has required=false. With the fix it covers constraints, hidden-tab errors, typed values, read-only fields, malformed patterns, date/time bounds, state preservation and absence of network submissions/page errors.

Validation: Modeler lint, production build and browser regression. No BPMN serialization, backend or desktop bridge change. Desktop packaging is outside this frontend-only change. Playwright and Chrome must be installed; NODE_PATH may point to a provided Playwright runtime.
