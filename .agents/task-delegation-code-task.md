# Code Task Implementation - Task Breakdown & Delegation

**Epic**: EPIC-Code-Task-Support  
**Date**: April 22, 2026  
**Total Story Points**: 32 sp (Phases 8.1-8.5)  
**Target Completion**: 4-5 weeks  

---

## Sprint Planning

### Phase 8.1: Backend Infrastructure (12 sp)

#### T8.1.1: Database Schema & Flyway Migration (2 sp)

**Acceptance Criteria**:
- ✅ V20 Flyway migration creates code_task_jar table
- ✅ code_class_metadata table created with unique constraint
- ✅ code_task_execution audit table created
- ✅ Proper indexes on all foreign keys
- ✅ Migration is idempotent (can run multiple times safely)
- ✅ Gradle test passes: `./gradlew test`

**Implementation Checklist**:
```
[ ] Create src/main/resources/db/migration/V20__add_code_task_support.sql
    [ ] code_task_jar table (id, content BYTEA, file_hash UNIQUE, upload_date, file_name, description)
    [ ] code_class_metadata table (jar_id FK, class_name, method_name, method_signature, input_params JSONB, return_type)
    [ ] code_task_execution audit table (instance_id FK, jar_id FK, class_name, method_name, input_variables JSONB, output_variables JSONB, execution_time_ms, status, error_message)
    [ ] Create indexes: instance_id, jar_id, jar_hash
[ ] Verify migration syntax
[ ] Test idempotency
```

**File Location**: `src/main/resources/db/migration/V20__add_code_task_support.sql`

---

#### T8.1.2: CodeTaskJar Entity & Repository (2 sp)

**Acceptance Criteria**:
- ✅ CodeTaskJar.kt JPA entity with @Entity, @Table annotations
- ✅ Properties: id, content (BLOB), fileName, fileHash (unique), uploadDate, uploadedBy, description
- ✅ CodeTaskJarRepository.kt with CRUD methods
- ✅ Methods: findByFileHash, findAll, save, delete
- ✅ Unit tests for persistence

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/entity/CodeTaskJar.kt
    [ ] @Entity @Table("code_task_jar")
    [ ] id: Long, content: ByteArray, fileName: String, fileHash: String (unique)
    [ ] uploadDate: LocalDateTime, uploadedBy: String, description: String
    [ ] toString() method
[ ] Create src/main/kotlin/com/example/bpm/repository/CodeTaskJarRepository.kt
    [ ] JpaRepository<CodeTaskJar, Long>
    [ ] findByFileHash(hash: String): CodeTaskJar?
    [ ] findByUploadedBy(user: String): List<CodeTaskJar>
[ ] Create unit test: CodeTaskJarRepositoryTest.kt
    [ ] Test save/load
    [ ] Test unique fileHash constraint
    [ ] Test findByFileHash
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/entity/CodeTaskJar.kt`
- `src/main/kotlin/com/example/bpm/repository/CodeTaskJarRepository.kt`
- `src/test/kotlin/com/example/bpm/repository/CodeTaskJarRepositoryTest.kt`

---

#### T8.1.3: CodeClassMetadata Entity & Repository (1.5 sp)

**Acceptance Criteria**:
- ✅ CodeClassMetadata.kt with class_name, method_name, method_signature
- ✅ Input parameters stored as JSONB array
- ✅ Return type tracked
- ✅ Repository methods: findByJarAndClass, findByJarAndMethod
- ✅ Unit tests passing

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/entity/CodeClassMetadata.kt
    [ ] @Entity @Table("code_class_metadata")
    [ ] jarId: Long (FK), className: String, methodName: String, methodSignature: String
    [ ] inputParams: String (JSONB), returnType: String
    [ ] Composite unique constraint: (jar_id, class_name, method_name)
[ ] Create src/main/kotlin/com/example/bpm/repository/CodeClassMetadataRepository.kt
    [ ] JpaRepository<CodeClassMetadata, Long>
    [ ] findByJarIdAndClassName(jarId: Long, className: String): List<CodeClassMetadata>
    [ ] findByJarIdAndClassNameAndMethodName(...): CodeClassMetadata?
    [ ] findByJarId(jarId: Long): List<CodeClassMetadata>
[ ] Unit tests: CodeClassMetadataRepositoryTest.kt
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/entity/CodeClassMetadata.kt`
- `src/main/kotlin/com/example/bpm/repository/CodeClassMetadataRepository.kt`
- `src/test/kotlin/com/example/bpm/repository/CodeClassMetadataRepositoryTest.kt`

---

#### T8.1.4: CodeTaskExecutionAudit Entity & Repository (1 sp)

**Acceptance Criteria**:
- ✅ CodeTaskExecutionAudit entity for audit trail
- ✅ Tracks: instanceId, jarId, className, methodName, input/output variables, execution time, status
- ✅ Repository for querying execution history
- ✅ Indexes on instanceId and jarId for fast queries

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/entity/CodeTaskExecutionAudit.kt
    [ ] @Entity @Table("code_task_execution")
    [ ] instanceId: Long (FK), nodeId: String, jarId: Long (FK)
    [ ] className, methodName: String
    [ ] inputVariables: String (JSONB), outputVariables: String (JSONB)
    [ ] executionTimeMs: Int, status: String, errorMessage: String?
    [ ] executedAt: LocalDateTime
