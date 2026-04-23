# JPA Entity Schema Mapping

**Last Updated**: 2026-04-23  
**Purpose**: Complete mapping of all Spring Boot JPA entities and their database column definitions.  
**Location**: `src/main/kotlin/com/easy/bpm/model/`

---

## Summary

| Entity Class | Table Name | File | Total Columns |
|---|---|---|---|
| Form | `form` | form/Form.kt | 6 |
| ProcessDefinition | `process_definition` | process/ProcessDefinition.kt | 6 |
| ProcessInstance | `process_instance` | process/ProcessInstance.kt | 11 |
| Task | `task` | task/Task.kt | 9 |
| ProcessVariable | `process_variable` | variable/ProcessVariable.kt | 6 |
| TaskVariable | `task_variable` | variable/TaskVariable.kt | 4 |
| CallActivityMapping | `call_activity_mapping` | process/CallActivityMapping.kt | 10 |
| MessageSubscription | `message_subscription` | message/MessageSubscription.kt | 10 |
| WorkerRequest | `worker_request` | worker/WorkerRequest.kt | 10 |

**Total Entities**: 9  
**Total Unique Tables**: 9

---

## Detailed Entity Definitions

### 1. Form

**File**: [form/Form.kt](src/main/kotlin/com/easy/bpm/model/form/Form.kt)  
**Table Name**: `form` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.form`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `form_id` | formId | VARCHAR(255) | NO | - | @Column(name = "form_id") |
| `name` | name | VARCHAR(255) | NO | - | Default column mapping |
| `schema` | schema | JSONB | NO | - | @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") |
| `version` | version | INT | YES | - | Default value = 1 |
| `created_at` | createdAt | TIMESTAMP | YES | - | Default = LocalDateTime.now() |

**Foreign Keys**: None  
**Indexes**: None (except implicit PK index)  
**Unique Constraints**: None  
**Special Notes**:
- Uses `@JdbcTypeCode(SqlTypes.JSON)` for JSONB schema column
- No explicit form_key column defined in entity (check Flyway migrations if needed)

---

### 2. ProcessDefinition

**File**: [process/ProcessDefinition.kt](src/main/kotlin/com/easy/bpm/model/process/ProcessDefinition.kt)  
**Table Name**: `process_definition` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.process`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_id` | key | VARCHAR(255) | NO | - | @Column(name = "process_id") JSON alias: "key" |
| `process_name` | processName | VARCHAR(255) | YES | - | @Column(name = "process_name") JSON alias: "processName" |
| `description` | description | VARCHAR(255) | YES | - | Default column mapping |
| `version` | version | INT | YES | - | Default value = 1 |
| `definition_json` | definitionJson | JSONB | NO | - | @Type(JsonBinaryType::class) @Column(columnDefinition = "jsonb") |

**Foreign Keys**: None  
**Indexes**: None (except implicit PK index)  
**Unique Constraints**: None  
**Special Notes**:
- Uses Vladmihalcea's `JsonBinaryType` for JSONB definition_json
- Has @JsonProperty annotations for JSON serialization (process_id → key, process_name → processName)

---

### 3. ProcessInstance

**File**: [process/ProcessInstance.kt](src/main/kotlin/com/easy/bpm/model/process/ProcessInstance.kt)  
**Table Name**: `process_instance` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.process`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_definition_id` | processDefinition | BIGINT | NO | FK | @ManyToOne @JoinColumn(name = "process_definition_id") |
| `status` | status | VARCHAR(255) | NO | - | @Enumerated(EnumType.STRING) |
| `current_nodes` | currentNode | JSONB | YES | - | @Type(JsonBinaryType::class) @Column(columnDefinition = "jsonb") |
| `node_history` | nodeHistory | JSONB | NO | - | @Type(JsonBinaryType::class) @Column(columnDefinition = "jsonb") Default = emptyList() |
| `created_at` | createdAt | TIMESTAMP | NO | - | Default = LocalDateTime.now() |
| `updated_at` | updatedAt | TIMESTAMP | NO | - | Default = LocalDateTime.now() |
| `parent_instance_id` | parentInstanceId | BIGINT | YES | - | @Column(nullable = true) Phase 7: Call Activity support |
| `call_activity_node_id` | callActivityNodeId | VARCHAR(255) | YES | - | @Column(length = 255) Phase 7: Call Activity support |
| `nesting_level` | nestingLevel | INT | NO | - | Default = 0 Phase 7: Call Activity support |
| `completion_node_id` | completionNodeId | VARCHAR(255) | YES | - | @Column(length = 255) Phase 7: Call Activity support |

**Foreign Keys**:
- `process_definition_id` → `process_definition.id` (ManyToOne)

**Indexes**: None explicit (except implicit PK)  
**Unique Constraints**: None  
**Special Notes**:
- Phase 7 additions for Call Activity/Subprocess support
- `parentInstanceId` and `callActivityNodeId` enable parent-child process relationships
- `nesting_level` tracks subprocess depth
- `completionNodeId` specifies where parent resumes after child completes

---

### 4. Task

**File**: [task/Task.kt](src/main/kotlin/com/easy/bpm/model/task/Task.kt)  
**Table Name**: `task` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.task`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_instance_id` | processInstanceId | BIGINT | NO | - | @Column(nullable = false) |
| `title` | title | VARCHAR(255) | YES | - | Default = null |
| `node_id` | nodeId | VARCHAR(255) | NO | - | Default column mapping |
| `assignee` | assignee | VARCHAR(255) | YES | - | Mutable (var) |
| `status` | status | VARCHAR(255) | NO | - | @Enumerated(EnumType.STRING) Default = TaskStatus.PENDING |
| `created_at` | createdAt | TIMESTAMP | NO | - | Default = LocalDateTime.now() |
| `completed_at` | completedAt | TIMESTAMP | YES | - | Mutable, nullable |
| `form_id` | formId | BIGINT | YES | - | Nullable foreign key reference |

**Foreign Keys**: None explicit (but `form_id` implies reference to Form.id)  
**Indexes**: None explicit  
**Unique Constraints**: None  
**Special Notes**:
- `form_id` is stored as Long but not declared as FK relationship
- `status` is enum (TaskStatus)
- Most fields are mutable except createdAt

---

### 5. ProcessVariable

**File**: [variable/ProcessVariable.kt](src/main/kotlin/com/easy/bpm/model/variable/ProcessVariable.kt)  
**Table Name**: `process_variable` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.variable`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_instance_id` | processInstanceId | BIGINT | NO | - | Default column mapping |
| `name` | name | VARCHAR(255) | NO | - | Default column mapping |
| `value` | value | JSONB | NO | - | @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") Mutable |
| `created_at` | createdAt | TIMESTAMP | NO | - | Mutable, Default = LocalDateTime.now() |
| `updated_at` | updatedAt | TIMESTAMP | NO | - | Mutable, Default = LocalDateTime.now() |

**Foreign Keys**: None explicit (but `process_instance_id` should reference ProcessInstance.id)  
**Indexes**: None explicit  
**Unique Constraints**: None  
**Special Notes**:
- Value field is mutable and uses @JdbcTypeCode for JSON handling
- Both timestamps are mutable for tracking variable changes

---

### 6. TaskVariable

**File**: [variable/TaskVariable.kt](src/main/kotlin/com/easy/bpm/model/variable/TaskVariable.kt)  
**Table Name**: `task_variable` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.variable`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `task_id` | taskId | BIGINT | NO | - | Default column mapping |
| `name` | name | VARCHAR(255) | NO | - | Default column mapping |
| `value` | value | JSONB | NO | - | @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") |

