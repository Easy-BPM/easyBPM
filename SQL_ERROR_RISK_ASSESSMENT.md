# SQL/Schema Error Risk Assessment

**Generated**: 2026-04-23  
**Purpose**: Identify potential SQL errors and schema mismatches that could occur during Spring context initialization or runtime.

---

## Executive Summary

✅ **Good News**: The 9 JPA entities are **CORRECTLY MAPPED** to the database schema.  
⚠️ **Caution**: 3 additional database tables (Phase 8.1 code task support) **DO NOT HAVE CORRESPONDING ENTITIES**.  
❌ **Risk**: Missing entity-to-table mappings could cause Spring Data JPA queries to fail.

---

## Potential Runtime Errors

### 1. **Spring Context Initialization Failure** (Low Risk)

**Scenario**: If Hibernate tries to sync entities to schema before Flyway migrations run.

**Possible Error**:
```
org.hibernate.tool.schema.spi.CommandAcceptanceException: 
Error executing DDL via import script 'import.sql': 
Error executing DDL "CREATE TABLE form ..." 
[Column list mismatch between entity and database]
```

**Why It Won't Happen**:
- ✅ Flyway migrations run **before** Hibernate schema validation (spring.jpa.hibernate.ddl-auto = validate)
- ✅ All Flyway migrations (V1-V21) have been applied
- ✅ Entity definitions match final database schema

### 2. **JPA Query Failures for Code Task Operations** (MEDIUM Risk)

**Scenario**: Phase 8.1 backend REST controller tries to use repositories for code task tables.

**Possible Error**:
```
org.springframework.beans.factory.BeanCreationException:
Error creating bean 'codeTaskJarRepository':
'CodeTaskJarRepository' is an invalid interface - RepositoryInterface 
does not inherit from 'Repository'
OR
No PersistenceProvider found for repository interface 'CodeTaskJarRepository'
```

**Why It Could Happen**:
- ❌ **CodeTaskJar entity NOT defined** in model directory
- ❌ **CodeTaskClassMetadata entity NOT defined** in model directory
- ❌ **CodeTaskExecution entity NOT defined** in model directory
- ✅ Database tables **DO EXIST** (created by V20 migration)

**Impact**: 
- Cannot use Spring Data JPA repositories for code task operations
- Must use raw SQL queries or JDBC
- Breaks Type Safety

**Status**: 🔴 **BLOCKING ISSUE for Phase 8.2 Modeler integration if using Spring Data repositories**

### 3. **Foreign Key Constraint Violations** (LOW Risk)

**Scenario**: Cascading deletes or updates fail unexpectedly.

**Possible Error**:
```
org.hibernate.engine.jdbc.spi.SqlExceptionHelper: 
Batch entry 0 string INSERT INTO process_variable (process_instance_id, name, value, created_at, updated_at) 
VALUES (999, 'key', '{}', NOW(), NOW()) was aborted: 
ERROR: insert or update on table "process_variable" violates foreign key constraint 
"fk_process_variable_process_instance"
```

**Why It Could Happen**:
- ⚠️ Foreign keys are **NOT declared** in many entities (only as Long fields)
- Hibernate doesn't know about cascade behaviors
- Manual enforcement required in service layer

**Current State**:
- ProcessInstance ↔ ProcessDefinition: ✅ **@ManyToOne declared** with cascade
- ProcessVariable ↔ ProcessInstance: ❌ **No @ManyToOne** (only Long field)
- TaskVariable ↔ Task: ❌ **No @ManyToOne** (only Long field)
- Task ↔ Form: ❌ **No @ManyToOne** (only Long field)
- MessageSubscription ↔ ProcessInstance: ❌ **No @ManyToOne** (only Long field)
- WorkerRequest ↔ ProcessInstance: ❌ **No @ManyToOne** (only Long field)

**Impact**: 
- 🟡 **Moderate** - Service layer handles FK constraints, but no ORM cascade support
- Type safety loss in entity relationships

### 4. **JSON Type Handling Inconsistency** (LOW Risk)

**Scenario**: Mixed use of two different JSON libraries could cause serialization issues.

**Current Implementation**:
```
// Hibernate @JdbcTypeCode (newer approach)
Form.schema, ProcessVariable.value, TaskVariable.value

// Vladmihalcea @Type(JsonBinaryType::class) (older approach)
ProcessDefinition.definitionJson, ProcessInstance.currentNode/nodeHistory, 
MessageSubscription.messagePayload, CallActivityMapping.inputMappings/outputMappings
```

**Possible Error**:
```
org.hibernate.type.SerializationException:
Could not deserialize class with ObjectInputStream while deserializing 
a TypedValue for entity [com.easy.bpm.model.process.ProcessDefinition]
```

**Current Risk**: 
- 🟢 **LOW** - Both libraries are compatible with Spring Boot 3.5.3
- No reports of serialization issues in test logs