[ ] Create src/main/kotlin/com/example/bpm/repository/CodeTaskExecutionAuditRepository.kt
    [ ] JpaRepository<CodeTaskExecutionAudit, Long>
    [ ] findByInstanceId(instanceId: Long): List<CodeTaskExecutionAudit>
    [ ] findByJarId(jarId: Long): List<CodeTaskExecutionAudit>
[ ] Unit tests: CodeTaskExecutionAuditRepositoryTest.kt
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/entity/CodeTaskExecutionAudit.kt`
- `src/main/kotlin/com/example/bpm/repository/CodeTaskExecutionAuditRepository.kt`
- `src/test/kotlin/com/example/bpm/repository/CodeTaskExecutionAuditRepositoryTest.kt`

---

#### T8.1.5: CodeClassRepository - JAR Loading & ClassLoader Management (3 sp)

**Acceptance Criteria**:
- ✅ Load JAR from BLOB storage and create ClassLoader
- ✅ Discover classes and methods in JAR via reflection
- ✅ Cache ClassLoaders to avoid reload overhead
- ✅ Method signature extraction (parameters + return type)
- ✅ Handle corrupt/invalid JARs gracefully
- ✅ Unit tests with mock JAR files

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/service/CodeClassRepository.kt
    [ ] loadJarAsClassLoader(jarBytes: ByteArray): ClassLoader
    [ ] discoverClassesInJar(jarBytes: ByteArray): List<String>
    [ ] discoverMethods(clazz: Class<*>): List<MethodMetadata>
    [ ] extractMethodSignature(method: Method): MethodMetadata
    [ ] validateJarFile(jarBytes: ByteArray): Boolean
    [ ] @Transactional methods for data persistence
[ ] Unit tests: CodeClassRepositoryTest.kt
    [ ] Test loading valid JAR
    [ ] Test loading invalid JAR (should throw exception)
    [ ] Test method discovery
    [ ] Test signature extraction (including parameters and return type)
    [ ] Test caching behavior
```

**Key Implementation Details**:

```kotlin
data class MethodMetadata(
  val className: String,
  val methodName: String,
  val parameterTypes: List<Class<*>>,
  val parameterNames: List<String>, // extracted via reflection/ASM
  val returnType: Class<*>,
  val signature: String // "String name, int age -> String"
)

fun createClassLoader(jarBytes: ByteArray): URLClassLoader {
  val tempFile = createTempFile(jarBytes)
  return URLClassLoader(arrayOf(tempFile.toURI().toURL()), this.javaClass.classLoader)
}
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/service/CodeClassRepository.kt`
- `src/test/kotlin/com/example/bpm/service/CodeClassRepositoryTest.kt`
- `src/test/resources/sample-code-task-1.0.jar` (test fixture)

---

#### T8.1.6: CodeExecutionService - Reflection & Method Invocation (2.5 sp)

**Acceptance Criteria**:
- ✅ Invoke methods via reflection on loaded classes
- ✅ Convert process variables to method parameter types
- ✅ Extract return values from method execution
- ✅ Handle null values and optional parameters
- ✅ Timeout handling (max 30 seconds)
- ✅ Exception capture and propagation
- ✅ Unit tests covering all parameter types

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/service/CodeExecutionService.kt
    [ ] invokeMethod(clazz: Class<*>, method: Method, params: Array<Any?>): Any?
    [ ] convertParameter(value: Any?, targetType: Class<*>): Any?
    [ ] extractReturnValue(result: Any?, returnTypePath: String?): Any?
    [ ] handleException(ex: Exception, instanceId: Long)
    [ ] @Timeout(30, TimeUnit.SECONDS) annotation on invoke method
[ ] Support parameter types:
    [ ] Primitives: Int, Long, Double, Boolean, String
    [ ] Collections: List<T>, Map<String, T>
    [ ] Custom objects via JSON serialization
    [ ] Null/Optional values
[ ] Unit tests: CodeExecutionServiceTest.kt
    [ ] Test method invocation with various parameter types
    [ ] Test return value extraction
    [ ] Test type conversion (String -> Int, etc.)
    [ ] Test exception handling
    [ ] Test timeout behavior
