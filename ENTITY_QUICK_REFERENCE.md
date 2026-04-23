# Quick Reference: Entity-to-Table Mappings

**Purpose**: Quick lookup for entity → table → columns mapping  
**Format**: CLI-friendly, grep-searchable  
**Generated**: 2026-04-23

---

## One-Line Summary

9 entities → 9 tables ✅ | 3 unimplemented tables from Phase 8.1 ❌

---

## Entity Quick Reference

### 1️⃣ Form
```
Entity: com.easy.bpm.model.form.Form
Table:  form (DEFAULT - no @Table annotation)
File:   src/main/kotlin/com/easy/bpm/model/form/Form.kt
Columns: id(PK), form_id, name, schema(JSONB), version, created_at
FK:     None
Status: ✅ COMPLETE
```

### 2️⃣ ProcessDefinition
```
Entity: com.easy.bpm.model.process.ProcessDefinition
Table:  process_definition (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/process/ProcessDefinition.kt
Columns: id(PK), process_id, process_name, description, version, definition_json(JSONB)
FK:     None
Status: ✅ COMPLETE
```

### 3️⃣ ProcessInstance
```
Entity: com.easy.bpm.model.process.ProcessInstance
Table:  process_instance (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/process/ProcessInstance.kt
Columns: id(PK), process_definition_id(FK), status, current_nodes(JSONB), node_history(JSONB),
          created_at, updated_at, parent_instance_id(Phase7), call_activity_node_id(Phase7),
          nesting_level(Phase7), completion_node_id(Phase7)
FK:     process_definition_id → process_definition.id (@ManyToOne ✅)
Status: ✅ COMPLETE (Phase 7: Call Activity support)
```

### 4️⃣ Task
```
Entity: com.easy.bpm.model.task.Task
Table:  task (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/task/Task.kt
Columns: id(PK), process_instance_id, title, node_id, assignee, status, created_at, completed_at, form_id
FK:     None declared (form_id is Long field only)
Status: ✅ MAPPED (but FK not declared in entity)
```

### 5️⃣ ProcessVariable
```
Entity: com.easy.bpm.model.variable.ProcessVariable
Table:  process_variable (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/variable/ProcessVariable.kt
Columns: id(PK), process_instance_id, name, value(JSONB), created_at, updated_at
FK:     None declared (process_instance_id is Long field only)
Status: ✅ MAPPED (but FK not declared in entity)
```

### 6️⃣ TaskVariable
```
Entity: com.easy.bpm.model.variable.TaskVariable
Table:  task_variable (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/variable/TaskVariable.kt
Columns: id(PK), task_id, name, value(JSONB)
FK:     None declared (task_id is Long field only)
Status: ✅ MAPPED (but FK not declared in entity)
```

### 7️⃣ CallActivityMapping
```
Entity: com.easy.bpm.model.process.CallActivityMapping
Table:  call_activity_mapping (EXPLICIT @Table annotation)
File:   src/main/kotlin/com/easy/bpm/model/process/CallActivityMapping.kt
Columns: id(PK), parent_instance_id, child_instance_id, call_activity_node_id,
          input_mappings(JSONB), output_mappings(JSONB), propagate_all_variables,
          created_at, updated_at
FK:     None declared (but implicit references to process_instance)
Indexes: 3 indexes on parent_id, child_id, call_activity_node_id
UK:     unique_call_activity_mapping on (parent_instance_id, child_instance_id, call_activity_node_id)
Status: ✅ COMPLETE (Phase 7: Call Activity support)
```

### 8️⃣ MessageSubscription
```
Entity: com.easy.bpm.model.message.MessageSubscription
Table:  message_subscription (DEFAULT)
File:   src/main/kotlin/com/easy/bpm/model/message/MessageSubscription.kt
Columns: id(PK), process_instance_id, node_id, message_name, correlation_key, status,
          message_payload(JSONB), timeout_at, created_at, received_at
FK:     None declared
UK:     Implicit in database: (process_instance_id, node_id) but not in @Table
Status: ✅ MAPPED (missing @Table unique constraint annotation)
```

