# Features & Architecture

Easy BPM is a Spring Boot-based Business Process Management engine built in Kotlin. It provides a complete solution for deploying, executing, and monitoring BPMN-style processes.

---

## Implemented Features

### ✅ Core Process Engine
- **Process Deployment**: Upload and store BPMN-compatible process definitions
- **Process Instances**: Create and execute multiple instances per definition
- **Version Control**: Automatic versioning of process definitions and forms
- **Process State Management**: Track process status (ACTIVE, COMPLETED, FAILED, CANCELLED)

### ✅ Node Types & Flows
- **Start/End Events**: Process entry and completion points
- **User Tasks**: Human interaction points with form integration
- **Service Tasks**: External system integration with async callback
- **Message Events**: Trigger process events via messages
- **Exclusive Gateways**: Conditional routing (if/then decision points)
- **Parallel Gateways**: Fan-out and join for parallel flows
- **Inclusive Gateways**: Complex conditional splits with multiple paths

### ✅ Variable Management
- **Process Variables**: Context data shared across entire process
- **Task Variables**: Form data isolated to individual tasks
- **Input/Output Mapping**: Automatic variable transformation between nodes
- **Type Support**: JSON-based, supports nested objects and arrays
- **Variable Persistence**: All variables stored in PostgreSQL

### ✅ Task Management
- **Task Creation**: Automatically created for user tasks
- **Task Assignment**: Assign tasks to individual users
- **Task Status**: PENDING, COMPLETED
- **Form Integration**: Link forms to tasks for data collection
- **Task Completion**: Mark complete with variable submission
- **Task Search**: Query by assignee, status with pagination

### ✅ Form Management
- **Dynamic Forms**: JSON Schema-based form definitions
- **Version Control**: Multiple versions per form name
- **Form Linking**: Reference forms from tasks
- **Input/Output**: Map between task/process variables via forms

### ✅ External Integration
- **HTTP Integration**: Call external APIs (GET, POST, PUT, DELETE)
- **Header Customization**: Pass custom headers to external services
- **Response Processing**: Map HTTP responses to process variables
- **Error Handling**: Graceful error handling with variable mapping

### ✅ Messaging & Events
- **Message Sending**: Publish messages to external systems
- **Message Receiving**: Process listens for and handles incoming messages
- **Correlation Keys**: Link messages to specific process instances
- **Topic-based Routing**: RabbitMQ topic exchange for event distribution

### ✅ Worker Architecture (Phase 2)
- **Async Execution**: Service tasks executed by external workers
- **Retry Logic**: Exponential backoff (5s, 10s, 20s)
- **Idempotency**: SHA-256 hash tracking prevents duplicate execution
- **Dead Letter Queue**: Failed tasks routed after 3 retries
- **Request Tracking**: Track worker request state and history

### ✅ Observability (Phase 3)
- **Health Checks**: Database and RabbitMQ health indicators
- **Metrics Collection**: Process, task, and node execution metrics
- **Prometheus Export**: Real-time metrics for monitoring systems
- **Performance Tracking**: 50th, 95th, 99th percentile timing
- **Rate Monitoring**: Track throughput (started, completed, failed)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│          REST Controllers (Spring MVC)               │
│  ProcessController | TaskController | FormController │
└────────────┬────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────┐
│          Service Layer (Business Logic)              │
│  ProcessService | TaskService | GatewayService      │
│  IntegrationService | FormService | WorkerListener  │
└────────────┬────────────────────────────────────────┘
             │
┌────────────▼───────────────────────────────────────┐
│            Data Access Layer (JPA)                   │
│  ProcessInstanceRepository | TaskRepository         │
│  ProcessVariableRepository | FormRepository         │
└────────────┬───────────────────────────────────────┘
             │
┌────────────▼───────────────────────────────────────┐
│         PostgreSQL Database (Flyway)                 │
│  process_definition | process_instance | task       │
│  process_variable | task_variable | form            │
└───────────────────────────────────────────────────┘

     ┌─────────────────────────────────────┐
     │      RabbitMQ Message Broker        │
     │  task events | service requests     │
     │  service completions | worker queue │
     └─────────────────────────────────────┘
```

---

## Data Model

### ProcessDefinition
Stores BPMN process templates with versioning.

```json
{
  "id": 1,
  "name": "OrderApproval",
  "definitionJson": {
    "nodes": [
      {
        "id": "start_1",
        "type": "StartEvent"
      },
      {
        "id": "humanTask_1",
        "type": "HumanTask",
        "name": "Approve Order",
        "config": {
          "inputs": [...],
          "outputs": [...]
        }
      }
    ],
    "edges": [...]
  },
  "version": 1
}
```

### ProcessInstance
Tracks runtime execution of a process.

```json
{
  "id": 100,
  "processDefinitionId": 1,
  "status": "ACTIVE",
  "currentNode": ["humanTask_1"],
  "nodeHistory": ["start_1"],
  "createdAt": "2026-04-14T10:00:00Z",
  "updatedAt": "2026-04-14T10:05:00Z"
}
```

### Task
User-facing work item for human tasks.

```json
{
  "id": 200,
  "processInstanceId": 100,
  "title": "Approve Order",
  "nodeId": "humanTask_1",
  "assignee": "john@example.com",
  "status": "PENDING",
  "formId": 50,
  "createdAt": "2026-04-14T10:00:00Z",
  "completedAt": null
}
```

### ProcessVariable & TaskVariable
Context data stored as JSON.

```json
{
  "id": 1,
  "processInstanceId": 100,
  "name": "orderAmount",
  "value": {"type": "number", "value": 500.00}
}
```

---

## Process Execution Flow

### 1. Deploy Process
```
POST /processes
  ↓