```

**Type Conversion Matrix**:
```
Source -> Target Conversion:
String -> Int: Integer.parseInt()
String -> Double: Double.parseDouble()
String -> Boolean: toBoolean()
Number -> String: toString()
Any -> List<T>: JSON parsing
Map -> Custom Object: JSON deserialization
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/service/CodeExecutionService.kt`
- `src/test/kotlin/com/example/bpm/service/CodeExecutionServiceTest.kt`

---

#### T8.1.7: CodeTaskHandler - Orchestration & Process Integration (2 sp)

**Acceptance Criteria**:
- ✅ Orchestrate full Code Task lifecycle
- ✅ Load JAR, invoke method, capture result
- ✅ Apply input variable mappings
- ✅ Apply output variable mappings
- ✅ Record execution audit trail
- ✅ Handle errors with exception variables
- ✅ Integration tests end-to-end

**Implementation Checklist**:
```
[ ] Create src/main/kotlin/com/example/bpm/handler/CodeTaskHandler.kt
    [ ] executeCodeTask(instance: ProcessInstance, node: ProcessNode, definition: ProcessDefinitionJson)
    [ ] applyInputMappings(instanceId: Long, mappings: Map<String, String>): Array<Any?>
    [ ] applyOutputMappings(instanceId: Long, result: Any?, mappings: Map<String, String>)
    [ ] recordExecution(...): CodeTaskExecutionAudit
    [ ] handleException(instanceId: Long, ex: Exception)
    [ ] Integration with ProcessService lifecycle
[ ] Integration tests: CodeTaskHandlerIntegrationTest.kt
    [ ] Test complete execution flow
    [ ] Test variable input/output mapping
    [ ] Test exception handling
    [ ] Verify audit trail created
    [ ] Verify process state updated
```

**Orchestration Flow**:
```kotlin
suspend fun executeCodeTask(...) {
  1. Load JAR from codeTaskJarRepo.findById(jarId)
  2. Create ClassLoader via codeClassRepository.loadJarAsClassLoader()
  3. Load target class via ClassLoader.loadClass(className)
  4. Find method via reflection: method = findMethod(clazz, methodName)
  5. Apply input mappings: params = applyInputMappings(mappings)
  6. Measure time: startTime = System.currentTimeMillis()
  7. Invoke method: result = codeExecutionService.invokeMethod(clazz, method, params)
  8. Measure end time
  9. Apply output mappings: applyOutputMappings(result, mappings)
  10. Record audit: codeTaskExecutionAuditRepo.save(audit)
  11. Mark node complete: instance.completeNode(nodeId)
  12. Return result
}
```

**File Locations**:
- `src/main/kotlin/com/example/bpm/handler/CodeTaskHandler.kt`
- `src/test/kotlin/com/example/bpm/handler/CodeTaskHandlerIntegrationTest.kt`
- Test fixture JARs: `src/test/resources/sample-code-*.jar`

---

#### T8.1.8: Backend Integration Tests & Validation (1 sp)

**Acceptance Criteria**:
- ✅ 100% of Phase 8.1 code covered by tests
- ✅ All test scenarios passing
- ✅ No regressions in existing features (123/124 tests still pass)
- ✅ Performance baseline: JAR loading < 100ms, method invocation < 50ms
- ✅ Gradle build: BUILD SUCCESSFUL

**Test Scenarios**:
```
[ ] Unit Tests (per component above)
    [ ] CodeTaskJarRepository CRUD
    [ ] CodeClassMetadata persistence
    [ ] CodeClassRepository class discovery
    [ ] CodeExecutionService invocation
    [ ] CodeTaskHandler orchestration

[ ] Integration Tests
    [ ] Full lifecycle: upload JAR -> select method -> invoke -> update variables
    [ ] Variable mapping: process var -> param -> return -> process var
    [ ] Exception handling: JAR not found, method not found, invocation fails
    [ ] Multiple code tasks in single process

[ ] Performance Tests
    [ ] JAR loading overhead (target: < 100ms)
    [ ] Method invocation overhead (target: < 50ms)
    [ ] Variable mapping overhead (target: < 10ms)
    [ ] Concurrent code task execution

[ ] Regression Tests
    [ ] Run all existing Phase 7 tests
    [ ] Ensure 123/124 tests still pass
```

**Validation Checklist**:
```
[ ] Run: ./gradlew test
    [ ] Result: BUILD SUCCESSFUL
    [ ] Test count: ≥130 (existing 123 + new 8+)
    [ ] Coverage: ≥95%
[ ] Run: ./gradlew build
    [ ] Result: BUILD SUCCESSFUL
[ ] Code review checklist
    [ ] Follows Kotlin conventions
    [ ] Proper null handling
    [ ] Logging in place (@Slf4j)
    [ ] Error messages are clear
```

**File Locations**:
- All test files in `src/test/kotlin/com/example/bpm/`
- Test fixtures in `src/test/resources/`

---

### Phase 8.2: Modeler UI (8 sp)

**Prerequisite**: Phase 8.1 Backend COMPLETE & BUILD SUCCESSFUL

#### T8.2.1: Code Task Type & Palette (2 sp)

**Acceptance Criteria**:
- ✅ 'code-task' added to NodeType union
- ✅ Code Task palette item with calculator/code icon
- ✅ Drag-drop onto canvas works
- ✅ Default styling (orange color, custom icon)
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Update types.ts
    [ ] Add 'code-task' to NodeType union
    [ ] Add to NodeData interface:
        - codeTaskConfig?: {
            jarId: string;
            jarFileName: string;
            className: string;
            methodName: string;
            methodSignature: string;
            inputMappings: Record<string, string>;
            outputMappings: Record<string, string>;
          }

[ ] Update Palette.tsx
    [ ] Import Code icon from lucide-react
    [ ] Add Code Task item:
        { type: 'code-task', label: 'Code Task', icon: <Code />, color: 'text-orange-600' }

[ ] Update Canvas.tsx
    [ ] Add to isTask check: node.type === 'code-task'
    [ ] Add to rendering logic:
        if (node.type === 'code-task') {
          // Draw orange rectangle
          // Render Code icon
          // Add hover effect
        }

[ ] Compilation test: npm run build
    [ ] Result: BUILD SUCCESSFUL
```