### 9️⃣ WorkerRequest
```
Entity: com.easy.bpm.model.worker.WorkerRequest
Table:  worker_request (EXPLICIT @Table annotation)
File:   src/main/kotlin/com/easy/bpm/model/worker/WorkerRequest.kt
Columns: id(PK), process_instance_id, node_id, idempotency_key(UNIQUE),
          retry_count, status, last_error, created_at, last_attempt_at, completed_at
FK:     None declared
Indexes: 3 indexes on idempotency_key, (process_instance_id, node_id), status
UK:     idempotency_key (unique)
Status: ✅ COMPLETE
```

---

## Missing Entities (Phase 8.1 - Code Task Support)

### ❌ CodeTaskJar
```
Database Table: code_task_jar (exists via V20 migration)
Entity:         ❌ NOT DEFINED
Columns:        id(PK), content(BYTEA), file_name, file_hash(UNIQUE), upload_date, uploaded_by, description
Status:         BLOCKING - Phase 8.2 will fail without this entity
Action:         Create JPA entity class (see SQL_ERROR_RISK_ASSESSMENT.md)
```

### ❌ CodeClassMetadata
```
Database Table: code_class_metadata (exists via V20 migration)
Entity:         ❌ NOT DEFINED
Columns:        id(PK), jar_id(FK), class_name, method_name, method_signature, input_params(JSONB), return_type, created_at
Status:         BLOCKING - Phase 8.2 will fail without this entity
Action:         Create JPA entity class (see SQL_ERROR_RISK_ASSESSMENT.md)
```

### ❌ CodeTaskExecution
```
Database Table: code_task_execution (exists via V20 migration)
Entity:         ❌ NOT DEFINED
Columns:        id(PK), instance_id, node_id, jar_id(FK), class_name, method_name,
                input_variables(JSONB), output_variables(JSONB), execution_time_ms, status, error_message, executed_at
Status:         BLOCKING - Phase 8.2 will fail without this entity
Action:         Create JPA entity class (see SQL_ERROR_RISK_ASSESSMENT.md)
```

---

## Column Type Reference

### JSONB Columns (Use @JdbcTypeCode or @Type)
- Form.schema
- ProcessDefinition.definitionJson
- ProcessInstance.currentNode, nodeHistory
- ProcessVariable.value
- TaskVariable.value
- CallActivityMapping.inputMappings, outputMappings
- MessageSubscription.messagePayload
- CodeClassMetadata.inputParams
- CodeTaskExecution.inputVariables, outputVariables

### Enum Columns (Use @Enumerated(STRING))
- ProcessInstance.status → ProcessStatus
- Task.status → TaskStatus
- MessageSubscription.status → MessageSubscriptionStatus
- WorkerRequest.status → WorkerRequestStatus
- CodeTaskExecution.status → String (no enum yet)

### String Columns with Length Restrictions
- ProcessDefinition.process_id: VARCHAR(255)
- ProcessInstance.call_activity_node_id: VARCHAR(255)
- ProcessInstance.completion_node_id: VARCHAR(255)
- CallActivityMapping.call_activity_node_id: VARCHAR(255)
- WorkerRequest.node_id: VARCHAR(255)
- MessageSubscription.node_id: VARCHAR(255)
- Task.node_id: VARCHAR(255)
- WorkerRequest.last_error: VARCHAR(1000)

### Timestamp Columns (Use LocalDateTime)
- Form.created_at
- ProcessDefinition: none (use version instead)
- ProcessInstance.created_at, updated_at
- Task.created_at, completed_at
- ProcessVariable.created_at, updated_at
- CallActivityMapping.created_at, updated_at
- MessageSubscription.created_at, received_at
- WorkerRequest.created_at, last_attempt_at, completed_at
- CodeTaskJar.upload_date
- CodeClassMetadata.created_at
- CodeTaskExecution.executed_at

