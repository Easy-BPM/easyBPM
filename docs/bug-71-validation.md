# Bug #71: presentation form constraints

Frontend supports minimum/maximum, integer/multipleOf, string lengths/patterns and enums. Invalid fields block completion requests, retain entered data, and show messages. Drafts may be saved incomplete. Numeric zero and boolean false remain typed values.

## Updated contract — PR #74

By explicit product decision, all form validation occurs only in the frontend. The backend validator originally introduced by #71 is removed by #74. API completion does not enforce form required fields, types or constraints. Essential business rules belong to explicit process modeling; task authorization, state checks, locking and execution invariants remain enforced.

FormConstraintsIntegrationTest now verifies API acceptance of values outside presentation constraints, while the Chrome form regression verifies frontend blocking, drafts and field types. Backend cases use real PostgreSQL and mocked Rabbit publication; frontend cases use isolated API fixtures. Required-field details and validation evidence are in [bug-65-validation.md](bug-65-validation.md).