**File Locations**:
- `easybpmn-modeler/types.ts` (modified)
- `easybpmn-modeler/components/Palette.tsx` (modified)
- `easybpmn-modeler/components/Canvas.tsx` (modified)

---

#### T8.2.2: JAR Upload & Metadata Display (3 sp)

**Acceptance Criteria**:
- ✅ File upload input (drag-drop + click)
- ✅ Upload progress bar
- ✅ Parse uploaded JAR and list classes/methods
- ✅ Select target class and method from dropdowns
- ✅ Display method signature
- ✅ Error handling for invalid JARs
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Create CodeTaskPanel.tsx component
    [ ] File upload input with drag-drop
    [ ] Progress bar during upload
    [ ] Upload button + status indicator
    [ ] Class dropdown (populated after upload)
    [ ] Method dropdown (filtered by selected class)
    [ ] Method signature display (read-only)
    [ ] Error message display

[ ] Backend endpoint: POST /code-tasks/upload
    [ ] Accept JAR file upload
    [ ] Validate JAR format
    [ ] Extract classes and methods
    [ ] Return: { jarId, classes, methods }
    [ ] Store in code_task_jar table

[ ] Backend endpoint: GET /code-tasks/jar/{jarId}/classes
    [ ] Return list of classes in JAR
    [ ] Return { className, methods: [...] }

[ ] Backend endpoint: GET /code-tasks/jar/{jarId}/classes/{className}/methods
    [ ] Return methods in class
    [ ] Return { methodName, signature, parameters }

[ ] Integration with PropertiesPanel
    [ ] Show CodeTaskPanel when node.type === 'code-task'
    [ ] Update config on changes: jarId, className, methodName
    [ ] Disable method selection until class selected
    [ ] Disable parameters until method selected

[ ] Error handling
    [ ] Invalid JAR file
    [ ] Unsupported JAR format
    [ ] Upload timeout
    [ ] Network error

[ ] Compilation: npm run build
    [ ] Result: BUILD SUCCESSFUL
```

**File Locations**:
- `easybpmn-modeler/components/CodeTaskPanel.tsx` (NEW)
- `easybpmn-modeler/services/modelerService.ts` (modified - add upload endpoint)
- Backend endpoint handler (Spring REST controller)

---

#### T8.2.3: Variable Mapping UI (2 sp)

**Acceptance Criteria**:
- ✅ Input mapping table: process var → method parameter
- ✅ Output mapping table: return value field → process var
- ✅ Add/remove mapping rows
- ✅ Type hints from method signature
- ✅ Visual validation (highlight mismatches)
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Extend CodeTaskPanel with mapping section
    [ ] Input Mappings Table
        [ ] Columns: Process Variable | Method Parameter | Type
        [ ] Dropdown for process variables (from context)
        [ ] Dropdown for method parameters (from method signature)
        [ ] Add/remove row buttons
        [ ] Validation: highlight type mismatches
        [ ] Help text: "Map process variables to method inputs"

    [ ] Output Mappings Table
        [ ] Columns: Return Value Path | Process Variable | Type
        [ ] Input for return value path (e.g., "total" or "items[0].price")
        [ ] Dropdown for target process variable
        [ ] Add/remove row buttons
        [ ] Help text: "Capture method output into process variables"

[ ] Type matching visualization
    [ ] Green: types match
    [ ] Yellow: types compatible (with conversion)
    [ ] Red: types incompatible
    [ ] Tooltip: show conversion strategy

[ ] Data persistence
    [ ] Save mappings in node.codeTaskConfig
    [ ] Mappings survive canvas save/load

[ ] Compilation: npm run build
```

**Example UI Layout**:
```
┌─────────────────────────────────────────────┐
│ Code Task: "calculate-order"                │
├─────────────────────────────────────────────┤
│ JAR File:     order-calculator-1.0.jar      │
│ Class:        com.acme.OrderCalculator      │
│ Method:       calculateTotal (3 params)     │
│               Signature: Order, TaxRate → OrderTotal
├─────────────────────────────────────────────┤
│ Input Mappings:                             │
│ ┌─────────────┬──────────────┬────────────┐│
│ │Process Var  │Method Param  │Type Check  ││
│ ├─────────────┼──────────────┼────────────┤│
│ │order        │order         │✓ Match    ││
│ │taxRate      │taxRate       │✓ Match    ││
│ │             │              │            ││
│ └─────────────┴──────────────┴────────────┘│
│ [+ Add Row]  [Remove]                      │
├─────────────────────────────────────────────┤
│ Output Mappings:                            │
│ ┌──────────────┬─────────────┬────────────┐│
│ │Return Path   │Process Var  │Type Check  ││
│ ├──────────────┼─────────────┼────────────┤│
│ │total         │orderTotal   │✓ Match    ││
│ │tax           │orderTax     │✓ Match    ││
│ │              │             │            ││
│ └──────────────┴─────────────┴────────────┘│
│ [+ Add Row]  [Remove]                      │
└─────────────────────────────────────────────┘
```

