# Easy BPM Quality Evaluation

Date: 2026-08-07

This evaluation refactors the previous QA package against the current repository state. It covers the React modeler, form modeler, task portal form runtime, backend BPM engine, worker path, code tasks, and agentic orchestration.

## Current Product Surface

| Area | Implemented surface | Primary paths |
| --- | --- | --- |
| Process modeler | BPM graph creation, import/export, deployment, validation, properties, variables, forms attachment, code-task helpers, agent-process-call nodes | `easy-bpm-modeler/` |
| Forms | Form builder with tabs, field palette, preview, schema generation, import/export, deployment | `easy-bpm-modeler/components/FormModeler.tsx` |
| Task portal | Start processes, claim/unclaim, draft, complete tasks, render dynamic forms, document fields | `easy-bpm-task-portal/` |
| Runtime engine | Deployment, versioning, start/resume, variables, gateways, human tasks, API/service tasks, messages, timers, call activity, AI tasks, agent process calls | `src/main/kotlin/com/easy/bpm/service/process/` |
| Agentic orchestration | Agent process definition deploy/list/version, modeler board, provider-backed or planned execution, output mappings, timeline events | `AgentBoardModeler.tsx`, `AgentProcessService.kt`, `AgentProcessCallHandler.kt` |
| Code tasks | JAR upload/discovery/controller support, handler execution tests, admin audit UI | `CodeTask*`, `CodeTaskHandler.kt`, `easy-bpm-admin/` |

## Key Findings

1. `MessageStartEvent` is no longer a modeler-only risk. It is present in `NodeType.kt`, validated in `ProcessDefinitionValidator.kt`, and covered in integration/service tests. The old QA notes were stale and have been updated.
2. CodeTask remains a partial end-to-end feature. The backend type and handler exist, and there are repository/handler/controller/admin tests, but `ProcessExecutionEngine` currently treats `CodeTask` as a no-op because it is not routed in the `when` branch. QA should keep component-level CodeTask tests, but fail full runtime CodeTask as a known gap until engine routing is implemented.
3. Agent Process calls are executable in the BPM runtime. If the agent definition includes provider config, the backend calls the selected AI provider; otherwise it records a planned/auditable execution. QA should run both modes.
4. Form modeling and task rendering are tightly coupled through generated JSON schema. QA must test both modeler deploy format and portal runtime rendering, especially file upload/download/PDF viewer fields.
5. Frontend automated coverage is uneven. Admin has many component tests. Modeler and task portal rely mostly on type checking and manual/E2E process execution.

## Release Gates

| Gate | Command or action | Pass criteria |
| --- | --- | --- |
| Backend unit/integration | `./gradlew test` | All non-excluded JUnit/Kotest tests pass |
| Worker compile/test | `./gradlew :worker:test` if worker tests exist, otherwise `./gradlew :worker:classes` | Worker builds and listener dependencies resolve |
| Modeler type check | `cd easy-bpm-modeler && npm run lint` | TypeScript passes |
| Task portal type check | `cd easy-bpm-task-portal && npm run lint` | TypeScript passes |
| Admin tests | `cd easy-bpm-admin && npm test -- --runInBand` or project test script | Component tests pass |
| Docker smoke | `docker compose up -d postgres rabbitmq backend worker` | Backend, worker, DB, RabbitMQ healthy |
| Manual modeler smoke | Import each QA process and export again | No data loss in nodes, flows, variables, key configs |
| Runtime smoke | Deploy/start supported QA processes | Expected tasks, variables, subscriptions, events, and completions |

## QA Asset Inventory

| Asset | Purpose | Deploy gate |
| --- | --- | --- |
| `forms-backend/qa-intake-form.json` | Backend form schema for intake user task | Yes |
| `forms-backend/qa-manager-review-form.json` | Backend form schema for manager review | Yes |
| `forms-backend/qa-finance-review-form.json` | Backend form schema for finance review | Yes |
| `forms-modeler/*.modeler.json` | Importable editable forms for Form Modeler | Modeler only plus deploy |
| `processes/qa-user-task-forms.json` | Human tasks, form ids, task variable mappings, exclusive gateway | Yes |
| `processes/qa-gateway-routing.json` | Exclusive and parallel gateway routing | Yes |
| `processes/qa-api-components.json` | API task auth, request bodies, output mappings | Yes with mock APIs/credentials |
| `processes/qa-message-timer-events.json` | Message start/catch/throw, payload mapping, timer | Yes |
| `processes/qa-code-task-component.json` | Code task contract, JAR/class/method mapping, boundary path | Component only until runtime route exists |
| `agent-processes/customer-support-resolution.agent-process.json` | Agent process deploy/runtime fixture | Yes |
| `processes/qa-agent-process-call.json` | BPM call into agent process plus human review | Yes |
| `jars/test-service.jar` | CodeTask upload/discovery/execution fixture | Component only |

## Process Modeler Test Process

1. Sign in to the modeler and confirm session survives refresh.
2. Create a new process and add StartEvent, HumanTask, APITask, AiTask, AgentProcessCall, CallActivity, ExclusiveGateway, ParallelGateway, MessageStartEvent, MessageIntermediateCatchEvent, MessageIntermediateThrowEvent, TimerEvent, ErrorBoundaryEvent, and EndEvent.
3. Rename each node id and verify invalid ids, duplicates, blank ids, and invalid process variable names are blocked or surfaced in validation.
4. Connect every node with sequence flows, edit labels/conditions, move nodes, pan/zoom, and export JSON.
5. Import the exported JSON and compare node ids, types, positions, sizes, variables, flows, conditions, and node configs.
6. Deploy the supported process variants and verify backend response, version increment, and process list visibility in the task portal/admin.

