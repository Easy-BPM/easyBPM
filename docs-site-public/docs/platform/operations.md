---
title: Operations
---

# Operations

Easy BPM includes operational tools for investigating process execution, recovering failed work, and cleaning old runtime data.

## Instance timeline

The Admin Console Instance Explorer shows a chronological timeline for each process instance. The timeline is different from `nodeHistory`:

| View | Purpose |
| --- | --- |
| Node history | Shows the path of process nodes visited by the instance. |
| Process timeline | Shows timestamped runtime events such as task creation, task completion, worker requests, incidents, retries, and manual moves. |

Timeline events are useful for support investigations because they show both process movement and operational actions.

Common event types include:

| Event type | Meaning |
| --- | --- |
| `PROCESS_STARTED` | Instance was created and started. |
| `NODE_ENTERED` | Execution entered a node. |
| `TASK_CREATED` | A user task was created. |
| `TASK_CLAIMED` | A user claimed a task. |
| `TASK_COMPLETED` | A user completed a task. |
| `WORKER_REQUESTED` | An API or external service task was sent to the worker. |
| `WORKER_COMPLETED` | Worker completed the external task. |
| `WORKER_FAILED` | Worker task failed or timed out. |
| `GATEWAY_EVALUATED` | A gateway routed execution to one or more next nodes. |
| `INCIDENT_CREATED` | An operational incident was created. |
| `INCIDENT_RETRY_REQUESTED` | An operator requested a retry from Incident Manager. |
| `INCIDENT_RESOLVED` | An incident was marked resolved. |
| `MANUAL_MOVE` | An operator moved the process token. |
| `PROCESS_COMPLETED` | Instance completed. |
| `PROCESS_FAILED` | Instance failed. |
| `PROCESS_CANCELLED` | Instance was cancelled. |

## Incident management

Incidents are operational records created when Easy BPM cannot continue work normally or when a task-specific failure needs operator attention.

Incident sources are intentionally broad:

| Source | Typical cause |
| --- | --- |
| `PROCESS_ENGINE` | Unhandled runtime error, gateway error, or process failure. |
| `WORKER` | API/service task timeout, DLQ, or external worker failure. |
| `CODE_TASK` | JAR-based code task execution failure. |
| `AI_TASK` | AI provider/configuration/retry failure. |
| `MESSAGE` | Message-related runtime failure. |

Operators can:

- filter incidents by status, source, and instance
- acknowledge an incident
- retry recoverable worker/API incidents
- resolve incidents with a resolution action
- reopen incidents
- open the related process instance
- inspect the incident timeline

Resolution actions include:

| Action | Use when |
| --- | --- |
| `RESOLVED_MANUALLY` | Operator handled the issue outside the system. |
| `VARIABLE_FIXED` | Process variables were corrected before recovery. |
| `RETRIED_SUCCESSFULLY` | A retry fixed the incident. |
| `IGNORED_KNOWN_ISSUE` | Issue is accepted or tracked elsewhere. |
| `INSTANCE_CANCELLED` | Process instance was cancelled as the resolution. |

## Purge and archiving

The Admin Console Maintenance page provides destructive cleanup operations for environments with data-retention policies.

Available operations:

| Operation | What it deletes |
| --- | --- |
| Purge completed instances | Completed process instances older than a selected date, optionally filtered by process definition. |
| Delete process definition | A process definition and all runtime data related to that definition. |

Both operations support a preview before execution.

The cleanup removes related runtime records, including:

- process instances
- tasks
- process variables
- task variables
- documents
- message subscriptions
- worker requests
- code-task execution audits
- incidents and incident events
- process timeline events
- call-activity mappings

Use these operations carefully. They are hard deletes, not soft archive records.

## Recommended support workflow

1. Search the process instance in Admin.
2. Review the process timeline and current variables.
3. If an incident exists, open it from Incident Manager.
4. Fix variables or external system state if needed.
5. Retry worker/API incidents where applicable.
6. Resolve the incident with a resolution action.
7. Use purge tools only after the customer retention window has passed.