**File Locations**:
- `easybpmn-modeler/components/CodeTaskPanel.tsx` (extended from T8.2.2)
- `easybpmn-modeler/components/VariableMappingTable.tsx` (NEW - reusable)

---

#### T8.2.4: Validation & Deploy Integration (1 sp)

**Acceptance Criteria**:
- ✅ validateCodeTask() function added
- ✅ Validation on code task save
- ✅ Validation on process deploy
- ✅ Error messages are clear
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Update validation.ts
    [ ] Add validateCodeTask(config: CodeTaskConfig): string[]
        [ ] JAR file is selected (required)
        [ ] Class is selected (required)
        [ ] Method is selected (required)
        [ ] All input mappings are valid
        [ ] Method parameters all mapped (or optional)
        [ ] Output mappings are valid
        [ ] No duplicate mappings
        [ ] Returns array of error messages

[ ] Integrate with deploy validation
    [ ] When deploying process: call validateCodeTask() for all code task nodes
    [ ] Show validation errors to user before deploy
    [ ] Prevent deploy if validation fails
    [ ] Show: "Code Task 'xyz': Missing class selection" etc.

[ ] Error messages
    [ ] "Code Task requires JAR file upload"
    [ ] "Select a class from the uploaded JAR"
    [ ] "Select a method to execute"
    [ ] "Parameter 'amount' has type mismatch: String → int"
    [ ] "Output mapping 'total' to variable 'orderTotal' - type mismatch"

[ ] Compilation: npm run build
```

**Example Validation Flow**:
```
User tries to deploy process with Code Task
↓
validateCodeTask() called
↓
Checks:
  ✓ JAR file selected?
  ✓ Class selected?
  ✓ Method selected?
  ✓ Required params mapped?
  ✓ Types compatible?
↓
IF any check fails:
  Display errors in UI
  Prevent deploy
ELSE:
  Allow deploy
```

**File Locations**:
- `easybpmn-modeler/utils/validation.ts` (modified)

---

### Phase 8.3: Admin UI (4 sp)

**Prerequisite**: Phase 8.1 Backend & 8.2 Modeler UI COMPLETE

#### T8.3.1: Code Task Execution View (2 sp)

**Acceptance Criteria**:
- ✅ Display code task execution details in instance view
- ✅ Show JAR file info (name, hash, upload date)
- ✅ Show executed class and method
- ✅ Show execution time
- ✅ Show input/output variables
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Update types.ts
    [ ] Add CodeTaskExecution interface:
        - id: number
        - nodeId: string
        - jarId: number
        - jarFileName: string
        - className: string
        - methodName: string
        - methodSignature: string
        - inputVariables: Record<string, any>
        - outputVariables: Record<string, any>
        - executionTimeMs: number
        - status: 'COMPLETED' | 'FAILED'
        - errorMessage?: string
        - executedAt: string

[ ] Update adminService.ts
    [ ] Add getCodeTaskExecution(instanceId, nodeId): Promise<CodeTaskExecution>
    [ ] Fetch from backend: GET /instances/{id}/code-tasks/{nodeId}
    [ ] Mock implementation if USE_MOCK flag set

[ ] Create CodeTaskExecutionPanel.tsx
    [ ] Section in InstanceExplorerView for code task info
    [ ] Execution Details Card:
        [ ] JAR Info (file name, upload date, file size)
        [ ] Class/Method: fully qualified name
        [ ] Signature: display method signature
        [ ] Execution Status: badge (COMPLETED/FAILED)
        [ ] Execution Time: "234 ms"
    [ ] Input Variables Section
        [ ] Table or JSON viewer of input variables
    [ ] Output Variables Section
        [ ] Table or JSON viewer of output variables
    [ ] Error Section (if failed)
        [ ] Display error message
        [ ] Stack trace (if available)

[ ] Integration with InstanceExplorerView
    [ ] On instance search/load, fetch code task executions
    [ ] Display in node history or sidebar
    [ ] Load on demand when user clicks code task node

[ ] Styling
    [ ] Use orange color scheme for code tasks
    [ ] Icons from lucide-react (Code icon)
    [ ] Responsive layout (mobile/tablet/desktop)

[ ] Compilation: npm run build
```