**Recommendation**:
- Gradually migrate all to `@JdbcTypeCode` (Hibernate 6.2+)
- Or standardize on Vladmihalcea's library

### 5. **Enum Column Type Mismatch** (LOW Risk)

**Scenario**: Database column type doesn't match entity @Enumerated annotation.

**All Entities Using Enums**:
- ProcessInstance.status → ProcessStatus
- Task.status → TaskStatus
- MessageSubscription.status → MessageSubscriptionStatus
- WorkerRequest.status → WorkerRequestStatus

**Database Types**: All stored as `VARCHAR(50)` ✅

**Current Risk**: 🟢 **LOW** - All correctly configured with @Enumerated(EnumType.STRING)

### 6. **Column Definition Mismatches** (LOW Risk)

**Potential Issues**:

| Entity Field | Entity Type | Database Type | Status |
|---|---|---|---|
| Form.schema | JsonNode | JSONB | ✅ |
| ProcessDefinition.definitionJson | String | JSONB | ✅ |
| ProcessInstance.currentNode | List<String>? | JSONB | ✅ |
| ProcessVariable.value | JsonNode | JSONB | ✅ |
| TaskVariable.value | JsonNode | JSONB | ✅ |
| WorkerRequest.lastError | String? (1000 chars) | VARCHAR(1000) | ✅ |
| CallActivityMapping.createdAt | LocalDateTime | TIMESTAMP (NOT UPDATABLE) | ✅ |

**Current Risk**: 🟢 **LOW** - All match

---

## Configuration Verification Checklist

### Hibernate Configuration
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ✅ SHOULD BE - not create or update
    database-platform: org.hibernate.dialect.PostgreSQL12Dialect
    properties:
      hibernate.jdbc.batch_size: 20
      hibernate.order_inserts: true
      hibernate.order_updates: true
```

**Check**: Ensure `ddl-auto: validate` is set to prevent Hibernate from attempting to create/alter tables.

### Flyway Configuration
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
```

**Check**: Ensure Flyway runs **BEFORE** Hibernate validation.

---

## Specific SQL Error Scenarios

### Scenario A: New ProcessVariable Without Entity FK

