# EasyBPM delivery team

## Repository map

- `src/main/kotlin/com/easy/bpm`: Kotlin/Spring Boot API and BPM runtime.
- `worker`: asynchronous RabbitMQ worker.
- `easy-bpm-modeler`: React/Vite BPMN and form modeler.
- `easy-bpm-admin`: React/Vite operations and administration console.
- `easy-bpm-task-portal`: React/Vite human-task portal.
- `src/main/resources/db/migration`: append-only Flyway migrations.

Use the repository skills under `.agents/skills` whenever the task touches their area. For changes spanning applications, load every affected skill and verify the contracts at each boundary.

## Independent delivery roles

Use the custom agents in `.codex/agents` when the user asks for the EasyBPM team, a full feature lifecycle, roadmap-to-PR delivery, or explicit delegation. Do not start the full workflow for a narrow question or a small isolated edit.

1. `product_manager` turns product intent into an ordered, outcome-based backlog item. It owns scope, priority, dependencies, milestones, and issue-ready content. It may publish GitHub issues only when the user explicitly asks for publication.
2. `business_analyst` consumes an issue or roadmap item and produces an implementation-ready specification grounded in the current codebase. It owns business rules, acceptance criteria, API/data impacts, permissions, exceptions, and traceability.
3. `easybpm_developer` consumes the approved specification, implements the smallest coherent change, runs the relevant checks, and prepares or opens a pull request when requested. It must not silently invent unresolved business rules.
4. `easybpm_qa` reviews the resulting diff or pull request, executes relevant tests, probes edge cases, and emits exactly one verdict: `APPROVED_FOR_HUMAN_REVIEW` or `CHANGES_REQUESTED`.

The roles are independent: Product Manager and Business Analyst do not edit application code; QA does not fix the code it reviews; the developer does not approve its own work. If QA returns `CHANGES_REQUESTED`, send the defect report back to `easybpm_developer`, then require a fresh QA pass after the fix.

## Handoff contract

Every handoff must identify the source issue or objective, assumptions, decisions, artifacts produced, unresolved questions, and the next role. Preserve stable identifiers for acceptance criteria (`AC-1`, `AC-2`) and defects (`BUG-1`, `BUG-2`) so implementation and QA can trace results.

A feature is ready for implementation only when it has observable acceptance criteria, explicit out-of-scope items, affected modules, business and permission rules, error behavior, and a test matrix. A feature is ready for human review only when all acceptance criteria have evidence, required checks pass, and QA has no unresolved blocking defect.

## Engineering invariants

- BPMN 2.0 XML is the persisted process-definition contract; changes to modeler serialization must remain compatible with backend parsing and existing definitions.
- PostgreSQL is the source of truth for runtime state. Keep state transitions transactional and preserve clustered-execution locking/idempotency behavior.
- RabbitMQ transports asynchronous work; the backend owns process state and decides how execution advances.
- Never modify an applied Flyway migration. Add the next versioned migration and cover compatibility with existing data.
- Keep API types aligned across controllers, frontend services, and UI types. Treat authentication, authorization, and tenant/user visibility as acceptance criteria, not follow-up work.
- Do not commit secrets, generated build output, dependency directories, or local environment files.

## Validation baseline

- Backend and worker: `./gradlew test` (or `./gradlew.bat test` on Windows).
- Modeler: `npm run lint` and `npm run build` from `easy-bpm-modeler`.
- Admin: `npm run build` from `easy-bpm-admin`, plus relevant tests when a configured test runner is present.
- Task Portal: `npm run build` from `easy-bpm-task-portal`, plus relevant tests when a configured test runner is present.

Run the smallest relevant checks during iteration and the complete affected-module checks before handoff. Report any check that could not run and why.