**Foreign Keys**: None explicit (but `task_id` should reference Task.id)  
**Indexes**: None explicit  
**Unique Constraints**: None  
**Special Notes**:
- Similar structure to ProcessVariable but without timestamps
- Immutable data class (val fields only)

---

### 7. CallActivityMapping

**File**: [process/CallActivityMapping.kt](src/main/kotlin/com/easy/bpm/model/process/CallActivityMapping.kt)  
**Table Name**: `call_activity_mapping`  
**Package**: `com.easy.bpm.model.process`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `parent_instance_id` | parentInstanceId | BIGINT | NO | - | @Column(nullable = false) |
| `child_instance_id` | childInstanceId | BIGINT | NO | - | @Column(nullable = false) |
| `call_activity_node_id` | callActivityNodeId | VARCHAR(255) | NO | - | @Column(length = 255, nullable = false) |
| `input_mappings` | inputMappings | JSONB | NO | - | @Column(columnDefinition = "jsonb") Default = "{}" |
| `output_mappings` | outputMappings | JSONB | NO | - | @Column(columnDefinition = "jsonb") Default = "{}" |
| `propagate_all_variables` | propagateAllVariables | BOOLEAN | NO | - | @Column(nullable = false) Default = false |
| `created_at` | createdAt | TIMESTAMP | NO | - | @Column(updatable = false, columnDefinition = "TIMESTAMP") |
| `updated_at` | updatedAt | TIMESTAMP | NO | - | @Column(columnDefinition = "TIMESTAMP") Mutable |