**UI Layout Example**:
```
┌────────────────────────────────────────────────┐
│ Code Task Execution: "CalculateTotal"          │
├────────────────────────────────────────────────┤
│ Status: ✓ COMPLETED                            │
│ Execution Time: 234 ms                         │
│                                                │
│ JAR Information:                               │
│ ├─ File: order-calculator-1.0.jar              │
│ ├─ Hash: a1b2c3d4...                           │
│ └─ Uploaded: 2026-04-20 14:30:45               │
│                                                │
│ Executed Code:                                 │
│ ├─ Class: com.acme.OrderCalculator             │
│ ├─ Method: calculateTotal                      │
│ └─ Signature: (Order, double) → OrderTotal     │
│                                                │
│ Input Variables:                               │
│ ┌────────────────┬──────────────────────────┐ │
│ │Variable        │Value                     │ │
│ ├────────────────┼──────────────────────────┤ │
│ │order           │Order(id=123, items=[..])│ │
│ │taxRate         │0.08                      │ │
│ └────────────────┴──────────────────────────┘ │
│                                                │
│ Output Variables:                              │
│ ┌────────────────┬──────────────────────────┐ │
│ │Variable        │Value                     │ │
│ ├────────────────┼──────────────────────────┤ │
│ │orderTotal      │{subtotal: 100, tax: 8..}│ │
│ └────────────────┴──────────────────────────┘ │
└────────────────────────────────────────────────┘
```

**File Locations**:
- `easy-bpm-admin/types.ts` (modified)
- `easy-bpm-admin/services/adminService.ts` (modified)
- `easy-bpm-admin/components/CodeTaskExecutionPanel.tsx` (NEW)
- `easy-bpm-admin/App.tsx` (modified - integrate panel)

---

#### T8.3.2: Execution History & Audit Trail (2 sp)

**Acceptance Criteria**:
- ✅ Timeline of code task executions
- ✅ Filter by instance or JAR
- ✅ Snapshots of input/output variables
- ✅ Exception messages and stack traces
- ✅ Performance metrics (execution duration)
- ✅ TypeScript compilation: BUILD SUCCESSFUL

**Implementation Checklist**:
```
[ ] Backend endpoints
    [ ] GET /instances/{id}/code-tasks → list all executions
    [ ] GET /code-tasks/executions → list all executions (filterable)
    [ ] Filter params: ?instanceId=123&status=FAILED&timeRange=7d
    [ ] Pagination: ?page=0&size=20

[ ] Create CodeTaskAuditTrail.tsx
    [ ] Timeline view of all code task executions
    [ ] Each execution entry shows:
        [ ] Timestamp
        [ ] Status badge (COMPLETED/FAILED)
        [ ] JAR name + class.method
        [ ] Duration (ms)
        [ ] Quick view of input/output counts
    [ ] Click to expand and see full details
    [ ] Expandable input/output variable trees

[ ] Filter & Search
    [ ] Filter by status: All / Completed / Failed
    [ ] Filter by JAR file
    [ ] Date range picker
    [ ] Search by class/method name

[ ] Analytics Section
    [ ] Total executions (time period)
    [ ] Success rate (%)
    [ ] Average execution time (ms)
    [ ] Most used JARs/methods
    [ ] Failed executions (with error breakdown)

[ ] Exception Details
    [ ] Full error message display
    [ ] Stack trace (if available)
    [ ] Input values that caused failure
    [ ] Suggested fixes (if determinable)

[ ] Integration with InstanceExplorerView
    [ ] Sidebar or tab: "Code Task Audit"
    [ ] Show all code tasks executed in instance
    [ ] Link from each code task node to execution details

[ ] Compilation: npm run build
```

**UI Layout Example**:
```
Code Task Execution History
├─ Filters
│  ├─ Status: [All ▼] [Completed ▼] [Failed ▼]
│  ├─ JAR: [All JARs ▼]
│  └─ Date Range: [Last 7 days ▼]
│
├─ Analytics Summary
│  ├─ Total: 142 executions (7 days)
│  ├─ Success: 138 (97.2%) ✓
│  ├─ Failed: 4 (2.8%) ✗
│  └─ Avg Time: 234 ms
│
└─ Timeline
   ├─ [2026-04-22 14:30:45] ✓ COMPLETED
   │  ├─ JAR: order-calculator-1.0.jar
   │  ├─ com.acme.OrderCalculator.calculateTotal
   │  ├─ Duration: 145 ms
   │  └─ [Click to expand...]
   │
   ├─ [2026-04-22 14:29:12] ✓ COMPLETED
   │  ...
   │
   └─ [2026-04-22 14:15:33] ✗ FAILED
      ├─ JAR: payment-processor-2.0.jar
      ├─ com.acme.PaymentEngine.processPayment
      ├─ Duration: 5432 ms
      ├─ Error: "Payment gateway timeout"
      └─ [Click to expand...]
```

**File Locations**:
- `easy-bpm-admin/components/CodeTaskAuditTrail.tsx` (NEW)
- `easy-bpm-admin/App.tsx` (modified - add audit tab)
- Backend endpoints (Spring REST controller)

---

### Phase 8.4: QA Testing (4 sp)

**Prerequisite**: All Phases 8.1, 8.2, 8.3 COMPLETE

#### T8.4.1: Comprehensive Test Plan (2 sp)

**File**: `docs-site-working/docs/phase-8-qa-test-plan.md`

**Test Scenarios**:
1. ✅ Basic Code Task Execution
   - Upload valid JAR → select method → invoke → capture result
2. ✅ Variable Mapping
   - Input variables → method parameters
   - Method return → output variables
   - Type conversions
3. ✅ Error Handling
   - JAR not found → error handled
   - Method not found → error handled
   - Method throws exception → captured
   - Type mismatch → conversion or error
