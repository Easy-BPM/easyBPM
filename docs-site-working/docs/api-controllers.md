# API Reference

Complete REST API documentation for Easy BPM Engine.

## Base URL

```
http://localhost:8080
```

## Authentication

Currently, all endpoints are public (no authentication required).

Future versions will support OAuth 2.0 and JWT tokens.

---

## Process Management

All process-related endpoints under `/processes`.

### Deploy Process Definition

Create a new process definition from BPMN-compatible JSON.

```http
POST /processes
Content-Type: application/json
```

**Request Body**:
```json
{
  "nodes": [
    {
      "id": "start_1",
      "type": "StartEvent",
      "name": "Start Process"
    },
    {
      "id": "userTask_1",
      "type": "UserTask",
      "name": "Review Document",
      "config": {
        "inputs": [],
        "outputs": []
      }
    },
    {
      "id": "end_1",
      "type": "EndEvent",
      "name": "End"
    }
  ],
  "edges": [
    {
      "source": "start_1",
      "target": "userTask_1"
    },
    {
      "source": "userTask_1",
      "target": "end_1"
    }
  ]
}
```

**Response** (200):
```json
{
  "id": 1,
  "name": "MyProcess",
  "definitionJson": "{...}",
  "version": 1
}
```

### Start Process Instance

Create and start a new instance of a process definition.

```http
POST /processes/{processDefinitionId}/start
```

**Parameters**:
- `processDefinitionId` (path): Process definition ID

**Response** (200):
```json
{
  "id": 100,
  "processDefinitionId": 1,
  "status": "ACTIVE",
  "currentNode": ["userTask_1"],
  "nodeHistory": ["start_1"],
  "createdAt": "2026-04-14T10:00:00Z",
  "updatedAt": "2026-04-14T10:00:00Z"
}
```

### List Process Instances

Get all process instances with pagination.

```http
GET /processes/instances?page=0&size=20&sort=createdAt,desc
```

**Query Parameters**:
- `page`: 0-indexed page (default: 0)
- `size`: Results per page (default: 20)
- `sort`: Sort field and direction (default: id,asc)

**Response** (200):
```json
{
  "content": [
    {
      "id": 100,
      "processDefinitionId": 1,
      "status": "ACTIVE",
      "currentNode": ["userTask_1"],
      "nodeHistory": ["start_1"],
      "createdAt": "2026-04-14T10:00:00Z",
      "updatedAt": "2026-04-14T10:00:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "currentPage": 0,
  "pageSize": 20
}
```

### List Process Definitions

Get latest versions of all process definitions.

```http
GET /processes?page=0&size=20
```

**Response** (200): Array of ProcessDefinition objects (see Deploy endpoint).

### Send Message to Process

Trigger a message event in a running process.

```http
POST /processes/messages
Content-Type: application/json
```

**Request Body**:
```json
{
  "messageName": "approvalReceived",
  "correlationKey": "order_123",
  "variables": {
    "approved": true,
    "approverName": "John Doe"
  }
}
```

**Response** (200):
```json
{
  "status": "success",
  "message": "Message received and process resumed",
  "messageName": "approvalReceived",
  "correlationKey": "order_123"
}
```

---

## Task Management

All task-related endpoints under `/tasks`.

### List All Tasks

Get all tasks with pagination.

```http
GET /tasks?page=0&size=20&sort=createdAt,desc
```

**Response** (200):
```json
{
  "content": [
    {
      "id": 200,
      "processInstanceId": 100,
      "title": "Review Document",
      "nodeId": "userTask_1",
      "assignee": null,
      "status": "PENDING",
      "createdAt": "2026-04-14T10:00:00Z",
      "completedAt": null,
      "formId": 50
    }
  ],
  "totalElements": 25,
  "totalPages": 2
}
```

### Get Task by ID

Retrieve a specific task.

```http
GET /tasks/{taskId}
```

**Parameters**:
- `taskId` (path): Task ID

**Response** (200): Single Task object (see List above).

**Response** (404): Task not found.

### Search Tasks

Filter tasks by assignee and/or status.

```http
GET /tasks/search?assignee=john&status=PENDING&page=0&size=20
```

**Query Parameters**:
- `assignee`: Username (optional)
- `status`: `PENDING` or `COMPLETED` (optional)
- `page`: 0-indexed page (default: 0)
- `size`: Results per page (default: 20)

**Response** (200): Filtered task list (see List above).

### Complete Task

Mark a task as completed and advance the process.

```http
POST /tasks/{taskId}/complete
Content-Type: application/json
```

**Parameters**:
- `taskId` (path): Task ID

**Request Body**:
```json
{
  "assignee": "john",
  "variables": {
    "approved": true,
    "comments": "Document looks good"
  }
}
```

