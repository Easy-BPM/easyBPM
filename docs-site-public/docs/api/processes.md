---
title: Processes API
---

# Processes API

Use the Processes API to deploy definitions, start instances, manage variables, operate runtime state, and correlate messages.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/processes` | Get latest process definitions |
| `POST` | `/processes` | Deploy a process definition |
| `POST` | `/processes/{processId}/start` | Start a process instance |
| `GET` | `/processes/definitions/{id}` | Get process definition by ID |
| `GET` | `/processes/instances` | Get process instances |
| `GET` | `/processes/instances/{id}` | Get process instance by ID |
| `GET` | `/processes/instances/{id}/timeline` | Get process instance timeline |
| `DELETE` | `/processes/instances/{id}` | Delete process instance |
| `GET` | `/processes/instances/{id}/children` | Get child process instances |
| `POST` | `/processes/instances/{id}/move-node` | Move process token |
| `GET` | `/processes/instances/{id}/parent` | Get parent process instance |
| `POST` | `/processes/instances/{id}/stop` | Stop process instance |
| `GET` | `/processes/instances/{id}/variables` | Get process variables |
| `PUT` | `/processes/instances/{id}/variables` | Assign process variables |
| `GET` | `/processes/instances/{parentId}/children/{childId}/mapping` | Get call activity mapping |
| `POST` | `/processes/messages` | Send a message |

<a id="get-processes"></a>
## GET /processes

**Get latest process definitions**

Retrieve the latest versions of all process definitions

| Property | Value |
| --- | --- |
| Operation ID | `getLatestProcesses` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [PageProcessDefinition](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `pageable` | query | Yes | Pageable |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [PageProcessDefinition](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "content": [
    {
      "id": 10,
      "key": "expense-approval",
      "processName": "Expense Approval",
      "description": "Review and approve expense requests.",
      "version": 3,
      "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    }
  }
}
```

<a id="post-processes"></a>
## POST /processes

**Deploy a process definition**

Upload and deploy a new BPMN process definition

| Property | Value |
| --- | --- |
| Operation ID | `deploy` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | `Easy BPM process JSON` |
| Request content type | `application/json` |
| Response DTO | [ProcessDefinition](./schemas) |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | `Easy BPM process JSON` |

Example request body:

```json
{
  "processId": "expense-approval",
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "variables": [
    {
      "name": "approved",
      "initialValue": false
    },
    {
      "name": "amount",
      "initialValue": 0
    }
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
        "formId": "expenseReview"
      }
    },
    {
      "id": "end",
      "name": "End",
      "type": "EndEvent"
    }
  ],
  "flows": [
    {
      "source": "start",
      "target": "manager-review"
    },
    {
      "source": "manager-review",
      "target": "end"
    }
  ]
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/processes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "processId": "expense-approval",
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "variables": [
    {
      "name": "approved",
      "initialValue": false
    },
    {
      "name": "amount",
      "initialValue": 0
    }
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
        "formId": "expenseReview"
      }
    },
    {
      "id": "end",
      "name": "End",
      "type": "EndEvent"
    }
  ],
  "flows": [
    {
      "source": "start",
      "target": "manager-review"
    },
    {
      "source": "manager-review",
      "target": "end"
    }
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessDefinition](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 10,
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "version": 3,
  "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
}
```

<a id="post-processes-processid-start"></a>
## POST /processes/\{processId\}/start

**Start a process instance**

Create and start a new instance of a process definition by processId

| Property | Value |
| --- | --- |
| Operation ID | `startInstance` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `processId` | path | Yes | string |  |

### Example request

