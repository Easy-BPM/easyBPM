---
sidebar_position: 3
title: Architecture Overview
---

# Architecture Overview

Complete architecture guide for the Easy BPM Engine.

## System Architecture

```
┌──────────────────────────────────────────────────┐
│   REST API Layer (Port 8080)                     │
│  ProcessController | TaskController | FormCtrl   │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────────┐
│   Service Layer (Business Logic)                  │
│                                                   │
│  ProcessService      TaskService                 │
│    - Orchestration     - Task Lifecycle          │
│    - Node Execution    - Variable Mapping        │
│    - Message Handling  - Completion              │
│                                                   │
│  GatewayService      IntegrationService          │
│    - Routing Logic     - HTTP Calls              │
│    - Conditions        - Response Mapping        │
│                                                   │
│  FormService         MetricsService              │
│    - Versioning        - Observability           │
│    - Schema Mgmt       - Performance Tracking    │
└────────────────┬─────────────────────────────────┘
                 │
     ┌───────────┴────────────┐
     │                        │
┌────▼──────┐         ┌──────▼────┐
│  Database │         │  Messaging │
│ PostgreSQL │        │  RabbitMQ  │
│            │        │            │
│ Tables:    │        │ Exchanges: │
│ - process_ │        │ - bpm.ex   │
│   definit. │        │ Queues:    │
│ - process_ │        │ - requests │
│   instance │        │ - response │
│ - task     │        │ - dlq      │
│ - form     │        │ - events   │
│ - variable │        │            │
│ - worker_  │        │            │
│   request  │        │            │
└────────────┘        └────────────┘
```

---

## Core Components

### 1. REST Controllers

**ProcessController** (`POST /processes`, `GET /processes`, etc.)
- Deploy process definitions
- Start process instances
- Send messages
- Query process state

**TaskController** (`GET /tasks`, `POST /tasks/{id}/complete`)
- List and search tasks
- Complete tasks with variables
- Query task status

**FormController** (`POST /forms`, `GET /forms`)
- Deploy form definitions
- Query form versions
- Retrieve form schemas

### 2. Service Layer

**ProcessService** - Main orchestration engine
- Validates and stores process definitions
- Creates process instances
- Executes process node by node
- Handles message reception
- Manages process variables
- Routes to specialized node handlers

**TaskService** - Task lifecycle management
- Creates tasks for user tasks
- Handles task completion
- Applies variable input/output mappings
- Searches tasks with filters
- Records task metrics

**GatewayService** - Decision routing
- Evaluates conditions (SpEL, JavaScript)
- Determines next node(s)
- Handles parallel gateway synchronization
- Manages token counting

**IntegrationService** - External services
- Executes HTTP calls (GET, POST, PUT, DELETE)
- Customizes headers
- Maps request/response payloads
- Extracts values to process variables

**FormService** - Form management
- Stores form definitions with versioning
- Retrieves latest versions
- Manages schema history

**MetricsService** - Observability
- Records execution timers
- Tracks completion counters
- Manages health gauges
- Exports to Micrometer/Prometheus

### 3. Data Access Layer

All repositories follow Spring Data JPA pattern:
- `ProcessDefinitionRepository`
- `ProcessInstanceRepository`
- `TaskRepository`
- `ProcessVariableRepository`
- `TaskVariableRepository`
- `FormRepository`
- `WorkerRequestRepository`

### 4. Message Broker (RabbitMQ)

**Exchange**: `bpm.exchange` (Topic)

| Queue | Routing Key | Purpose |
|-------|------------|---------|
| `service-task-requests` | `service.task.*` | Worker task requests |
| `service-task-completions` | `service.task.*` | Worker task results |
| `service-task-dlq` | `service.task.dlq` | Failed tasks (3+ retries) |
| `task-created` | `task.created` | Task creation events |
| `task-completed` | `task.completed` | Task completion events |

---

## Process Execution Flow

### 1. Deploy Process