**Response** (200):
```json
"Task completed successfully"
```

**Response** (400): Missing assignee or invalid request.

**Response** (404): Task not found.

**Response** (409): Task already completed.

---

## Form Management

All form-related endpoints under `/forms`.

### Deploy Form

Create a new form definition with JSON Schema.

```http
POST /forms
Content-Type: application/json
```

**Request Body**:
```json
{
  "name": "ApprovalForm",
  "schema": {
    "title": "Approval Form",
    "type": "object",
    "properties": {
      "approved": {
        "type": "boolean",
        "title": "Approve?"
      },
      "comments": {
        "type": "string",
        "title": "Comments",
        "maxLength": 500
      }
    },
    "required": ["approved"]
  }
}
```

**Response** (200):
```json
{
  "id": 50,
  "name": "ApprovalForm",
  "schema": {...},
  "version": 1,
  "createdAt": "2026-04-14T10:00:00Z"
}
```

### Get Latest Form

Get latest version of a form by name.

```http
GET /forms/latest?name=ApprovalForm
```

**Query Parameters**:
- `name`: Form name (required)

**Response** (200): Form object (see Deploy).

**Response** (404): Form not found.

### Get Form by ID

Retrieve a specific form version.

```http
GET /forms/{formId}
```

**Parameters**:
- `formId` (path): Form ID

**Response** (200): Form object (see Deploy).

### Get All Form Versions

Get all versions of a form.

```http
GET /forms?name=ApprovalForm
```

**Query Parameters**:
- `name`: Form name (required)

**Response** (200):
```json
[
  {
    "id": 50,
    "name": "ApprovalForm",
    "version": 2,
    "createdAt": "2026-04-14T10:05:00Z"
  },
  {
    "id": 49,
    "name": "ApprovalForm",
    "version": 1,
    "createdAt": "2026-04-14T10:00:00Z"
  }
]
```

---

## Health & Metrics

System health and observability endpoints.

### System Health

```http
GET /actuator/health
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "database": {"status": "UP"},
    "rabbitmq": {"status": "UP"}
  }
}
```

### All Metrics

List available metrics.

```http
GET /actuator/metrics
```

### Prometheus Metrics

Export metrics in Prometheus text format.

```http
GET /actuator/prometheus
```

---

## Data Models

### ProcessDefinition

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `name` | String | Process name |
| `version` | Int | Version number (auto-increment) |
| `definitionJson` | String | BPMN definition as JSON |

### ProcessInstance

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `processDefinitionId` | Long | FK to ProcessDefinition |
| `status` | Enum | ACTIVE, COMPLETED, FAILED, CANCELLED |
| `currentNode` | String[] | Active node IDs |
| `nodeHistory` | String[] | Executed node history |
| `createdAt` | DateTime | Creation timestamp |
| `updatedAt` | DateTime | Last update timestamp |

### Task

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `processInstanceId` | Long | FK to ProcessInstance |
| `title` | String | Task title |
| `nodeId` | String | Node ID in process definition |
| `assignee` | String | Assigned user (null if unassigned) |
| `status` | Enum | PENDING, COMPLETED |
| `formId` | Long | FK to Form (nullable) |
| `createdAt` | DateTime | Creation timestamp |
| `completedAt` | DateTime | Completion timestamp (null if pending) |

### Form

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `name` | String | Form name |
| `schema` | JSONB | JSON Schema definition |
| `version` | Int | Version number |
| `createdAt` | DateTime | Creation timestamp |

### ProcessVariable

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `processInstanceId` | Long | FK to ProcessInstance |
| `name` | String | Variable name |
| `value` | JSONB | Variable value (typed as JSON) |

### TaskVariable

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique identifier |
| `taskId` | Long | FK to Task |
| `name` | String | Variable name |
| `value` | JSONB | Variable value (from form data) |

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| **200** | Success - Request processed |
| **201** | Created - Resource created |
| **204** | No Content - Success with no body |
| **400** | Bad Request - Invalid input |
| **404** | Not Found - Resource doesn't exist |
| **409** | Conflict - Invalid state (e.g., task already completed) |
| **500** | Server Error - Internal error |

---

## Error Responses

All errors return JSON with details:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required field: assignee"
}
```

---

## Pagination

All list endpoints support pagination:

**Parameters**:
- `page`: 0-indexed page number (default: 0)
- `size`: Items per page (default: 20, max: 100)
- `sort`: Sort by field (e.g., `createdAt,desc`)

**Response**:
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20
}
```

---

## API Documentation

Interactive API documentation is available at:

**Swagger UI**: http://localhost:8080/swagger-ui.html

**OpenAPI Spec**: http://localhost:8080/v3/api-docs

All endpoints are documented with request/response examples and schema validation.

