# Bug #71: form value constraints

Source: https://github.com/Easy-BPM/easyBPM/issues/71.

## Contract

Portal completion validates top-level declared value types, minimum/maximum, integer, multipleOf, text minLength/maxLength/pattern and enum. Errors appear beside the affected fields with accessible descriptions. Invalid input stays in place and no completion request is made. Numeric enums preserve numbers rather than converting options to strings.

The backend authoritatively applies the same constraints to the exact pinned Task.formId version and effective persisted task values overlaid with submitted fields. Invalid overrides cannot fall back to a valid draft. Validation occurs inside the locked completion transaction before variable/output writes or completion publication; rejection rolls back automatic assignment as well. Error responses are HTTP 400 with the field name and rule, without echoing values. Draft saves remain unconstrained and may be corrected later. Read-only fields remain disabled in the UI but their values do not bypass backend rules.

This complements #65 / PR #74, which covers required presence, blank required text and required null handling. It is based independently on master and does not copy that PR. The methods and call sites are kept separate to reduce integration overlap.

Optional empty controls (empty string/null/missing) keep their existing unset-value behavior; required presence is checked separately. Supported constraints are top-level, not a complete JSON Schema implementation: nested schemas, composition, date bounds and formats are not added here. Patterns use unanchored matching (an anchored pattern must provide its own anchors). Invalid patterns fail closed with a safe configuration message. Text lengths count Unicode code points. Backend decimal multiple checks use BigDecimal; frontend checks tolerate floating-point representation error and backend remains final authority.

## Evidence

- Thirteen PostgreSQL integration cases: nine direct API violations rejected without changing task/process position, persisted draft, timeline or publication; valid boundary values zero/100 and false; draft correction with a later form version; and rollback of shared-task automatic assignment. The nine invalid cases fail against unpatched master while three positive cases pass.
- Chrome regression mounts real TaskDetailView, first reproduces a completion request for a minimum violation on master, then checks minimum/maximum, integer/multipleOf, pattern/minLength, retained input, incomplete draft save/reopen, read-only fields and numeric enum/zero/false payload types. API fixtures are intercepted; it is not a live-backend browser E2E.
- Full backend test results, Portal build and browser regression are recorded in the PR. Publisher is mocked in integration tests; PostgreSQL and service transactions are real.

No migrations, BPMN contract changes or authentication changes are included. TaskDetailView is exported solely to allow the component browser harness to exercise the same production flow.