---

## Foreign Key Summary

### Declared in Entity (@ManyToOne or @JoinColumn)
- ✅ ProcessInstance.processDefinition → ProcessDefinition

### NOT Declared in Entity (exist in database only)
- ❌ ProcessVariable.processInstanceId → ProcessInstance.id
- ❌ TaskVariable.taskId → Task.id
- ❌ Task.formId → Form.id
- ❌ Task.processInstanceId → ProcessInstance.id
- ❌ MessageSubscription.processInstanceId → ProcessInstance.id
- ❌ WorkerRequest.processInstanceId → ProcessInstance.id
- ❌ CallActivityMapping.parentInstanceId → ProcessInstance.id
- ❌ CallActivityMapping.childInstanceId → ProcessInstance.id
- ❌ CodeClassMetadata.jar_id → CodeTaskJar.id
- ❌ CodeTaskExecution.jar_id → CodeTaskJar.id

**Impact**: No Hibernate cascading for these relationships; service layer must handle manually.

---

## Unique Constraints Summary

### Table-Level Unique Constraints

| Table | Constraint | Columns | In Entity Annotation | In Database |
|---|---|---|---|---|
| form | uk_form_form_id_version | (form_id, version) | ❌ | ✅ |
| worker_request | idempotency_key | (idempotency_key) | ✅ | ✅ |
| call_activity_mapping | unique_call_activity_mapping | (parent_instance_id, child_instance_id, call_activity_node_id) | ✅ | ✅ |
| message_subscription | (implicit) | (process_instance_id, node_id) | ❌ | ✅ |
| code_task_jar | jar_file_hash_unique | (file_hash) | ❌ (no entity) | ✅ |
| code_class_metadata | uk_code_class_metadata | (jar_id, class_name, method_name) | ❌ (no entity) | ✅ |

**⚠️ Note**: message_subscription unique constraint exists in database but not declared in @Table

---

## Index Summary

### Table Indexes

| Table | Index Name | Columns | Purpose |
|---|---|---|---|
| form | uk_form_form_id_version | (form_id, version) | Unique lookup by form_id + version |
| process_instance | idx_process_instance_parent_id | (parent_instance_id) | Find children of parent |
| process_instance | idx_process_instance_call_activity_node | (call_activity_node_id) | Find node that triggered subprocess |
| process_instance | idx_process_instance_nesting_level | (nesting_level) | Filter by nesting depth |
| call_activity_mapping | idx_call_activity_mapping_parent_id | (parent_instance_id) | Find mappings by parent |
| call_activity_mapping | idx_call_activity_mapping_child_id | (child_instance_id) | Find mapping by child |
| call_activity_mapping | idx_call_activity_mapping_call_activity_node | (call_activity_node_id) | Find mapping by node |
| message_subscription | idx_message_subscription_lookup | (message_name, correlation_key, status) | Fast message arrival lookup |
| message_subscription | idx_message_subscription_timeout | (timeout_at) WHERE status='AWAITING' | Timeout processing |
| worker_request | idx_worker_request_idempotency_key | (idempotency_key) | Idempotency deduplication |
| worker_request | idx_worker_request_process_node | (process_instance_id, node_id) | Find requests by instance+node |
| worker_request | idx_worker_request_status | (status) | Find pending/failed requests |
| code_task_jar | idx_code_task_jar_file_hash | (file_hash) | Lookup JAR by hash |
| code_task_jar | idx_code_task_jar_upload_date | (upload_date) | List recent uploads |
| code_class_metadata | idx_code_class_jar_id | (jar_id) | Find classes in JAR |
| code_class_metadata | idx_code_class_name | (class_name) | Lookup by class name |
| code_class_metadata | idx_code_class_method_name | (method_name) | Lookup by method name |
| code_task_execution | idx_code_execution_instance_id | (instance_id) | Audit trail by instance |
| code_task_execution | idx_code_execution_jar_id | (jar_id) | Trace JAR execution |
| code_task_execution | idx_code_execution_status | (status) | Find failed executions |
| code_task_execution | idx_code_execution_executed_at | (executed_at DESC) | Recent execution queries |

