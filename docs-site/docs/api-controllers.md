# API & Controllers

The Easy BPM Engine provides REST endpoints for managing processes, tasks, and forms. All endpoints are documented with OpenAPI/Swagger integration.

## Process Management API

### Base Path: `/processes`

#### Deploy Process Definition
```http
POST /processes
Content-Type: application/json

{
  "nodes": [...],
  "edges": [...]
}
```

**Response**:
```json
{
  "id": 1,
  "name": "MyProcess",
  "definitionJson": "{...}",
  "version": 1
}
```

#### Start Process Instance
```http
POST /processes/{processDefinitionId}/start
```

Creates and starts a new instance of the process definition.

**Response**:
```json
{
  "id": 1,
  "processDefinitionId": 1,
  "status": "ACTIVE",
  "currentNode": ["node_1"],
  "nodeHistory": ["start_event"],
  "createdAt": "2026-04-14T12:00:00Z",
  "updatedAt": "2026-04-14T12:00:00Z"
}
```

#### List Process Instances
```http
GET /processes/instances?page=0&size=20&sort=createdAt,desc
```

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "processDefinitionId": 1,
      "status": "ACTIVE",
      "currentNode": ["userTask_1"],
      "createdAt": "2026-04-14T12:00:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "currentPage": 0
}
```

#### List Process Definitions
```http
GET /processes?page=0&size=20
```

Returns latest version of each process definition.

#### Send Message to Process
```http
POST /processes/messages
Content-Type: application/json

{
  "messageName": "approvalReceived",
  "correlationKey": "order_123",
  "variables": {
    "approved": true,
    "approverName": "John Doe"
  }
}
```

Triggers message intermediate events waiting for this message.

**Response**:
```json
{
  "status": "success",
  "message": "Message received and process resumed",
  "messageName": "approvalReceived",
  "correlationKey": "order_123"
}
```

---

## Task Management API

### Base Path: `/tasks`

#### List All Tasks
```http
GET /tasks?page=0&size=20
```

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "processInstanceId": 1,
      "title": "Review Document",
      "nodeId": "userTask_1",
      "assignee": null,
      "status": "PENDING",
      "createdAt": "2026-04-14T12:00:00Z",
      "completedAt": null,
      "formId": 1
    }
  ],
  "totalElements": 25,
  "totalPages": 2
}
```

#### Get Task by ID
```http
GET /tasks/{taskId}
```

**Response**: Single task object (see above).

#### Search Tasks
```http
GET /tasks/search?assignee=john&status=PENDING&page=0&size=20
```

Filter tasks by:
- `assignee`: Username (optional)
- `status`: `PENDING` or `COMPLETED` (optional)

#### Complete Task
```http
POST /tasks/{taskId}/complete
Content-Type: application/json

{
  "assignee": "john",
  "variables": {
    "approved": true,
    "comments": "Looks good"
  }
}
```

Marks the task as complete and provides task form data.

**Response**:
```json
"Task completed successfully"
```

**Error Response** (400):
```json
"Missing assignee"
```

**Error Response** (409):
```json
"Task already completed"
```

---

## Form Management API

### Base Path: `/forms`

#### Deploy Form
```http
POST /forms
Content-Type: application/json

{
  "name": "ApprovalForm",
  "schema": {
    "title": "Approval",
    "type": "object",
    "properties": {
      "approved": {"type": "boolean"},
      "comments": {"type": "string"}
    },
    "required": ["approved"]
  }
}
```

**Response**:
```json
{
  "id": 1,
  "name": "ApprovalForm",
  "schema": {...},
  "version": 1,
  "createdAt": "2026-04-14T12:00:00Z"
}
```

#### Get Latest Form Version
```http
GET /forms/latest?name=ApprovalForm
```

Returns the most recent version of the form.

#### Get Form by ID
```http
GET /forms/{formId}
```

#### Get All Versions of a Form
```http
GET /forms?name=ApprovalForm
```

Returns all versions with version history.

---

## Response Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 400 | Bad Request (missing required fields) |
| 404 | Not Found (resource doesn't exist) |
| 409 | Conflict (e.g., task already completed) |
| 500 | Server Error |

---

## Pagination

All list endpoints support Spring Data pagination:
- `page`: 0-indexed page number (default: 0)
- `size`: Results per page (default: 20)
- `sort`: Sort by field (e.g., `sort=createdAt,desc`)

---

## OpenAPI/Swagger

Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui.html
```

OpenAPI spec:
```
http://localhost:8080/v3/api-docs
```
