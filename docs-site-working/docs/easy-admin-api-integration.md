---
sidebar_position: 9
---

# Easy BPM Admin: API Integration Reference

## Overview

Easy BPM Admin communicates exclusively with the Easy BPM backend REST API. This document provides detailed reference for all endpoints Easy Admin consumes and how they integrate with the frontend.

## Base URL

```
http://localhost:8085
```

All examples assume this base URL. In production, configure `VITE_API_BASE_URL` or update the default in `easy-bpm-admin/services/adminService.ts`:

```typescript
const API_BASE_URL = 'http://your-production-api.com';
```

## Authentication

### POST /login

Authenticate user and obtain session token.

**Request**:
```http
POST /login
Content-Type: application/json

{
  "username": "admin",
  "password": "secret"
}
```

**Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "roles": ["ADMIN", "OPERATOR"],
    "email": "admin@example.com"
  }
}
```

**Response (401 Unauthorized)**:
```json
{
  "error": "Invalid credentials",
  "message": "Username or password incorrect"
}
```

**Easy Admin Integration**:
```typescript
// LoginView.tsx
const handleSubmit = async (e) => {
  const response = await fetch(`${API_BASE_URL}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  
  if (response.ok) {
    const { token, user } = await response.json();
    localStorage.setItem('authToken', token);
    setCurrentUser(user.username);
  }
};
```

:::note Development Mode
Current implementation uses mock login (no real authentication). See [Security Configuration](#security-configuration) to enable real authentication.
:::

## Security Configuration

For production readiness:

1. Enable real `POST /login` implementation in backend
2. Require and validate bearer token on admin API calls
3. Enforce role-based access (`ADMIN`, `OPERATOR`, `VIEWER`) per endpoint
4. Disable mock login paths in frontend build configuration

---

## Process Instances

### GET /processes/instances

Fetch paginated list of process instances.

**Parameters**:
| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `page` | int | No | 0 | Page number (0-indexed) |
| `size` | int | No | 20 | Items per page |
| `status` | string | No | - | Filter by status: ACTIVE, PAUSED, CANCELLED, COMPLETED |
| `keyword` | string | No | - | Search by instance ID or process name |

**Request**:
```bash
GET /processes/instances?page=0&size=20&status=ACTIVE
```

**Response (200 OK)**:
```json
{
  "content": [
    {
      "id": 1001,
      "processDefinitionId": 1,
      "processDefinitionName": "Order Fulfillment",
      "status": "ACTIVE",
      "currentNode": ["user-review"],
      "nodeHistory": ["start", "validate-order", "user-review"],
      "createdAt": "2025-04-15T10:30:00Z",
      "updatedAt": "2025-04-15T14:25:00Z"
    },
    {
      "id": 1002,
      "processDefinitionId": 2,
      "processDefinitionName": "Expense Approval",
      "status": "PAUSED",
      "currentNode": ["manager-approval"],
      "nodeHistory": ["start", "submit-expense", "manager-approval"],
      "createdAt": "2025-04-14T09:15:00Z",
      "updatedAt": "2025-04-15T12:00:00Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 95,
  "number": 0,
  "size": 20
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx
const [instances, setInstances] = useState<ProcessInstance[]>([]);
const [totalPages, setTotalPages] = useState(0);
const [page, setPage] = useState(0);

useEffect(() => {
  const fetchInstances = async () => {
    const data = await adminService.getProcessInstances(page, 20);
    setInstances(data.content);
    setTotalPages(data.totalPages);
  };
  fetchInstances();
}, [page]);
```

---

### `GET /processes/instances/{id}`

Get a single process instance by ID.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request**:
```bash
GET /processes/instances/1001
```

**Response (200 OK)**:
```json
{
  "id": 1001,
  "processDefinitionId": 1,
  "processDefinitionName": "Order Fulfillment",
  "key": "order-fulfillment",
  "description": "Handle customer orders end-to-end",
  "status": "ACTIVE",
  "currentNode": ["user-review"],
  "nodeHistory": ["start", "validate-order", "user-review"],
  "createdAt": "2025-04-15T10:30:00Z",
  "updatedAt": "2025-04-15T14:25:00Z"
}
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Easy Admin Integration**:
```typescript
// Click instance row to expand details
const handleSelectInstance = async (instanceId: number) => {
  const instance = await adminService.findInstanceById(instanceId);
  setSelectedInstance(instance);
  
  // Also fetch variables for this instance
  const vars = await adminService.getInstanceVariables(instanceId);
  setVariables(vars);
};
```

---

### `POST /processes/instances/{id}/stop`

Stop (cancel) a running process instance.

**Behavior**:
- Transitions instance status from `ACTIVE` → `CANCELLED`
- Clears `currentNode` (no longer executing)
- Deletes all message subscriptions (prevents message-triggered resumption)
- Preserves task records and execution history (non-destructive)

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request**:
```bash
POST /processes/instances/1001/stop
```

**Response (200 OK)**:
```json
{
  "message": "Instance stopped successfully",
  "status": "CANCELLED"
}
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Response (409 Conflict)**:
```json
{
  "error": "Invalid State Transition",
  "message": "Cannot stop instance with status COMPLETED"
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx - Stop button handler
const handleStopInstance = async (instanceId: number) => {
  if (!window.confirm('This will cancel the instance. Continue?')) return;
  
  try {
    await adminService.stopInstance(instanceId);
    
    // Refresh instance detail
    const updated = await adminService.findInstanceById(instanceId);
    setSelectedInstance(updated);
    
    // Show success confirmation
    alert('Instance stopped successfully');
  } catch (error) {
    alert(`Failed to stop instance: ${error.message}`);
  }
};
```

:::info Non-Destructive
Stopping is reversible. Tasks remain in the database and can be queried. Implement manual restart feature in v2 to resume from stopped state.
:::

---

### `DELETE /processes/instances/{id}`

Permanently delete a process instance and all related data.

**Cascading Deletions**:
1. Delete all tasks for this instance
2. Delete all task variables
3. Delete all process variables
4. Delete all message subscriptions
5. Delete all worker requests
6. Delete the instance record itself

All deletions occur within a single `@Transactional` boundary (all-or-nothing semantics).

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request**:
```bash
DELETE /processes/instances/1001
```

**Response (204 No Content)**:
```
[empty body - deletion successful]
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Response (403 Forbidden)**:
```json
{
  "error": "Permission Denied",
  "message": "Only admins can delete instances"
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx - Delete button handler
const handleDeleteInstance = async (instanceId: number) => {
  if (!window.confirm('Delete this instance?')) return;
  if (!window.confirm('This cannot be undone. Really delete?')) return;
  
  try {
    await adminService.deleteInstance(instanceId);
    
    // Clear selection
    setSelectedInstance(null);
    
    // Remove from list
    setInstances(instances.filter(i => i.id !== instanceId));
    
    alert('Instance deleted successfully');
  } catch (error) {
    alert(`Failed to delete instance: ${error.message}`);
  }
};
```

:::danger Irreversible
Deletion permanently removes all instance data. No recovery possible after completion.
:::

---

## Process Variables

### `GET /processes/instances/{id}/variables`

Get all variables for a process instance.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request**:
```bash
GET /processes/instances/1001/variables
```

**Response (200 OK)**:
```json
[
  {
    "name": "orderId",
    "value": "ORD-9912"
  },
  {
    "name": "priority",
    "value": "HIGH"
  },
  {
    "name": "approved",
    "value": false
  },
  {
    "name": "amount",
    "value": 1500.50
  },
  {
    "name": "items",
    "value": ["SKU-001", "SKU-002", "SKU-003"]
  },
  {
    "name": "metadata",
    "value": {
      "source": "web_form",
      "version": 2
    }
  }
]
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx - Variable inspector
const handleFetchVariables = async (instanceId: number) => {
  const vars = await adminService.getInstanceVariables(instanceId);
  setVariables(vars);
};

// Render variables
{variables.map(v => (
  <div key={v.name} className="flex justify-between p-2 border-b">
    <span className="font-mono text-sm">{v.name}</span>
    <span className="font-mono text-sm text-gray-600">{JSON.stringify(v.value)}</span>
  </div>
))}
```

---

### `PUT /processes/instances/{id}/variables`

Assign or update variables in a process instance.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request Body**:
```json
{
  "variables": {
    "orderId": "ORD-9913",
    "approved": true,
    "priority": "CRITICAL",
    "notes": null,
    "items": ["SKU-001", "SKU-002"]
  }
}
```

**Response (200 OK)**:
```json
{
  "message": "Variables assigned successfully",
  "assigned": 5,
  "variables": [
    { "name": "orderId", "value": "ORD-9913" },
    { "name": "approved", "value": true },
    { "name": "priority", "value": "CRITICAL" },
    { "name": "notes", "value": null },
    { "name": "items", "value": ["SKU-001", "SKU-002"] }
  ]
}
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Response (400 Bad Request)**:
```json
{
  "error": "Invalid Variable Type",
  "message": "Variable 'amount' must be a number, got string"
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx - Variable assignment
const handleAssignVariables = async (vars: Record<string, unknown>) => {
  try {
    const response = await adminService.assignVariables(selectedInstance.id, vars);
    
    // Refresh variables
    const updated = await adminService.getInstanceVariables(selectedInstance.id);
    setVariables(updated);
    
    alert(`${response.assigned} variables assigned`);
  } catch (error) {
    alert(`Failed to assign variables: ${error.message}`);
  }
};

// Form submission
const handleSaveVariables = async (e) => {
  e.preventDefault();
  
  const formData = new FormData(e.target);
  const vars: Record<string, unknown> = {};
  
  for (const [key, value] of formData) {
    // Parse JSON if possible
    try {
      vars[key] = JSON.parse(value as string);
    } catch {
      vars[key] = value;
    }
  }
  
  await handleAssignVariables(vars);
};
```

---

## Node Control

### `POST /processes/instances/{id}/move-node`

Manually move process execution from one node to another.

**Behavior**:
- Validates both `fromNode` and `toNode` exist in process definition
- Verifies instance is currently at `fromNode`
- Moves execution to `toNode`
- Records movement in node history with timestamp and reason
- Resumes normal process execution from new node

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | long | Yes | Process instance ID |

**Request Body**:
```json
{
  "fromNode": "manager-approval",
  "toNode": "manager-approved",
  "reason": "Manager unreachable, auto-escalating to next step"
}
```

**Response (200 OK)**:
```json
{
  "message": "Node moved successfully",
  "fromNode": "manager-approval",
  "toNode": "manager-approved",
  "timestamp": "2025-04-15T15:30:45Z",
  "reason": "Manager unreachable, auto-escalating to next step",
  "nodeHistory": [
    "start",
    "submit-expense",
    "manager-approval",
    "manager-approved"
  ]
}
```

**Response (404 Not Found)**:
```json
{
  "error": "Instance Not Found",
  "message": "Process instance 1001 does not exist"
}
```

**Response (400 Bad Request)**:
```json
{
  "error": "Invalid Node Transition",
  "message": "Instance is at 'manager-approval' but requested to move from 'finance-review'"
}
```

**Response (409 Conflict)**:
```json
{
  "error": "Invalid State",
  "message": "Cannot move node: instance is CANCELLED"
}
```

**Easy Admin Integration**:
```typescript
// InstanceExplorerView.tsx - Node control
const handleMoveNode = async (fromNode: string, toNode: string, reason: string) => {
  try {
    const result = await adminService.moveNode(selectedInstance.id, {
      fromNode,
      toNode,
      reason
    });
    
    // Update node history
    setSelectedInstance(prev => ({
      ...prev,
      nodeHistory: result.nodeHistory,
      currentNode: [toNode]
    }));
    
    alert(`Moved from ${fromNode} to ${toNode}`);
  } catch (error) {
    alert(`Failed to move node: ${error.message}`);
  }
};

// UI Form
<form onSubmit={(e) => {
  e.preventDefault();
  const formData = new FormData(e.currentTarget);
  handleMoveNode(
    formData.get('fromNode') as string,
    formData.get('toNode') as string,
    formData.get('reason') as string
  );
}}>
  <select name="fromNode" defaultValue={selectedInstance?.currentNode?.[0]}>
    <option>-- Current Node --</option>
  </select>
  
  <select name="toNode">
    <option>-- Target Node --</option>
    {/* List available nodes from process definition */}
  </select>
  
  <textarea name="reason" placeholder="Reason for movement (optional)"></textarea>
  
  <button type="submit">Move Execution</button>
</form>
```

:::caution Advanced Feature
This feature requires understanding of the process model. Incorrect node movements can cause process deadlocks or data inconsistencies.
:::

---

## Process Definitions

### GET /processes/definitions

Fetch paginated list of deployed process definitions.

**Parameters**:
| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `page` | int | No | 0 | Page number (0-indexed) |
| `size` | int | No | 20 | Items per page |
| `keyword` | string | No | - | Search by process name or key |

**Request**:
```bash
GET /processes/definitions?page=0&size=20&keyword=order
```

**Response (200 OK)**:
```json
{
  "content": [
    {
      "id": "1",
      "name": "Order Fulfillment",
      "key": "order-fulfillment",
      "description": "Handle customer orders end-to-end",
      "version": 3
    },
    {
      "id": "2",
      "name": "Expense Approval",
      "key": "expense-approval",
      "description": "Approval workflow for expenses",
      "version": 2
    }
  ],
  "totalPages": 1,
  "totalElements": 2,
  "number": 0,
  "size": 20
}
```

**Easy Admin Integration**:
```typescript
// WorkflowCatalogView.tsx
const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);

useEffect(() => {
  const fetchDefinitions = async () => {
    const data = await adminService.getWorkflowDefinitions(0, 20);
    setDefinitions(data.content);
  };
  fetchDefinitions();
}, []);
```

---

### `GET /processes/definitions/{id}`

Get a single process definition by ID.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | string | Yes | Process definition ID |

**Request**:
```bash
GET /processes/definitions/1
```

**Response (200 OK)**:
```json
{
  "id": "1",
  "name": "Order Fulfillment",
  "key": "order-fulfillment",
  "description": "Handle customer orders end-to-end",
  "version": 3,
  "nodes": [
    { "id": "start", "type": "start", "name": "Start Order" },
    { "id": "validate-order", "type": "task", "name": "Validate Order" },
    { "id": "user-review", "type": "userTask", "name": "User Review" },
    { "id": "end", "type": "end", "name": "Order Complete" }
  ],
  "deployedAt": "2025-04-15T08:00:00Z"
}
```

---

## Tasks

### GET /tasks

Fetch paginated list of tasks.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `page` | int | No | Page number |
| `size` | int | No | Items per page |
| `status` | string | No | Filter: PENDING, COMPLETED, ASSIGNED |
| `assignee` | string | No | Filter by assignee username |

**Request**:
```bash
GET /tasks?page=0&size=20&status=PENDING
```

**Response (200 OK)**:
```json
{
  "content": [
    {
      "id": 501,
      "title": "Review Order",
      "name": "user-review",
      "description": "Review customer order for approval",
      "processInstanceId": 1001,
      "nodeId": "user-review",
      "assignee": "john.doe",
      "status": "PENDING",
      "createdAt": "2025-04-15T10:35:00Z",
      "completedAt": null,
      "formId": "order-review-form",
      "variables": {
        "orderId": "ORD-9912",
        "priority": "HIGH",
        "amount": 1500.50
      }
    }
  ],
  "totalPages": 3,
  "totalElements": 47,
  "number": 0,
  "size": 20
}
```

**Note**: `variables` field contains task context data (embedded from associated `ProcessVariable` records).

---

## Error Handling

### Standard Error Response Format

All error responses follow this pattern:

```json
{
  "error": "Error Category",
  "message": "Human-readable error description",
  "timestamp": "2025-04-15T15:30:45Z",
  "path": "/processes/instances/999"
}
```

### Status Codes

| Code | Scenario | Recovery |
|------|----------|----------|
| **200** | Success | No action needed |
| **204** | Success (no content) | No action needed |
| **400** | Invalid request | Check request body format |
| **401** | Unauthorized | Re-login |
| **403** | Forbidden | Check user permissions |
| **404** | Not found | Verify resource ID |
| **409** | Conflict | Invalid state transition |
| **500** | Server error | Check backend logs |

### Easy Admin Error Handling

```typescript
// Global error handler in adminService
const handleResponse = async (response: Response) => {
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || `HTTP ${response.status}`);
  }
  return response.json();
};

// Component-level error handling
try {
  await adminService.stopInstance(id);
} catch (error) {
  if (error.message.includes('404')) {
    setError('Instance not found. It may have been deleted.');
  } else if (error.message.includes('403')) {
    setError('You do not have permission to stop this instance.');
  } else {
    setError(`Operation failed: ${error.message}`);
  }
}
```

---

## CORS Configuration

Easy Admin runs on `http://localhost:5173`. Backend must allow CORS from this origin.

**Backend Configuration** (Spring Boot):
```yaml
# application.yml
cors:
  allowed-origins: http://localhost:5173,http://localhost:3001
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: Content-Type,Authorization
  allow-credentials: true
```

---

## Rate Limiting

:::note Not Implemented
Current API has no rate limiting. Consider implementing for production:
- 100 requests/minute per IP
- 10 requests/second per endpoint
:::

---

## Pagination

All list endpoints return paginated results:

```json
{
  "content": [...],           // Actual items
  "totalPages": 5,            // Total number of pages
  "totalElements": 95,        // Total number of items
  "number": 0,                // Current page (0-indexed)
  "size": 20                  // Items per page
}
```

**Navigation**:
```typescript
const nextPage = () => setPage(page + 1);
const prevPage = () => setPage(Math.max(0, page - 1));
const hasNextPage = page < totalPages - 1;
```

---

## Mock Mode

For development without a running backend, toggle mock mode:

```typescript
// services/adminService.ts
const USE_MOCK = true;
```

Mock endpoints return synthesized data with realistic delays (250-350ms).

---

## Production Deployment

### Environment Variables

```bash
REACT_APP_API_BASE_URL=https://api.yourdomain.com
```

### API Security Checklist

- [ ] HTTPS enabled (not HTTP)
- [ ] CORS configured for allowed origins only
- [ ] Authentication token refreshed automatically
- [ ] Sensitive data not logged to console
- [ ] Rate limiting enabled on backend
- [ ] Input validation on all endpoints
- [ ] Output sanitization (prevent XSS)

---

**Last Updated**: April 2025 | **Version**: 1.0.0 | **API Version**: 1.0.0