4. ✅ Performance & Scale
   - Large JAR file (30MB)
   - Method with 10+ parameters
   - Complex return type
   - Concurrent code task execution
5. ✅ Edge Cases
   - Null parameters
   - Void return type
   - Static vs instance methods
   - Multiple code tasks in one process
6. ✅ Security
   - Malicious JAR detection
   - Timeout enforcement
   - ClassLoader isolation

**Test Matrix**:
```
| Scenario | Unit | Integration | E2E | Automated |
|----------|------|-------------|-----|-----------|
| Basic    | ✓    | ✓           | ✓   | ✓        |
| Mapping  | ✓    | ✓           | ✓   | ✓        |
| Errors   | ✓    | ✓           | ✓   | ✓        |
| Perf     | ✓    | ✓           | ✗   | ✓        |
| Edge     | ✓    | ✓           | ✓   | ✓        |
| Security | ✗    | ✓           | ✗   | ✓        |
```

**Test Execution Timeline**:
- Week 1: Unit tests (50+ test cases)
- Week 2: Integration tests (20+ scenarios)
- Week 3: E2E tests (10+ workflows)
- Week 4: Performance baseline, security audit

---

#### T8.4.2: Test Implementation & Validation (2 sp)

**Acceptance Criteria**:
- ✅ Execute all test scenarios
- ✅ ≥95% code coverage
- ✅ All critical tests PASS
- ✅ Performance baseline established
- ✅ No regressions (all existing tests still pass)

**Test Execution Checklist**:
```
[ ] Unit Tests (CodeTaskHandlerTest.kt, etc.)
    [ ] Run: ./gradlew test -x integration
    [ ] Result: All pass
    [ ] Coverage: ≥95%

[ ] Integration Tests
    [ ] Run: ./gradlew test -i integration
    [ ] Result: All pass
    [ ] Coverage: ≥90%

[ ] E2E Tests (Postman/REST client)
    [ ] Deploy process with Code Task
    [ ] Execute Code Task via API
    [ ] Verify result variables updated
    [ ] Verify audit trail recorded

[ ] Performance Tests
    [ ] JAR loading: measure & baseline
    [ ] Method invocation: measure & baseline
    [ ] Variable mapping: measure & baseline
    [ ] Report: "JAR loading takes 45ms (target: <100ms)" ✓

[ ] Regression Tests
    [ ] Run full test suite: ./gradlew test
    [ ] Verify: 130+ tests passing
    [ ] Existing features work: Call Activity, Service Tasks, etc.

[ ] Security Tests
    [ ] Try uploading JAR with System.exit() → blocked
    [ ] Try uploading corrupted JAR → error handled
    [ ] Execution timeout (>30s) → timeout enforced
    [ ] Concurrent executions → proper isolation

[ ] Sign-off
    [ ] QA approval
    [ ] Performance baseline documented
    [ ] No critical defects found
    [ ] Ready for production
```

**Success Criteria**:
- ✅ Unit test coverage: ≥95% (target: 100%)
- ✅ Integration test coverage: 100% of scenarios
- ✅ E2E test coverage: ≥80% of happy paths
- ✅ All CRITICAL tests: PASS
- ✅ Performance baselines: documented
- ✅ Zero critical/high-severity bugs

---

### Phase 8.5: Documentation (2 sp)

**Prerequisite**: Phases 8.1-8.4 COMPLETE

#### D8.5.1: User Guide & Best Practices (1 sp)

**File**: `docs-site-working/docs/code-task-user-guide.md` (~3000 words)

**Sections**:
1. What is a Code Task?
2. When to use Code Tasks vs alternatives
3. Step-by-step: Create your first Code Task
4. Uploading JAR files (format, requirements)
5. Method signature conventions
6. Variable mapping patterns
7. Handling errors and exceptions
8. Performance considerations
9. Best practices (6-8 items)
10. Troubleshooting & FAQ

---

#### D8.5.2: Examples & API Reference (1 sp)

**Files**:
- `docs-site-working/docs/code-task-examples.md` (~2000 words)
- `docs-site-working/docs/code-task-api-reference.md` (~1500 words)

**Examples**:
1. Example 1: Order calculation JAR
2. Example 2: Business rule evaluation
3. Example 3: Data transformation
4. Example 4: Enrichment service

**API Reference**:
- POST /code-tasks/upload (JAR upload)
- GET /code-tasks/jar/{id}/classes (class discovery)
- GET /code-tasks/jar/{id}/classes/{name}/methods (method discovery)
- Code Task Node configuration schema
- Variable mapping schema
- Error response codes

---

## Success Metrics & Acceptance

### Phase Completion Criteria

**Phase 8.1 Done When**:
- ✅ V20 Flyway migration created
- ✅ CodeTaskJar, CodeClassMetadata, CodeTaskExecutionAudit entities created
- ✅ CodeClassRepository service loading JARs
- ✅ CodeExecutionService invoking methods via reflection
- ✅ CodeTaskHandler orchestrating full lifecycle
- ✅ 130+ tests passing (100% coverage of Phase 8.1)
- ✅ `./gradlew test` → BUILD SUCCESSFUL
- ✅ No regressions: existing tests still pass