```bash
curl -X POST "http://localhost:8080/processes/expense-approval/start" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas) |

When an instance reaches `FAILED`, the response includes `errorMessage` and `errorNodeId`. Easy BPM also records this state when an API task has no attached error boundary and the worker does not complete it within 2 minutes.

### Example response

Status: `200 OK`

```json
{
  "id": 456,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "FAILED",
  "currentNode": [],
  "nodeHistory": [
    "start",
    "manager-review",
    "create-ticket"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:02:31",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null,
  "errorMessage": "API task 'create-ticket' timed out after 2 minutes without completion",
  "errorNodeId": "create-ticket"
}
```

<a id="get-processes-definitions-id"></a>
## GET /processes/definitions/\{id\}

**Get process definition by ID**

Retrieve a specific deployed process definition version by its ID

| Property | Value |
| --- | --- |
| Operation ID | `getProcessDefinitionById` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessDefinition](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/definitions/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessDefinition](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 10,
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "version": 3,
  "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
}
```

<a id="get-processes-instances"></a>
## GET /processes/instances

**Get process instances**

Retrieve all process instances with pagination

| Property | Value |
| --- | --- |
| Operation ID | `getProcessInstances` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [PageProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `pageable` | query | Yes | Pageable |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [PageProcessInstance](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "content": [
    {
      "id": 456,
      "processDefinition": {
        "id": 10,
        "key": "expense-approval",
        "processName": "Expense Approval",
        "description": "Review and approve expense requests.",
        "version": 3,
        "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
      },
      "status": "ACTIVE",
      "currentNode": [
        "manager-review"
      ],
      "nodeHistory": [
        "start",
        "manager-review"
      ],
      "createdAt": "2026-06-17T10:00:00",
      "updatedAt": "2026-06-17T10:00:01",
      "parentInstanceId": null,
      "callActivityNodeId": null,
      "nestingLevel": 0,
      "completionNodeId": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    }
  }
}
```

<a id="get-processes-instances-id"></a>
## GET /processes/instances/\{id\}

**Get process instance by ID**

Retrieve a specific process instance by its ID

| Property | Value |
| --- | --- |
| Operation ID | `getProcessInstanceById` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 456,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "ACTIVE",
  "currentNode": [
    "manager-review"
  ],
  "nodeHistory": [
    "start",
    "manager-review"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:01",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null
}
```

<a id="get-processes-instances-id-timeline"></a>
## GET /processes/instances/\{id\}/timeline

**Get process instance timeline**

Retrieve chronological runtime events for a process instance.

| Property | Value |
| --- | --- |
| Operation ID | `getProcessInstanceTimeline` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstanceEvent](./schemas)[] |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/456/timeline" \
  -H "Authorization: Bearer $TOKEN"
```

### Example response

Status: `200 OK`

```json
[
  {
    "id": 1,
    "processInstanceId": 456,
    "nodeId": null,
    "eventType": "PROCESS_STARTED",
    "message": "Process instance started.",
    "actor": null,
    "details": null,
    "createdAt": "2026-06-23T10:00:00"
  },
  {
    "id": 2,
    "processInstanceId": 456,
    "nodeId": "manager-review",
    "eventType": "TASK_CREATED",
    "message": "Task 'Manager Review' created.",
    "actor": null,
    "details": "taskId=123",
    "createdAt": "2026-06-23T10:00:01"
  }
]
```

<a id="delete-processes-instances-id"></a>
## DELETE /processes/instances/\{id\}

**Delete process instance**

Hard delete a process instance and related runtime data

| Property | Value |
| --- | --- |
| Operation ID | `deleteProcessInstance` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `No body` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X DELETE "http://localhost:8080/processes/instances/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | - |

### Example response

Status: `204 No Content`

_No response body._

<a id="get-processes-instances-id-children"></a>
## GET /processes/instances/\{id\}/children

**Get child process instances**

Retrieve all subprocess instances spawned by the given parent instance

| Property | Value |
| --- | --- |
| Operation ID | `getChildInstances` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstance](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/123/children" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": 101,
    "processDefinition": {
      "id": 10,
      "key": "expense-approval",
      "processName": "Expense Approval",
      "description": "Review and approve expense requests.",
      "version": 3,
      "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
    },
    "status": "ACTIVE",
    "currentNode": [
      "manager-review"
    ],
    "nodeHistory": [
      "start",
      "manager-review"
    ],
    "createdAt": "2026-06-17T10:00:00",
    "updatedAt": "2026-06-17T10:00:01",
    "parentInstanceId": 100,
    "callActivityNodeId": "run-kyc",
    "nestingLevel": 1,
    "completionNodeId": null
  }
]
```

<a id="post-processes-instances-id-move-node"></a>
## POST /processes/instances/\{id\}/move-node

**Move process token**

Manually move process execution from one node to another

| Property | Value |
| --- | --- |
| Operation ID | `moveProcessNode` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [MoveNodeRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [ProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [MoveNodeRequest](./schemas) |

Example request body:

```json
{
  "fromNode": "manual-review",
  "toNode": "approve-request",
  "reason": "SLA escalation approved by supervisor"
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/processes/instances/123/move-node" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "fromNode": "manual-review",
  "toNode": "approve-request",
  "reason": "SLA escalation approved by supervisor"
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 456,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "ACTIVE",
  "currentNode": [
    "approve-request"
  ],
  "nodeHistory": [
    "start",
    "manual-review",
    "approve-request"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:01",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null
}
```

<a id="get-processes-instances-id-parent"></a>
## GET /processes/instances/\{id\}/parent

**Get parent process instance**

Retrieve the parent instance for a subprocess instance

| Property | Value |
| --- | --- |
| Operation ID | `getParentInstance` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/123/parent" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 100,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "ACTIVE",
  "currentNode": [
    "run-kyc"
  ],
  "nodeHistory": [
    "start",
    "manager-review"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:01",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null
}
```

<a id="post-processes-instances-id-stop"></a>
## POST /processes/instances/\{id\}/stop

**Stop process instance**

Cancel an active process instance and stop further execution

| Property | Value |
| --- | --- |
| Operation ID | `stopProcessInstance` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessInstance](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X POST "http://localhost:8080/processes/instances/123/stop" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessInstance](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 456,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "CANCELLED",
  "currentNode": [],
  "nodeHistory": [
    "start",
    "manager-review"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:01",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null
}
```

<a id="get-processes-instances-id-variables"></a>
## GET /processes/instances/\{id\}/variables

**Get process variables**

Retrieve all variables for a process instance

| Property | Value |
| --- | --- |
| Operation ID | `getProcessVariables` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ProcessVariable](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/123/variables" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessVariable](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": 88,
    "processInstanceId": 456,
    "name": "approved",
    "value": true,
    "createdAt": "2026-06-17T10:00:00",
    "updatedAt": "2026-06-17T10:00:00"
  },
  {
    "id": 89,
    "processInstanceId": 456,
    "name": "amount",
    "value": 1250.75,
    "createdAt": "2026-06-17T10:00:00",
    "updatedAt": "2026-06-17T10:00:00"
  }
]
```

<a id="put-processes-instances-id-variables"></a>
## PUT /processes/instances/\{id\}/variables

**Assign process variables**

Create or update process variables for a process instance

| Property | Value |
| --- | --- |
| Operation ID | `assignProcessVariables` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [AssignProcessVariablesRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [ProcessVariable](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [AssignProcessVariablesRequest](./schemas) |

Example request body:

```json
{
  "variables": {
    "approved": true,
    "amount": 1250.75,
    "currency": "USD",
    "requester": {
      "id": 42,
      "name": "Alice"
    }
  }
}
```

### Example request

```bash
curl -X PUT "http://localhost:8080/processes/instances/123/variables" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "variables": {
    "approved": true,
    "amount": 1250.75,
    "currency": "USD",
    "requester": {
      "id": 42,
      "name": "Alice"
    }
  }
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ProcessVariable](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": 88,
    "processInstanceId": 456,
    "name": "approved",
    "value": true,
    "createdAt": "2026-06-17T10:00:00",
    "updatedAt": "2026-06-17T10:00:00"
  },
  {
    "id": 89,
    "processInstanceId": 456,
    "name": "amount",
    "value": 1250.75,
    "createdAt": "2026-06-17T10:00:00",
    "updatedAt": "2026-06-17T10:00:00"
  }
]
```

<a id="get-processes-instances-parentid-children-childid-mapping"></a>
## GET /processes/instances/\{parentId\}/children/\{childId\}/mapping

**Get call activity mapping**

Retrieve input/output variable mapping for a parent-child call activity relationship

| Property | Value |
| --- | --- |
| Operation ID | `getCallActivityMapping` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [CallActivityMappingResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `parentId` | path | Yes | integer(int64) |  |
| `childId` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/processes/instances/100/children/101/mapping" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [CallActivityMappingResponse](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 9,
  "parentInstanceId": 100,
  "childInstanceId": 101,
  "callActivityNodeId": "run-kyc",
  "inputMappings": {
    "customerId": "customerId"
  },
  "outputMappings": {
    "kycStatus": "kycStatus"
  },
  "propagateAllVariables": false
}
```

<a id="post-processes-messages"></a>
## POST /processes/messages

**Send a message**

Send a message to trigger message-based events in running process instances

| Property | Value |
| --- | --- |
| Operation ID | `sendMessage` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | `Message correlation payload` |
| Request content type | `application/json` |
| Response DTO | `Message correlation response` |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | `Message correlation payload` |

Example request body:

```json
{
  "messageName": "invoice-received",
  "correlationKey": "ORDER-12345",
  "variables": {
    "invoiceId": "INV-7788",
    "amount": 540,
    "receivedAt": "2026-06-17T10:00:00Z"
  }
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/processes/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "messageName": "invoice-received",
  "correlationKey": "ORDER-12345",
  "variables": {
    "invoiceId": "INV-7788",
    "amount": 540,
    "receivedAt": "2026-06-17T10:00:00Z"
  }
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | `object` |

### Example response

Status: `200 OK`

```json
{
  "status": "success",
  "message": "Message received and process resumed",
  "messageName": "invoice-received",
  "correlationKey": "ORDER-12345"
}
```