Validate definition JSON
  ↓
Store in process_definition table with version=1
```

### 2. Start Instance
```
POST /processes/{id}/start
  ↓
Create ProcessInstance with status=ACTIVE
  ↓
Initialize process variables
  ↓
Find StartEvent node
  ↓
Execute next nodes (recursively)
```

### 3. Execute Node
```
Node Router:
  ├─ UserTask → Create Task, publish event, apply inputs
  ├─ ServiceTask → Publish to RabbitMQ, wait for callback
  ├─ Gateway → Evaluate conditions, find next nodes
  ├─ MessageEvent → Subscribe, wait for message
  └─ EndEvent → Mark process COMPLETED
```

### 4. Complete Task
```
POST /tasks/{id}/complete
  ↓
Persist TaskVariables from form data
  ↓
Apply output mapping (task vars → process vars)
  ↓
Find next nodes in definition
  ↓
Update Task status = COMPLETED
  ↓
Publish TaskCompleted event
  ↓
Advance ProcessInstance
  ↓
Execute next steps (continue engine)
```

---

## Service Task Execution

### Synchronous Execution
For internal service tasks that set process variables:

```json
{
  "type": "ServiceTask",
  "config": {
    "variables": [
      {
        "name": "approvedAmount",
        "source": "static",
        "value": "1000"
      }
    ]
  }
}
```

### Asynchronous Execution (Worker Pattern)
For external systems:

1. **Task Creation**:
   - Service task found in definition
   - Request published to RabbitMQ queue

2. **Worker Processing**:
   - Worker polls and processes request
   - Tracks idempotency key to prevent duplicates
   - Retries with exponential backoff on failure
   - Publishes result back to completion queue

3. **Process Resumption**:
   - ProcessService receives completion message
   - Merges returned variables into process context
   - Continues with next nodes

---

## Condition Evaluation

Gateway routing uses SpEL (Spring Expression Language) or JavaScript:

```json
{
  "type": "ExclusiveGateway",
  "edges": [
    {
      "target": "approveTask",
      "condition": "${amount > 1000 && status == 'urgent'}"
    },
    {
      "target": "rejectTask",
      "condition": "true"  // default
    }
  ]
}
```

**Variable substitution**: `${variableName}` replaced with actual process variable value before evaluation.

---

## Entity Relationships

```
ProcessDefinition (1) ──→ (N) ProcessInstance
                              ├─→ (N) ProcessVariable
                              ├─→ (N) Task
                              │        ├─→ Form
                              │        └─→ (N) TaskVariable
                              └─→ (N) MessageSubscription
```

---

## Database Schema (14 Migrations)

| Table | Purpose |
|-------|---------|
| `process_definition` | BPMN templates |
| `process_instance` | Runtime executions |
| `process_variable` | Instance context data |
| `task` | User work items |
| `task_variable` | Task form data |
| `form` | Dynamic form schemas |
| `message_subscription` | Pending message events |
| `worker_request` | Async service task tracking |

---

## Asynchronous Messaging

### RabbitMQ Exchange: `bpm.exchange` (Topic)

| Event | Routing Key | Queue | Listener |
|-------|------------|-------|----------|
| Service Task Request | `service.task.request` | `service-task-requests` | Worker |
| Service Task Completion | `service.task.completion` | `service-task-completions` | RabbitListenerService |
| Task Created | `task.created` | `task-created` | (External subscribers) |
| Task Completed | `task.completed` | `task-completed` | (External subscribers) |
| Message Thrown | `message.sent` | (no queue) | (External system) |

---

## Key Classes & Responsibilities

### ProcessService
Main orchestration engine handling:
- Process deployment and versioning
- Instance creation and state management
- Node execution routing
- Variable initialization and persistence
- Message reception and event handling

### TaskService
Task lifecycle management:
- Task creation for user tasks
- Form input/output mapping
- Task completion workflow
- Task query and search
- Variable transformation

### GatewayService
Decision routing logic:
- Condition evaluation (SpEL/JavaScript)
- Next node resolution
- Parallel gateway token counting
- Inclusive gateway handling

### IntegrationService
External system calls:
- HTTP request execution
- Header and payload mapping
- Response variable extraction
- Error handling

### FormService
Form schema management:
- Form deployment with versioning
- Version history retrieval
- Form lookup by ID/name

### RabbitListener & RabbitPublisher
Async event handling:
- Publish task/service events
- Listen for service completions
- Persist events to queue

---

## Configuration

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/easybpm
    username: easybpm
    password: easybpm
  jpa:
    hibernate:
      ddl-auto: validate
  rabbitmq:
    host: localhost
    username: easybpm
    password: easybpm

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

---

## Known Limitations

- Parallel gateway requires explicit join synchronization
- Timer events not yet implemented
- Call activity (subprocess) support planned
- Form validation on task completion not enforced at API level
- Long-running processes may accumulate variable size (no archival)

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Runtime | Spring Boot 3.5.3, Kotlin |
| REST | Spring MVC, OpenAPI/Swagger |
| Database | PostgreSQL, Hibernate JPA, Flyway |
| Messaging | RabbitMQ, Spring AMQP |
| JSON | Jackson |
| Scripting | SpEL, Nashorn JavaScript |
| Observability | Micrometer, Prometheus, Spring Actuator |
| Testing | Spring Test, H2 in-memory DB |
| Build | Gradle, Kotlin DSL |