**⚠️ Entities don't declare indexes** - only in database via Flyway migrations

---

## Nullable Columns

### NOT NULL Columns
- id (all tables)
- form: form_id, name, schema, version, created_at
- process_definition: process_id, definition_json, version
- process_instance: process_definition_id, status, created_at, updated_at, nesting_level
- task: process_instance_id, node_id, status, created_at
- process_variable: process_instance_id, name, value, created_at, updated_at
- task_variable: task_id, name, value
- call_activity_mapping: parent_instance_id, child_instance_id, call_activity_node_id, input_mappings, output_mappings, propagate_all_variables, created_at, updated_at
- message_subscription: process_instance_id, node_id, message_name, correlation_key, status, created_at
- worker_request: process_instance_id, node_id, idempotency_key, status, created_at
- code_task_jar: content, file_name, file_hash, upload_date
- code_class_metadata: jar_id, class_name, method_name, created_at
- code_task_execution: instance_id, executed_at

### NULLABLE Columns (Can Be NULL)
- form: none
- process_definition: process_name, description
- process_instance: current_nodes, parent_instance_id, call_activity_node_id, completion_node_id
- task: title, assignee, completed_at, form_id
- process_variable: none (all NOT NULL)
- task_variable: none (all NOT NULL)
- call_activity_mapping: none (all NOT NULL)
- message_subscription: message_payload, timeout_at, received_at
- worker_request: last_error, last_attempt_at, completed_at
- code_task_jar: uploaded_by, description
- code_class_metadata: method_signature, input_params, return_type
- code_task_execution: node_id, jar_id, class_name, method_name, input_variables, output_variables, execution_time_ms, status, error_message

---

## Phase Information

### Phase 7 Additions (Call Activity/Subprocess Support)
- ProcessInstance: parent_instance_id, call_activity_node_id, nesting_level, completion_node_id
- NEW TABLE: CallActivityMapping

### Phase 8.1 Additions (Code Task Support - Backend Only)
- NEW TABLES: code_task_jar, code_class_metadata, code_task_execution
- ❌ No entities defined yet

---

## Default Values in Entities

| Entity.Column | Default Value | Notes |
|---|---|---|
| Form.version | 1 | Managed value |
| Form.createdAt | LocalDateTime.now() | Set at creation |
| ProcessDefinition.version | 1 | Managed value |
| ProcessInstance.nesting_level | 0 | Subprocess depth |
| ProcessInstance.nodeHistory | emptyList() | Starts empty |
| Task.status | TaskStatus.PENDING | Initial state |
| CallActivityMapping.inputMappings | "{}" | Empty JSON object |
| CallActivityMapping.outputMappings | "{}" | Empty JSON object |
| CallActivityMapping.propagateAllVariables | false | Explicit mapping required |
| MessageSubscription.status | MessageSubscriptionStatus.AWAITING | Initial state |
| WorkerRequest.retryCount | 0 | Starts at zero |
| WorkerRequest.status | WorkerRequestStatus.PENDING | Initial state |

---

## Database Dialect

**Target**: PostgreSQL 12+  
**H2 Compatibility**: YES (tests use H2 with PostgreSQL mode)  
**JSON Support**: JSONB (PostgreSQL native)  
**Enum Support**: VARCHAR(50) with application-level enum mapping

---

## Generated: 2026-04-23

**Related Documents**:
- `ENTITY_SCHEMA_MAPPING.md` - Full detailed mapping
- `SQL_ERROR_RISK_ASSESSMENT.md` - Risk analysis and mitigation
- `src/main/resources/db/migration/` - Flyway migration files (21 total)