**Current Query** (via Spring Data if FK wasn't declared):
```sql
-- Attempted by Hibernate
INSERT INTO process_variable (process_instance_id, name, value, created_at, updated_at) 
VALUES (?, ?, ?, NOW(), NOW());
```

**Database Constraint**:
```sql
-- V6 creates this implicitly via foreign key
ALTER TABLE process_variable ADD CONSTRAINT 
fk_process_variable_instance 
FOREIGN KEY (process_instance_id) REFERENCES process_instance(id);
```

**Will It Fail?**
- ✅ **NO** - If the process_instance_id actually exists
- ❌ **YES** - If process_instance_id doesn't exist (orphaned record)

**Current Status**: 
- Service layer validates existence before insert
- No Hibernate cascade support, manual enforcement required

### Scenario B: Deleting ProcessInstance Cascades to Variables

**Current Code** (ProcessInstance has Phase 7 additions):
```kotlin
@Column(name = "parent_instance_id", nullable = true)
val parentInstanceId: Long? = null
```

**Database Constraint** (V19):
```sql
ALTER TABLE process_instance
    ADD COLUMN parent_instance_id BIGINT NULL
    REFERENCES process_instance(id) ON DELETE CASCADE;
```

**Cascade Behavior**:
- Parent deletes → Child auto-deletes ✅
- But NOT defined in entity

**Current Status**: 
- Database enforces cascade
- Hibernate unaware of cascade
- Risk: Orphaned children if business logic expects Hibernate cascade

### Scenario C: Code Task Phase 8.1 Runtime Failure

**Hypothetical Code**:
```kotlin
// In CodeTaskService or REST controller
@Autowired
val jarRepository: CodeTaskJarRepository? = null  // FAILS TO INJECT

fun uploadJar(content: ByteArray, fileName: String): Long {
    val jar = CodeTaskJar(content = content, fileName = fileName)
    return jarRepository.save(jar).id  // NullPointerException
}
```

**Error Stack**:
```
Field jarRepository in com.easy.bpm.service.CodeTaskService required a bean of type 
'com.easy.bpm.repository.CodeTaskJarRepository' that could not be found.

Consider defining a bean of type 'com.easy.bpm.repository.CodeTaskJarRepository' 
in your configuration.
```

**Current Status**: 🔴 **WILL HAPPEN** if Phase 8.2 tries to use repositories

---

## Required Actions to Prevent Errors

### CRITICAL (Do Before Phase 8.2 starts)

**1. Create Missing JPA Entities** (if using Spring Data JPA)
```kotlin
// src/main/kotlin/com/easy/bpm/model/codetask/CodeTaskJar.kt
@Entity
@Table(name = "code_task_jar")
data class CodeTaskJar(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false)
    val content: ByteArray,
    
    @Column(nullable = false)
    val fileName: String,
    
    @Column(nullable = false, unique = true, length = 64)
    val fileHash: String,
    
    @Column(name = "upload_date")
    val uploadDate: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "uploaded_by")
    val uploadedBy: String? = null,
    
    val description: String? = null
)

// src/main/kotlin/com/easy/bpm/model/codetask/CodeClassMetadata.kt
@Entity
@Table(name = "code_class_metadata")
data class CodeClassMetadata(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne
    @JoinColumn(name = "jar_id", nullable = false)
    val jar: CodeTaskJar,
    
    @Column(nullable = false, length = 500)
    val className: String,
    
    @Column(nullable = false, length = 255)
    val methodName: String,
    
    @Column(columnDefinition = "TEXT")
    val methodSignature: String? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val inputParams: JsonNode? = null,
    
    @Column(length = 255)
    val returnType: String? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// src/main/kotlin/com/easy/bpm/model/codetask/CodeTaskExecution.kt
@Entity
@Table(name = "code_task_execution")
data class CodeTaskExecution(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "instance_id", nullable = false)
    val instanceId: Long,
    
    @Column(name = "node_id", length = 255)
    val nodeId: String? = null,
    
    @ManyToOne
    @JoinColumn(name = "jar_id")
    val jar: CodeTaskJar? = null,
    
    @Column(length = 500)
    val className: String? = null,
    
    @Column(length = 255)
    val methodName: String? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val inputVariables: JsonNode? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val outputVariables: JsonNode? = null,
    
    @Column(name = "execution_time_ms")
    val executionTimeMs: Int? = null,
    
    @Column(length = 50)
    val status: String? = null,
    
    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,
    
    @Column(name = "executed_at")
    val executedAt: LocalDateTime = LocalDateTime.now()
)
```

**2. Create Repositories** (if using Spring Data JPA)
```kotlin
// src/main/kotlin/com/easy/bpm/repository/CodeTaskJarRepository.kt
@Repository
interface CodeTaskJarRepository : JpaRepository<CodeTaskJar, Long> {
    fun findByFileHash(fileHash: String): CodeTaskJar?
}

// Similar for CodeClassMetadata and CodeTaskExecution
```

### RECOMMENDED (Nice-to-Have)

**3. Add Missing Foreign Key Relationships** in existing entities:
- Add @ManyToOne relationships for ProcessVariable ↔ ProcessInstance
- Add @ManyToOne relationships for TaskVariable ↔ Task
- Add @ManyToOne relationships for Task ↔ Form
- etc.

**4. Add Missing Unique Constraint** to MessageSubscription:
```kotlin
@Entity
@Table(
    name = "message_subscription",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["process_instance_id", "node_id"])
    ]
)
data class MessageSubscription(...)
```

**5. Add Missing Indexes** to entity @Table annotations (already in database):
- ProcessVariable(process_instance_id)
- TaskVariable(task_id)
- WorkerRequest(status)
- etc.

---

## Test Validation

### H2 In-Memory Database Tests

**File**: `src/test/resources/application-test.yml`

Verify:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;MODE=PostgreSQL
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: validate
  flyway:
    locations: classpath:db/migration
```

**Test Command**:
```bash
.\gradlew test --tests "*IntegrationTest" -i
```

**Expected Result**: ✅ All tests pass with 21 Flyway migrations applied

### Schema Verification SQL

```sql
-- Check all 9 main tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Check form table has form_id column
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'form' 
ORDER BY ordinal_position;

-- Check code_task_* tables exist (Phase 8.1)
SELECT table_name FROM information_schema.tables 
WHERE table_name LIKE 'code_task%';
```

---

## Summary Table: Error Risk by Component

| Component | Current Risk | When It Hits | Mitigation |
|---|---|---|---|
| Core Entity Mapping | 🟢 LOW | Spring startup | All tables/columns exist ✅ |
| FK Relationships | 🟡 MEDIUM | Phase 7-8 cascades | Service layer enforces manually |
| Code Task Entities | 🔴 HIGH | Phase 8.2 starts | CREATE entities & repositories |
| JSON Serialization | 🟢 LOW | Runtime operations | Both libraries compatible |
| Enum Handling | 🟢 LOW | Task/status queries | All VARCHAR(50) ✅ |
| Foreign Key Cascade | 🟡 MEDIUM | Delete operations | Database enforces, not ORM |
| Unique Constraints | 🟢 LOW | Insert operations | All database enforced ✅ |

---

## Generated: 2026-04-23

**Last Verified Against**: 
- Flyway migrations: V1-V21 ✅
- Entity files: 9 entity classes ✅
- Database schema: PostgreSQL with JSONB support ✅

**Next Review**: After Phase 8.1 entities are created