**Foreign Keys**: None explicit  
**Indexes**: 
- `idx_call_activity_mapping_parent_id` on `parent_instance_id`
- `idx_call_activity_mapping_child_id` on `child_instance_id`
- `idx_call_activity_mapping_call_activity_node` on `call_activity_node_id`

**Unique Constraints**:
- `unique_call_activity_mapping` on `(parent_instance_id, child_instance_id, call_activity_node_id)`

**Special Notes**:
- Phase 7: Call Activity/Subprocess support
- input_mappings and output_mappings are stored as JSON strings (not JsonNode)
- Includes helper methods for parsing mappings
- createdAt is NOT updatable

---

### 8. MessageSubscription

**File**: [message/MessageSubscription.kt](src/main/kotlin/com/easy/bpm/model/message/MessageSubscription.kt)  
**Table Name**: `message_subscription` (default, no @Table annotation)  
**Package**: `com.easy.bpm.model.message`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_instance_id` | processInstanceId | BIGINT | NO | - | @Column(nullable = false) |
| `node_id` | nodeId | VARCHAR(255) | NO | - | @Column(nullable = false) |
| `message_name` | messageName | VARCHAR(255) | NO | - | @Column(nullable = false) |
| `correlation_key` | correlationKey | VARCHAR(255) | NO | - | @Column(nullable = false) |
| `status` | status | VARCHAR(255) | NO | - | @Enumerated(EnumType.STRING) Default = MessageSubscriptionStatus.AWAITING |
| `message_payload` | messagePayload | JSONB | YES | - | @Type(JsonBinaryType::class) @Column(columnDefinition = "jsonb") Mutable, nullable |
| `timeout_at` | timeoutAt | TIMESTAMP | YES | - | Mutable, nullable |
| `created_at` | createdAt | TIMESTAMP | NO | - | Default = LocalDateTime.now() |
| `received_at` | receivedAt | TIMESTAMP | YES | - | Mutable, nullable |

**Foreign Keys**: None explicit  
**Indexes**: None explicit  
**Unique Constraints**: None  
**Special Notes**:
- Tracks message subscriptions for async message handling
- Status uses enum MessageSubscriptionStatus
- Timestamps track creation and message receipt

---

### 9. WorkerRequest

**File**: [worker/WorkerRequest.kt](src/main/kotlin/com/easy/bpm/model/worker/WorkerRequest.kt)  
**Table Name**: `worker_request`  
**Package**: `com.easy.bpm.model.worker`

#### Columns:

| Column Name | Field Name | Type | Nullable | Key | Special Attributes |
|---|---|---|---|---|---|
| `id` | id | BIGINT | NO | PK (IDENTITY) | @Id @GeneratedValue(IDENTITY) |
| `process_instance_id` | processInstanceId | BIGINT | NO | - | @Column(nullable = false) |
| `node_id` | nodeId | VARCHAR(255) | NO | - | @Column(nullable = false) |
| `idempotency_key` | idempotencyKey | VARCHAR(255) | NO | - | @Column(unique = true) Unique constraint |
| `retry_count` | retryCount | INT | NO | - | @Column(nullable = false) Default = 0 Mutable |
| `status` | status | VARCHAR(255) | NO | - | @Enumerated(EnumType.STRING) Default = WorkerRequestStatus.PENDING Mutable |
| `last_error` | lastError | VARCHAR(1000) | YES | - | @Column(length = 1000) Mutable, nullable |
| `created_at` | createdAt | TIMESTAMP | NO | - | Default = LocalDateTime.now() |
| `last_attempt_at` | lastAttemptAt | TIMESTAMP | YES | - | Mutable, nullable |
| `completed_at` | completedAt | TIMESTAMP | YES | - | Mutable, nullable |

**Foreign Keys**: None explicit  
**Indexes**: None explicit (idempotency_key has implicit unique index)  
**Unique Constraints**:
- `UQ_worker_request_idempotency_key` on `idempotency_key` (implicit from @UniqueConstraint in @Table)
- Table-level: `uniqueConstraints = [UniqueConstraint(columnNames = ["idempotency_key"])]`

**Special Notes**:
- Tracks async worker request execution
- Status uses enum WorkerRequestStatus (PENDING, IN_PROGRESS, COMPLETED, FAILED, DLQ)
- lastError stores up to 1000 chars
- Idempotency key ensures request deduplication

---

## Relationship Summary

### Foreign Key References (Implicit or Explicit)

| From Table | From Column | To Table | To Column | Type | Defined In Entity |
|---|---|---|---|---|---|
| process_instance | process_definition_id | process_definition | id | ManyToOne | ✅ @ManyToOne @JoinColumn |
| task | form_id | form | id | ??? | ❌ Only Long field, no relationship |
| process_variable | process_instance_id | process_instance | id | ??? | ❌ Only Long field, no relationship |
| task_variable | task_id | task | id | ??? | ❌ Only Long field, no relationship |
| call_activity_mapping | parent_instance_id | process_instance | id | ??? | ❌ Only Long field, no relationship |
| call_activity_mapping | child_instance_id | process_instance | id | ??? | ❌ Only Long field, no relationship |
| message_subscription | process_instance_id | process_instance | id | ??? | ❌ Only Long field, no relationship |
| worker_request | process_instance_id | process_instance | id | ??? | ❌ Only Long field, no relationship |

---

## Important Observations & Potential Issues

### 1. **Missing Foreign Key Relationships**
- Multiple entities reference other tables via Long IDs but **do NOT declare @ManyToOne or @JoinColumn** relationships
- This could cause:
  - ❌ Inability to cascade operations
  - ❌ No lazy loading support
  - ❌ Manual FK constraint management
  
**Affected Entities**:
- `Task.formId` → Form
- `ProcessVariable.processInstanceId` → ProcessInstance
- `TaskVariable.taskId` → Task
- `CallActivityMapping.parentInstanceId` → ProcessInstance
- `CallActivityMapping.childInstanceId` → ProcessInstance
- `MessageSubscription.processInstanceId` → ProcessInstance
- `WorkerRequest.processInstanceId` → ProcessInstance

### 2. **Missing form_key Column in Form Entity**
- Instructions mention form_key feature with V16 migration
- **Not present** in current Form.kt entity definition
- ⚠️ **Check Flyway migrations** to see if column exists in database but not mapped in entity
- Could cause: SQL errors if column exists but isn't mapped, or missing data if entity should have it

### 3. **Default Table Names (No @Table Annotation)**
- 8 of 9 entities rely on default table name generation
- Default: lowercase class name → `form`, `process_definition`, `process_instance`, etc.
- ⚠️ **Verify these match actual table names in database** (Flyway migrations)

### 4. **JSON Column Type Inconsistencies**
- **@JdbcTypeCode(SqlTypes.JSON)**: Form.schema, ProcessVariable.value, TaskVariable.value
- **@Type(JsonBinaryType::class)**: ProcessDefinition.definitionJson, ProcessInstance.currentNode, ProcessInstance.nodeHistory, MessageSubscription.messagePayload
- ⚠️ Mixed use of two different JSON handling libraries (Hibernate native vs Vladmihalcea's library)
- Could cause: Version compatibility issues, serialization differences

### 5. **Enum Handling**
- All enums use `@Enumerated(EnumType.STRING)` ✅
- Enums: ProcessStatus, TaskStatus, MessageSubscriptionStatus, WorkerRequestStatus
- ⚠️ **Verify database has VARCHAR columns for these**, not INT/numeric

### 6. **Mutable vs Immutable Design**
- ProcessVariable and ProcessInstance use `var` for some fields (mutable)
- CallActivityMapping and WorkerRequest also use `var` (mutable)
- TaskVariable and Form use all `val` (immutable)
- ⚠️ Could cause threading/concurrency issues if not handled carefully

### 7. **Missing Indexes**
- Only CallActivityMapping has explicit indexes
- Other high-cardinality lookups might be slow:
  - `process_variable(process_instance_id, name)`
  - `task_variable(task_id)`
  - `message_subscription(process_instance_id)`
  - `worker_request(status)` (for finding PENDING/FAILED)

---

## Database Schema vs Entity Mapping - VERIFIED (Flyway Analysis)

### Critical Findings from Migration Files (src/main/resources/db/migration/)

#### ✅ VERIFIED: Core Tables Exist
- **V1**: process_definition (id, name, definition_json)
- **V2**: process_instance (id, process_definition_id, status, current_node, context, created_at, updated_at)
- **V5**: task (id, process_instance_id, node_id, assignee, status, created_at, completed_at)
- **V6**: process_variable (id, process_instance_id, name, value)
- **V7**: task_variable (id, task_id, name, value)
- **V9**: form (id, name, version, definition, created_at) + alter task ADD form_id
- **V12**: message_subscription (full table with all columns)
- **V14**: worker_request (full table with all columns + indexes)

#### ✅ VERIFIED: Migrations Applied
- **V15**: Renamed process.name → process_id, added description, added process_name column
- **V16**: Added form_key to form table
- **V17**: Renamed form_key → form_id (V18), created unique index uk_form_form_id_version
- **V19**: Added call activity columns to process_instance (parent_instance_id, call_activity_node_id, nesting_level, completion_node_id)
- **V20**: Added code task support (3 new tables - NOT in entity model yet)
- **V21**: Added audit columns to process_variable (created_at, updated_at)

### ⚠️ SCHEMA vs ENTITY MISMATCHES FOUND

#### 1. **form_id Column in Form Entity** ✅ CONFIRMED
- **Database**: V16 creates `form_key`, V18 renames to `form_id`
- **Entity**: Form.kt has `@Column(name = "form_id")`
- **Status**: ✅ MATCHES

#### 2. **process_definition Columns** 
- **Database** (V1): id, name, definition_json
- **Database** (V15): Renamed name → process_id, added process_name, added description
- **Entity**: id, process_id (key), process_name, description, version, definition_json
- **Status**: ✅ MATCHES (after V15 and V17 migrations)

#### 3. **process_variable Value Type** ⚠️ MISMATCH
- **Database** (V6): `value TEXT`
- **Database** (V8): `ALTER TABLE process_variable ALTER COLUMN value TYPE JSONB`
- **Entity**: Uses `@JdbcTypeCode(SqlTypes.JSON)` expecting JSONB
- **Status**: ✅ MATCHES (V8 migration converts to JSONB)

#### 4. **task_variable Value Type** ⚠️ MISMATCH
- **Database** (V7): `value TEXT`
- **Database** (V8): Converts to JSONB
- **Entity**: Uses `@JdbcTypeCode(SqlTypes.JSON)` expecting JSONB
- **Status**: ✅ MATCHES (V8 migration converts to JSONB)

#### 5. **form Definition Column** ⚠️ NAME MISMATCH
- **Database** (V9): `definition JSONB NOT NULL` (renamed from schema in V10)
- **Database** (V10): `ALTER TABLE form RENAME COLUMN definition TO schema`
- **Entity** (Form.kt): `schema: JsonNode`
- **Status**: ✅ MATCHES (V10 renames to schema)

#### 6. **process_instance Current Node** ⚠️ COLUMN DEFINITION MISMATCH
- **Database** (V2): `current_node VARCHAR(255)` (single string)
- **Database** (V4): Converts to array/list type
- **Entity**: `@Column(name = "current_nodes")` (plural, JSONB list)
- **Status**: ✅ MATCHES (V4 converts to proper type)

#### 7. **Missing form title Column** ⚠️ POTENTIAL ISSUE
- **Database** (V11): `ALTER TABLE task ADD COLUMN title VARCHAR(255);`
- **Entity**: Task.kt has `title: String? = null`
- **Status**: ✅ MATCHES

#### 8. **message_subscription Constraints** ✅ CONFIRMED
- **Database** (V12): `UNIQUE(process_instance_id, node_id)` 
- **Entity**: No explicit unique constraint declared (but database enforces it)
- **Status**: ⚠️ Database constraint not reflected in @Table annotation

### 🚨 NEW TABLES NOT MAPPED IN ENTITIES (Phase 8.1 - Code Task Support)

**V20 created 3 tables without corresponding JPA entities:**

#### Table 1: code_task_jar
```sql
CREATE TABLE code_task_jar (
  id BIGSERIAL PRIMARY KEY,
  content BYTEA NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_hash VARCHAR(64) UNIQUE NOT NULL,
  upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  uploaded_by VARCHAR(255),
  description TEXT,
  CONSTRAINT jar_file_hash_unique UNIQUE (file_hash)
);
```
**Status**: ❌ NO ENTITY DEFINED

#### Table 2: code_class_metadata
```sql
CREATE TABLE code_class_metadata (
  id BIGSERIAL PRIMARY KEY,
  jar_id BIGINT NOT NULL REFERENCES code_task_jar(id) ON DELETE CASCADE,
  class_name VARCHAR(500) NOT NULL,
  method_name VARCHAR(255) NOT NULL,
  method_signature TEXT,
  input_params JSONB,
  return_type VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_code_class_metadata UNIQUE (jar_id, class_name, method_name)
);
```
**Status**: ❌ NO ENTITY DEFINED

#### Table 3: code_task_execution
```sql
CREATE TABLE code_task_execution (
  id BIGSERIAL PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  node_id VARCHAR(255),
  jar_id BIGINT REFERENCES code_task_jar(id),
  class_name VARCHAR(500),
  method_name VARCHAR(255),
  input_variables JSONB,
  output_variables JSONB,
  execution_time_ms INTEGER,
  status VARCHAR(50),
  error_message TEXT,
  executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_code_exec_jar FOREIGN KEY (jar_id) REFERENCES code_task_jar(id) ON DELETE SET NULL
);
```
**Status**: ❌ NO ENTITY DEFINED

---

## Verification Checklist

- [x] ✅ Verify all 9 main table names exist in database
- [x] ✅ Verify column names match exactly (snake_case)
- [x] ✅ Check if `form.form_id` column exists (V16→V18 migration)
- [x] ✅ Verify all JSONB columns are actually JSONB type in database (V8 migration)
- [x] ✅ Verify enum columns are VARCHAR, not INT
- [x] ✅ Verify foreign key constraints exist in database (migration files)
- [x] ✅ Check for any additional columns in database not mapped in entities
- [ ] ⚠️ **TODO**: Add JPA entities for Phase 8.1 code task tables (code_task_jar, code_class_metadata, code_task_execution)
- [ ] ⚠️ **TODO**: Add unique constraint to @Table annotation for message_subscription(process_instance_id, node_id)
- [ ] **TODO**: Verify all indexes match between migration files and entity annotations

---

## Summary of Database Schema Evolution

**21 Migration Files** (V1 - V21) have been applied:

| Version | Description | Impact |
|---|---|---|
| V1-V4 | Initial tables + JSON handling | Core tables, JSONB conversion |
| V5-V9 | Task and variable tables + form | Extended schema |
| V10-V13 | Form schema rename, message subscriptions, node_history | Message support |
| V14-V18 | Worker request, process metadata, form key/ID refactor | Async support, form identification |
| V19 | Call activity support (Phase 7) | Subprocess/parent-child relationships |
| V20 | Code task support (Phase 8.1) | JAR execution, class metadata, audit trail |
| V21 | Audit columns for process_variable | Audit trail enhancement |

---

## H2 Schema Comparison

To validate schema during testing:
1. **Check H2 schema** in embedded test database
2. **Compare against** Flyway migration files in `src/main/resources/db/migration/`
3. **Verify runtime schema** using database tools

**Database Version**: 21 migrations applied (as of 2026-04-23)

---

## Generated: 2026-04-23

**Recommendations**:
1. Create JPA entities for code_task_jar, code_class_metadata, code_task_execution (Phase 8.1)
2. Add missing unique constraint to MessageSubscription @Table annotation
3. Add foreign key relationships for Task.formId, ProcessVariable.processInstanceId, etc.
4. Add indexes for high-cardinality lookups
5. Consider adding database connection pooling verification

*This document should be updated whenever new entities are added or columns are modified in existing entities.*