```
Client Request
    ↓
ProcessController.deploy()
    ↓
ProcessService.deployProcess()
    ↓
Validate JSON structure
    ↓
Store ProcessDefinition (auto-versioned)
    ↓
Return process ID + version
```

### 2. Start Instance

```
Client Request (processDefinitionId)
    ↓
ProcessController.startInstance()
    ↓
ProcessService.startProcessInstance()
    ↓
Load ProcessDefinition
    ↓
Create ProcessInstance (ACTIVE)
    ↓
Initialize process variables
    ↓
Find StartEvent node
    ↓
Execute next nodes (recursive)
```

### 3. Execute Single Node

```
Node routing in ProcessService.executeNode()
    ↓
┌───────────────────────┴───────────────────────┐
│                                               │
UserTask          ServiceTask         Gateway
  ↓                  ↓                   ↓
Create Task    Publish to RabbitMQ  Evaluate
  ↓                  ↓                   ↓
Apply Inputs   Wait for Completion   Find Next
  ↓                  ↓                   ↓
Publish Event  Resume on Result    Continue
```

### 4. Complete Task

```
Client: POST /tasks/{id}/complete
    ↓
TaskService.completeTask()
    ↓
Get Task entity
    ↓
Persist form variables (TaskVariable records)
    ↓
Apply output mapping → process variables
    ↓
Find next nodes in definition
    ↓
Update Task status = COMPLETED
    ↓
Publish TaskCompleted event
    ↓
Advance ProcessInstance
    ↓
Execute next steps
```

---

## Data Model

### Core Tables

**process_definition**
- `id`: PK
- `name`: Process name (indexed)
- `version`: Auto-incremented by name
- `definition_json`: JSONB with node/edge structure

**process_instance**
- `id`: PK
- `process_definition_id`: FK
- `status`: Enum (ACTIVE,COMPLETED,FAILED,CANCELLED)
- `current_nodes`: JSONB array of active node IDs
- `node_history`: JSONB array of executed nodes
- `created_at`, `updated_at`: Timestamps

**task**
- `id`: PK
- `process_instance_id`: FK
- `title`: Display name
- `node_id`: Reference to process node
- `assignee`: User (nullable)
- `status`: Enum (PENDING, COMPLETED)
- `form_id`: FK to Form (nullable)
- `created_at`, `completed_at`: Timestamps

**form**
- `id`: PK
- `name`: Form name (indexed)
- `version`: Auto-incremented
- `schema`: JSONB with field definitions
- `created_at`: Timestamp

**process_variable**
- `id`: PK
- `process_instance_id`: FK
- `name`: Variable name
- `value`: JSONB typed value

**task_variable**
- `id`: PK
- `task_id`: FK
- `name`: Variable name
- `value`: JSONB from form submission

**worker_request** (Phase 2)
- `id`: PK
- `process_instance_id`: FK
- `request_payload`: JSONB
- `result_payload`: JSONB
- `status`: Enum (PENDING, SUCCESS, FAILED, DLQ)
- `retry_count`: Int
- `idempotency_key`: SHA-256 hash (unique)

---

## Node Type Handlers

| Node Type | Handler | Behavior |
|-----------|---------|----------|
| **StartEvent** | ProcessService | Entry point, no action |
| **EndEvent** | ProcessService | Mark instance COMPLETED |
| **UserTask** | TaskService | Create Task record, await completion |
| **ServiceTask** | ProcessService | Publish to RabbitMQ or execute internally |
| **APITask** | IntegrationService | HTTP call with variable mapping |
| **ExclusiveGateway** | GatewayService | Single path (first matching condition) |
| **ParallelGateway** | GatewayService | Split/join with token counting |
| **InclusiveGateway** | GatewayService | Multiple conditional paths |
| **MessageIntermediateCatchEvent** | ProcessService | Subscribe to message, wait |
| **MessageIntermediateThrowEvent** | ProcessService | Publish message, continue |
| **ScriptTask** | TaskService | Execute inline JavaScript |

---

## Variable Mapping System

### Input Mapping

