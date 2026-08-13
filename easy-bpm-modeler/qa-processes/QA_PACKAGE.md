# QA Package

This folder is the executable QA package for Easy BPM Modeler, Forms, Runtime, and Agentic Orchestration.

## Load Order

1. Deploy forms from `forms-backend/`.
2. Deploy agent processes from `agent-processes/`.
3. Deploy child processes before parent processes:
   - `processes/qa-call-activity-child.json`
   - `processes/qa-call-activity-parent.json`
4. Deploy independent BPM process fixtures from `processes/`.
5. Upload `jars/test-service.jar` before CodeTask component testing.

## Agents

| File | Key | Mode | Use |
| --- | --- | --- | --- |
| `agent-processes/customer-support-resolution.agent-process.json` | `customer-support-resolution` | Provider-backed by default | Agent Process board, provider config, dynamic steps, `AgentProcessCall` with AI provider |
| `agent-processes/planned-triage.agent-process.json` | `planned-triage-agent` | Provider-free planned mode | Agent Process runtime without external AI dependency |

## Forms

| Backend file | Modeler file | Form id | Main coverage |
| --- | --- | --- | --- |
| `forms-backend/qa-intake-form.json` | `forms-modeler/qa-intake-form.modeler.json` | `qaIntakeForm` | Intake fields, document upload |
| `forms-backend/qa-manager-review-form.json` | `forms-modeler/qa-manager-review-form.modeler.json` | `qaManagerReview` | Approval, comments, manager review |
| `forms-backend/qa-finance-review-form.json` | `forms-modeler/qa-finance-review-form.modeler.json` | `qaFinanceReview` | Finance review, payment method, PDF/document fields |

## BPM Processes

| File | Process id | Deploy gate | Coverage |
| --- | --- | --- | --- |
| `qa-user-task-forms.json` | `qa_user_task_forms` | Yes | User tasks, forms, draft/complete, exclusive approval route |
| `qa-gateway-routing.json` | `qa_gateway_routing` | Yes | Exclusive gateway, parallel fork/join |
| `qa-api-components.json` | `qa_api_components` | Yes with reachable API/credentials | API task auth variants, request body, output mapping |
| `qa-message-timer-events.json` | `qa_message_timer_events` | Yes | Message start, catch, throw, payload mapping, timer |
| `qa-agent-process-call.json` | `qa_agent_process_call` | Yes | Provider-backed AgentProcessCall, output mappings, human review |
| `qa-agent-planned-call.json` | `qa_agent_planned_call` | Yes | Provider-free AgentProcessCall planned execution |
| `qa-ai-task-runtime.json` | `qa_ai_task_runtime` | Yes with provider available | AiTask prompt substitution and output variable |
| `qa-call-activity-child.json` | `qa_call_activity_child` | Yes, deploy before parent | Child subprocess task/output mapping |
| `qa-call-activity-parent.json` | `qa_call_activity_parent` | Yes | CallActivity parent/child lifecycle and parent resume |
| `qa-code-task-component.json` | `qa_code_task_component` | Component only | CodeTask config, JAR/class/method mapping, known runtime gap |

## Minimal Full Regression

Run these when time is short:

1. `qa-user-task-forms`
2. `qa-gateway-routing`
3. `qa-message-timer-events`
4. `qa-agent-planned-call`
5. `qa-call-activity-parent`

Run these when dependencies are available:

1. `qa-api-components`
2. `qa-agent-process-call`
3. `qa-ai-task-runtime`
4. `qa-code-task-component` as component-only verification

## Known Runtime Gap

`CodeTask` is recognized as a node type and has handler/admin/component coverage, but full BPM runtime execution is not a release gate until `ProcessExecutionEngine` routes `CodeTask` nodes to `CodeTaskHandler`.
