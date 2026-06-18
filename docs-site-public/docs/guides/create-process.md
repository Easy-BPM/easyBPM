---
title: Create a Process
---

# Create a Process

An Easy BPM process is deployed as an Easy BPM process JSON graph. You can create it visually in the Modeler or send JSON directly to the `/processes` API.

## Basic process shape

Every process definition needs:

| Field | Description |
| --- | --- |
| `processId` | Stable identifier used to start the latest version. |
| `key` | Business key for version lookup. If omitted, `processId` is used. |
| `processName` or `name` | Display name. |
| `description` | Optional customer-facing description. |
| `variables` | Optional process variables initialized when an instance starts. |
| `nodes` | Workflow nodes. |
| `flows` | Connections between nodes. |

## Minimal approval process

```json
{
  "processId": "expense-approval",
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "variables": [
    { "name": "approved", "initialValue": false },
    { "name": "amount", "initialValue": 0 }
  ],
  "nodes": [
    {
      "id": "start",
      "name": "Start",
      "type": "StartEvent"
    },
    {
      "id": "manager-review",
      "name": "Manager Review",
      "type": "HumanTask",
      "config": {
        "assignee": "manager",
        "formId": "expenseReview",
        "inputs": [
          { "targetName": "amount", "source": "variable", "value": "amount" }
        ],
        "outputs": [
          { "target": "process", "sourceName": "approved", "value": "approved" },
          { "target": "process", "sourceName": "comment", "value": "managerComment" }
        ]
      }
    },
    {
      "id": "end",
      "name": "End",
      "type": "EndEvent"
    }
  ],
  "flows": [
    { "source": "start", "target": "manager-review" },
    { "source": "manager-review", "target": "end" }
  ]
}
```

## Deploy and start

```bash
curl -X POST http://localhost:8080/processes \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d @expense-approval.json
```

Start the latest deployed version by process key:

```bash
curl -X POST http://localhost:8080/processes/expense-approval/start \
  -H "Authorization: Bearer $TOKEN"
```

## Supported node types

| Type | Use for |
| --- | --- |
| `StartEvent` | Entry point for a process. |
| `EndEvent` | Completion point. |
| `HumanTask` | User work shown in the Task Portal. |
| `APITask` | HTTP/API work published to the async worker. |
| `ServiceTask` | Service work. |
| `ScriptTask` | Inline script-style variable work. |
| `CodeTask` | JVM method execution from an uploaded JAR. |
| `AiTask` | AI provider execution using stored credentials or environment references. |
| `ExclusiveGateway` | Choose one path based on conditions. |
| `ParallelGateway` | Fork or synchronize parallel paths. |
| `TimerEvent` | Wait for a configured duration. |
| `MessageEvent` | Message start or message-style wait using properties. |
| `MessageIntermediateCatchEvent` | Wait for an external message correlation. |
| `MessageIntermediateThrowEvent` | Publish or emit a message payload. |
| `ErrorBoundaryEvent` | Catch failures from an attached task or call activity. |
| `CallActivity` | Start another deployed process as a subprocess. |

## Best practices

Use stable IDs such as `manager-review`, not generated labels, because IDs are referenced by flows, admin token movement, audits, and integrations.

Keep process variables small and business-oriented. Store files through document endpoints and keep only document IDs or metadata in variables.

Use the Modeler for day-to-day process creation. Use direct JSON deployment for CI/CD, generated process definitions, or controlled migration workflows.
