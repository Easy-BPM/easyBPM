---
title: Tasks API
---

# Tasks API

Use the Tasks API to list visible work, claim shared tasks, inspect task context, and complete user tasks.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/tasks` | Get all tasks |
| `GET` | `/tasks/{id}` | Get task by ID |
| `POST` | `/tasks/{id}/claim` | Claim a task |
| `POST` | `/tasks/{id}/complete` | Complete a task |
| `GET` | `/tasks/search` | Search tasks |

<a id="get-tasks"></a>
## GET /tasks

**Get all tasks**

Retrieve all tasks with pagination

| Property | Value |
| --- | --- |
| Operation ID | `getTasks` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [PageTaskResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `pageable` | query | Yes | Pageable |  |

### Example request

```bash
curl -X GET "http://localhost:8080/tasks?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [PageTaskResponseDto](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "content": [
    {
      "id": 123,
      "title": "Manager Review",
      "name": "Manager Review",
      "description": "Review the expense request.",
      "processInstanceId": 456,
      "nodeId": "manager-review",
      "assignee": "manager",
      "candidateUsers": [],
      "candidateGroups": [
        "FINANCE"
      ],
      "status": "PENDING",
      "createdAt": "2026-06-17T09:30:00",
      "completedAt": null,
      "formDbId": 12,
      "formId": "expenseReview",
      "variables": {
        "amount": 1250.75,
        "requester": "Alice"
      }
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

<a id="get-tasks-id"></a>
## GET /tasks/\{id\}

**Get task by ID**

Retrieve a specific task by its ID

| Property | Value |
| --- | --- |
| Operation ID | `getTaskById` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [TaskResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/tasks/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [TaskResponseDto](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 123,
  "title": "Manager Review",
  "name": "Manager Review",
  "description": "Review the expense request.",
  "processInstanceId": 456,
  "nodeId": "manager-review",
  "assignee": "manager",
  "candidateUsers": [],
  "candidateGroups": [
    "FINANCE"
  ],
  "status": "PENDING",
  "createdAt": "2026-06-17T09:30:00",
  "completedAt": null,
  "formDbId": 12,
  "formId": "expenseReview",
  "variables": {
    "amount": 1250.75,
    "requester": "Alice"
  }
}
```

<a id="post-tasks-id-claim"></a>
## POST /tasks/\{id\}/claim

**Claim a task**

Claim a shared/group task for the current authenticated user

| Property | Value |
| --- | --- |
| Operation ID | `claimTask` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [TaskResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X POST "http://localhost:8080/tasks/123/claim" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [TaskResponseDto](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 123,
  "title": "Manager Review",
  "name": "Manager Review",
  "description": "Review the expense request.",
  "processInstanceId": 456,
  "nodeId": "manager-review",
  "assignee": "admin",
  "candidateUsers": [],
  "candidateGroups": [
    "FINANCE"
  ],
  "status": "PENDING",
  "createdAt": "2026-06-17T09:30:00",
  "completedAt": null,
  "formDbId": 12,
  "formId": "expenseReview",
  "variables": {
    "amount": 1250.75,
    "requester": "Alice"
  }
}
```

<a id="post-tasks-id-complete"></a>
## POST /tasks/\{id\}/complete

**Complete a task**

Mark a task as completed and provide task variables

| Property | Value |
| --- | --- |
| Operation ID | `completeTask` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | `Task completion payload` |
| Request content type | `application/json` |
| Response DTO | `string` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | `Task completion payload` |

Example request body:

```json
{
  "variables": {
    "approved": true,
    "comment": "Approved for payment",
    "reviewedAt": "2026-06-17T10:00:00Z"
  }
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/tasks/123/complete" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "variables": {
    "approved": true,
    "comment": "Approved for payment",
    "reviewedAt": "2026-06-17T10:00:00Z"
  }
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | `string` |

### Example response

Status: `200 OK`

```text
Task completed successfully
```

<a id="get-tasks-search"></a>
## GET /tasks/search

**Search tasks**

Search tasks by assignee and/or status with pagination

| Property | Value |
| --- | --- |
| Operation ID | `searchTasks` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [PageTaskResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `assignee` | query | No | string |  |
| `status` | query | No | string |  |
| `pageable` | query | Yes | Pageable |  |

### Example request

```bash
curl -X GET "http://localhost:8080/tasks/search?assignee=manager&status=PENDING&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [PageTaskResponseDto](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "content": [
    {
      "id": 123,
      "title": "Manager Review",
      "name": "Manager Review",
      "description": "Review the expense request.",
      "processInstanceId": 456,
      "nodeId": "manager-review",
      "assignee": "manager",
      "candidateUsers": [],
      "candidateGroups": [
        "FINANCE"
      ],
      "status": "PENDING",
      "createdAt": "2026-06-17T09:30:00",
      "completedAt": null,
      "formDbId": 12,
      "formId": "expenseReview",
      "variables": {
        "amount": 1250.75,
        "requester": "Alice"
      }
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