Defined in node config, executed when node starts:

```json
{
  "inputs": [
    {
      "targetName": "form_field_name",
      "source": "variable|static",
      "value": "processVarName|literal"
    }
  ]
}
```

Flow: `ProcessVariable` → transforms → `TaskVariable`

### Output Mapping

Defined in node config, executed on node completion:

```json
{
  "outputs": [
    {
      "target": "variable",
      "sourceName": "form_field_name",
      "value": "processVarName"
    }
  ]
}
```

Flow: `TaskVariable` → transforms → `ProcessVariable`

---

## Worker Architecture (Phase 2)

### Service Task Execution

**External (Async)**:
1. Service task found in process
2. Request published to `service-task-requests`
3. Worker picks up request
4. Executes work (with idempotency check)
5. Posts result to `service-task-completions`
6. ProcessService resumes from message listener
7. Merges returned variables
8. Continues to next nodes

**Internal (Sync)**:
- Service task with `variables` config
- Values set immediately
- Process continues without waiting

### Retry Strategy

- **Attempt 1**: Immediate execution
- **Failure**: Retry in 5 seconds
- **Failure**: Retry in 10 seconds
- **Failure**: Retry in 20 seconds
- **Failure**: Route to DLQ
- **Max retries**: 3

### Idempotency

- Request payload SHA-256 hashed
- Hash stored as unique constraint
- Duplicate requests skip execution (return cached result)
- Prevents duplicate charges/transactions

---

## Observability (Phase 3)

### Metrics Collected

**Timers** (50th, 95th, 99th percentiles):
- `process.execution.duration` - Start to finish
- `node.execution.duration` - Per node (tagged by type)
- `task.execution.duration` - Task processing
- `task.query.duration` - Database query time
- `service.task.duration` - External service calls

**Counters**:
- `process.started.total` - Processes created
- `process.completed.total` - Successful completions
- `process.failed.total` - Failed processes
- `task.created.total` - Tasks created
- `task.completed.total` - Tasks completed
- `service.task.retry.total` - Retries attempted
- `service.task.dlq.total` - DLQ routed
- `message.event.received.total` - Messages received

**Gauges**:
- `process.active` - Running processes
- `task.active` - Pending tasks
- `database.available` - DB connectivity (1/0)
- `rabbitmq.available` - RabbitMQ connectivity (1/0)

### Health Checks

Custom health indicators check external dependencies:

```
GET /actuator/health
  ├─ database (PostgreSQL)
  └─ rabbitmq (AMQP)
```

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Runtime** | Spring Boot | 3.5.3 |
| **Language** | Kotlin | 1.9+ |
| **JDK** | OpenJDK | 21+ |
| **ORM** | Hibernate JPA | Bundled |
| **Migrations** | Flyway | Bundled |
| **Database** | PostgreSQL | 12+ |
| **Messaging** | RabbitMQ | 3.10+ |
| **JSON** | Jackson | 2.15+ |
| **HTTP** | Spring RestTemplate | Bundled |
| **Scripting** | Nashorn JS | Java 21 |
| **Metrics** | Micrometer | Latest |
| **Monitoring** | Prometheus | Compatible |

---

## Scaling Considerations

### Database
- Index on `(process_instance_id, name)` for variable lookups
- Partition `process_instance` by date for large datasets
- Archive old instances (6+ months)

### RabbitMQ
- Increase prefetch count for worker throughput
- Use multiple worker instances
- Monitor DLQ depth

### Horizontal Scaling
- Stateless services (can run multiple instances)
- Shared PostgreSQL database
- Shared RabbitMQ broker
- Load balance REST API calls

---

## Security

### Current Status
- ⚠️ No authentication (all endpoints public)
- All user inputs validated
- SQL injection prevented (parameterized queries)
- Process definitions stored as JSONB (immutable once deployed)

### Future Enhancements
- OAuth 2.0 / OpenID Connect
- JWT token validation
- Role-based access control (RBAC)
- Audit logging
- Process definition signing