Acceptance:

- No node loses config across import/export.
- Deployed JSON uses backend node type names (`HumanTask`, `APITask`, `AgentProcessCall`, etc.).
- Validation blocks structurally unsafe definitions before deploy.
- Unsupported or partial features are visibly treated as QA notes, not silent pass conditions.

## Forms Test Process

1. Import each file under `forms-modeler/`.
2. For each form, edit name/id, add a tab, reorder fields, toggle required/read-only, and add every field type: short text, long text, number, checkbox, radio, dropdown, date, file upload, file download, PDF viewer.
3. Open preview and verify read-only/required/options are reflected.
4. Open schema modal and verify generated schema contains `formId`, `name`, `schema.properties`, `required`, `enum`, `format`, `allowedExtensions`, and `maxSizeMb`.
5. Deploy the form and fetch it through backend `GET /forms/{id}`.
6. Attach the deployed form to a HumanTask, start the process, open the task portal, fill fields, save draft, refresh, complete task, and inspect persisted task/process variables.

Acceptance:

- Duplicate field variable names are rejected.
- Invalid form ids are rejected.
- Field values round-trip through draft and completion.
- File/PDF fields use document endpoints and do not break task completion when optional.

## Agentic Orchestration Test Process

1. In the Agent Process board, create a definition with process name, goal, instructions, constraints, tools, participants, provider config, prompt template, dynamic task toggle, timeout, and steps across all board statuses.
2. Export, re-import, and confirm all fields survive normalization.
3. Deploy `agent-processes/customer-support-resolution.agent-process.json`.
4. Deploy `processes/qa-agent-process-call.json`.
5. Start the BPM process, complete `capture-complaint`, and verify `AgentProcessCall` creates an `agent_process_execution` row.
6. Verify process variables are written: `<nodeId>_agentExecutionId`, `<nodeId>_agentDecision`, `agentDecision`, and, when provider-backed, `agentResponseText`.
7. Verify timeline events include agent start and completion.
8. Run two modes:
   - Planned mode: remove provider config from the agent definition and expect `AGENT_PROCESS_PLANNED`.
   - Provider mode: configure an available provider such as Ollama/OpenAI/Gemini and expect provider response fields.

Acceptance:

- Missing agent process key fails deployment or runtime with a clear error.
- Provider failure creates auditable failure behavior and does not corrupt variables.
- Output mappings with missing paths write JSON null rather than crashing unexpectedly.

## Runtime Process Scenarios

| Scenario | Fixture | Checks |
| --- | --- | --- |
| Human task and forms | `qa-user-task-forms.json` | Task creation, assignment/candidate group, form id, inputs/outputs, exclusive approval route |
| Gateways | `qa-gateway-routing.json` | Conditional route evaluation, parallel fork/join state, final completion |
| API tasks | `qa-api-components.json` | Auth variants, request body, worker callback, retries/DLQ for failures, output mapping |
| Messages/timers | `qa-message-timer-events.json` | Message-start start path, catch subscription, correlation key, payload mapping, timer resume/timeout, throw event inbox |
| Call activity | `src/test/resources/process-call-activity*.json` | Child creation, variable mapping, parent resume, nesting limit |
| AI task | `src/test/resources/process-comprehensive.json` or dedicated AI tests | Provider config, credential ref, prompt rendering, output variable, failure/incident path |
| Code task | `qa-code-task-component.json` | JAR upload/discovery/handler/admin audit only; full process execution is known gap |

## Negative Tests

- Deploy process with missing `processId`, missing nodes/flows, duplicate node ids, unknown node type, dangling flow target, no start node, multiple conflicting starts.
- Deploy APITask with empty URL, invalid auth type, blank auth ref, invalid API key location.
- Deploy AgentProcessCall with missing or blank `agentProcessKey`, non-array `inputs`, non-array `outputs`.
- Start process with missing form id, missing called subprocess, missing agent definition, unavailable AI provider, unavailable external API.
- Complete task with invalid types, missing required form fields, extra unknown variables, stale task status, unauthorized user, unclaimed task when claim is required.
- Correlate message with wrong name, wrong key, duplicate messages, expired subscription, payload missing mapped values.

## Observability Checklist

- `process_instance.status`, `current_nodes`, `error_message`, and `failed_node_id`.
- `process_instance_event` timeline for node transitions, gateways, messages, timers, agent calls, failures.
- `process_variable` and `task_variable` JSONB values and type stability.
- `message_subscription` and `message_event_inbox` statuses.
- `worker_request` status, retries, timeout, DLQ behavior.
- `incident` and `incident_event` creation for unhandled runtime failures.
- `code_task_execution_audit` for CodeTask component tests.
- Actuator health for DB/Rabbit/backend/worker.

## Exit Criteria For Quality Phase

1. All release gates pass.
2. Every QA asset is classified as pass, fail, skipped, or known gap with evidence.
3. Full runtime pass for user tasks/forms, gateways, API task worker path, message/timer events, call activity, AI task, and agent process call.
4. CodeTask is either implemented in `ProcessExecutionEngine` and promoted to runtime gate, or formally tracked as a release limitation.
5. All P0/P1 defects are closed or explicitly accepted.
6. Manual execution evidence includes process definition ids, instance ids, task ids, screenshots or exported logs, and database/event observations.