**Phase 8.2 Done When**:
- ✅ Code Task palette item renders
- ✅ JAR upload UI functional
- ✅ Class/method selection working
- ✅ Variable mapping UI complete
- ✅ Deploy validation in place
- ✅ `npm run build` (modeler) → BUILD SUCCESSFUL
- ✅ No TypeScript errors
- ✅ All 5 modeler tests passing

**Phase 8.3 Done When**:
- ✅ Code Task execution view displays correctly
- ✅ Audit trail shows all executions
- ✅ Filters and search working
- ✅ Analytics dashboard shows metrics
- ✅ `npm run build` (admin) → BUILD SUCCESSFUL
- ✅ No TypeScript errors
- ✅ All 5 admin tests passing

**Phase 8.4 Done When**:
- ✅ All test scenarios executed
- ✅ ≥95% code coverage achieved
- ✅ All critical tests PASS
- ✅ Performance baselines documented
- ✅ Zero critical bugs
- ✅ QA sign-off received

**Phase 8.5 Done When**:
- ✅ User guide comprehensive and clear
- ✅ 4+ real-world examples included
- ✅ API reference complete
- ✅ Documentation site builds: `npm run build`
- ✅ All links working
- ✅ No spelling/grammar errors

---

## File Manifest

### Backend Files Created
```
src/main/kotlin/com/example/bpm/
├── entity/
│   ├── CodeTaskJar.kt
│   ├── CodeClassMetadata.kt
│   └── CodeTaskExecutionAudit.kt
├── repository/
│   ├── CodeTaskJarRepository.kt
│   ├── CodeClassMetadataRepository.kt
│   └── CodeTaskExecutionAuditRepository.kt
├── service/
│   ├── CodeClassRepository.kt
│   ├── CodeExecutionService.kt
│   └── CodeTaskHandler.kt
└── controller/
    └── CodeTaskController.kt

src/main/resources/db/migration/
└── V20__add_code_task_support.sql

src/test/kotlin/com/example/bpm/
├── entity/
│   └── *Test.kt (3 files)
├── repository/
│   └── *Test.kt (3 files)
├── service/
│   └── *RepositoryTest.kt (3 files)
├── handler/
│   └── CodeTaskHandlerIntegrationTest.kt
└── controller/
    └── CodeTaskControllerTest.kt

src/test/resources/
├── sample-code-task-1.0.jar
├── sample-code-task-2.0.jar
└── invalid-code-task.jar
```

### Frontend - Modeler Files
```
easybpmn-modeler/
├── types.ts (modified)
├── components/
│   ├── Palette.tsx (modified)
│   ├── Canvas.tsx (modified)
│   ├── PropertiesPanel.tsx (modified)
│   ├── CodeTaskPanel.tsx (NEW)
│   └── VariableMappingTable.tsx (NEW)
├── services/
│   └── modelerService.ts (modified)
└── utils/
    └── validation.ts (modified)
```

### Frontend - Admin Files
```
easy-bpm-admin/
├── types.ts (modified)
├── components/
│   ├── CodeTaskExecutionPanel.tsx (NEW)
│   └── CodeTaskAuditTrail.tsx (NEW)
├── services/
│   └── adminService.ts (modified)
└── App.tsx (modified)
```

### Documentation Files
```
docs-site-working/
├── docs/
│   ├── phase-8-qa-test-plan.md (NEW)
│   ├── code-task-user-guide.md (NEW)
│   ├── code-task-examples.md (NEW)
│   └── code-task-api-reference.md (NEW)
└── sidebars.ts (modified - add Code Task category)
```

---

## Estimated Timeline

| Phase | Week | Sprint | Status | Effort |
|-------|------|--------|--------|--------|
| 8.1 | 1-2 | Backend | ⏳ Ready | 12 sp |
| 8.2 | 2-3 | Modeler UI | ⏳ Ready | 8 sp |
| 8.3 | 3-4 | Admin UI | ⏳ Ready | 4 sp |
| 8.4 | 4 | QA Testing | ⏳ Ready | 4 sp |
| 8.5 | 4-5 | Documentation | ⏳ Ready | 2 sp |
| **TOTAL** | **4-5 weeks** | **All phases** | **Ready to start** | **32 sp** |

---

## Next Steps

1. ✅ Epic planned and documented
2. ✅ Task breakdown complete
3. 🔲 **START HERE**: Begin Phase 8.1 backend implementation
   - Create V20 migration
   - Create entity classes
   - Build CodeClassRepository
4. 🔲 Phase 8.1 → BUILD SUCCESSFUL
5. 🔲 Start Phase 8.2 modeler UI
6. 🔲 Continue through phases 8.3, 8.4, 8.5

---

## Approval & Sign-Off

**Epic Owner**: [Name]  
**Date Approved**: [Date]  
**Stakeholders**: [List]  
**Ready to proceed**: ☐ Yes ☐ No

---

**Document Version**: 1.0  
**Date Created**: April 22, 2026  
**Last Updated**: April 22, 2026
