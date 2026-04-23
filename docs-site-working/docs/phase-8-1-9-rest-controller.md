# Phase 8.1.9: Code Task REST Controller

**Status**: ✅ COMPLETE (2026-04-22)

## Overview

Phase 8.1.9 implements REST API endpoints for Code Task management, enabling:
- JAR file upload with validation and class/method discovery
- Retrieval of discovered classes from uploaded JARs
- Retrieval of method details with signatures and parameters
- Execution history and audit trail querying

## Endpoints

### 1. Upload JAR File
```
POST /code-tasks/upload
Content-Type: multipart/form-data

Parameters:
  - jarFile (required): JAR file (multipart)
  - description (optional): JAR description string

Response (200 OK):
{
  "jarId": 1,
  "fileName": "calculator-1.0.jar",
  "fileHash": "sha256hash...",
  "uploadedAt": "2026-04-22T10:30:00",
  "classCount": 5,
  "methodCount": 24,
  "classes": ["com.example.Calculator", "com.example.OrderProcessor", ...]
}

Error Cases:
  - 400: Invalid JAR file (not valid ZIP format)
  - 400: Empty JAR file
  - 400: Duplicate JAR (same hash already uploaded)
  - 500: Internal server error
```

### 2. List Classes in JAR
```
GET /code-tasks/jar/{jarId}/classes

Parameters:
  - jarId (path): ID of uploaded JAR

Response (200 OK):
{
  "jarId": 1,
  "fileName": "calculator-1.0.jar",
  "classes": ["com.example.Calculator", "com.example.OrderProcessor", ...]
}

Error Cases:
  - 404: JAR not found
  - 500: Internal server error
```

### 3. List Methods in Class
```
GET /code-tasks/jar/{jarId}/classes/{className}/methods

Parameters:
  - jarId (path): ID of uploaded JAR
  - className (path): Fully qualified class name

Response (200 OK):
{
  "className": "com.example.Calculator",
  "methods": [
    {
      "methodName": "add",
      "returnType": "int",
      "signature": "public int add(int, int)",
      "parameters": ["int", "int"],
      "parameterNames": ["param0", "param1"],
      "isStatic": true
    },
    ...
  ]
}

Error Cases:
  - 404: JAR or class not found
  - 500: Internal server error
```

### 4. List Execution History
```
GET /code-tasks/executions

Query Parameters:
  - instanceId (optional): Filter by process instance ID
  - status (optional): Filter by status (COMPLETED, FAILED, TIMEOUT)
  - page (optional): Page number (default: 0)
  - size (optional): Page size (default: 20)

Response (200 OK):
{
  "content": [
    {
      "executionId": 1,
      "instanceId": 123,
      "nodeId": "codeTask1",
      "jarId": 1,
      "className": "com.example.Calculator",
      "methodName": "add",
      "inputVariables": "{\"a\": 5, \"b\": 3}",
      "outputVariables": "{\"sum\": 8}",
      "executionTimeMs": 45,
      "status": "COMPLETED",
      "errorMessage": null,
      "executedAt": "2026-04-22T10:35:12"
    },
    ...
  ],
  "totalElements": 157,
  "totalPages": 8,
  "currentPage": 0
}

Error Cases:
  - 500: Internal server error
```

## Implementation Details

### Controller: `CodeTaskController`
- **Package**: `com.easy.bpm.controller`
- **Annotations**: `@RestController`, `@RequestMapping("/code-tasks")`
- **Methods**:
  - `uploadJar()` - POST endpoint
  - `getJarClasses()` - GET endpoint
  - `getClassMethods()` - GET endpoint
  - `getExecutions()` - GET endpoint

### DTOs Created

1. **CodeTaskJarUploadRequest**
   - Represents multipart JAR file upload
   - Fields: jarFile, description

2. **CodeTaskJarUploadResponse**
   - Represents successful upload response
   - Fields: jarId, fileName, fileHash, uploadedAt, classCount, methodCount, classes

3. **MethodMetadataResponse**
   - Represents single method with metadata
   - Fields: methodName, returnType, signature, parameters, parameterNames, isStatic

4. **ClassMetadataResponse**
   - Represents class with its methods
   - Fields: className, methods (list of MethodMetadataResponse)

5. **JarClassesResponse**
   - Represents list of classes in JAR
   - Fields: jarId, fileName, classes

6. **CodeTaskExecutionAuditResponse**
   - Represents single execution audit record
   - Fields: executionId, instanceId, nodeId, jarId, className, methodName, inputVariables, outputVariables, executionTimeMs, status, errorMessage, executedAt

7. **ExecutionAuditPageResponse**
   - Represents paginated execution audit records
   - Fields: content, totalElements, totalPages, currentPage

### Repository Methods Added

**CodeTaskExecutionAuditRepository**:
- `findByInstanceId(instanceId, pageable): Page<CodeTaskExecutionAudit>`
- `findByStatus(status, pageable): Page<CodeTaskExecutionAudit>`
- `findByInstanceIdAndStatus(instanceId, status, pageable): Page<CodeTaskExecutionAudit>`

## Validation & Error Handling

### JAR Upload Validation
1. File must not be empty
2. File must be valid ZIP/JAR format (magic bytes: 0x50, 0x4B, 0x03, 0x04)
3. File hash must be unique (no duplicates)

### Error Responses
- All endpoints return proper HTTP status codes
- Errors logged with LoggerFactory
- Stack traces logged for debugging

## Testing

### Integration Tests
- Test JAR upload with valid/invalid files
- Test class discovery
- Test method retrieval
- Test execution history filtering
- Test pagination

### TODO: Add to Backend Tests
```kotlin
// CodeTaskControllerIntegrationTest.kt
- uploadJar_validFile_success()
- uploadJar_invalidFormat_badRequest()
- uploadJar_duplicate_badRequest()
- getJarClasses_existingJar_success()
- getJarClasses_nonExistentJar_notFound()
- getClassMethods_validClass_success()
- getExecutions_withFilters_paginatedResults()
```

## Integration with Frontend

### Phase 8.2 Modeler Integration
The Modeler UI components consume these endpoints:
1. **CodeTaskJarUploadPanel** → `POST /code-tasks/upload`
2. **CodeTaskPropertyPanel** → `GET /code-tasks/jar/{jarId}/classes` and `/methods`
3. **Execution monitoring** → `GET /code-tasks/executions` (Admin UI)

### CORS Configuration
Endpoints are accessible from:
- http://localhost:3000 (Modeler)
- http://localhost:5173 (Admin UI)
- http://localhost:5174 (Task Portal)

## Security Considerations

### Current Implementation
- No authentication required (TODO: Add JWT/Session auth)
- No authorization checks on endpoints
- JAR content stored in database (BLOB)

### Future Enhancements
1. Add JWT token validation on all endpoints
2. Implement role-based access control (RBAC)
3. Add JAR file size limits
4. Implement JAR signature verification
5. Add audit logging for all uploads
6. Encrypt JAR content at rest

## Performance Considerations

### Optimization Opportunities
1. Cache discovered classes/methods (currently discovers on every upload)
2. Index JAR lookup by hash (already done)
3. Paginate execution history (implemented)
4. Add compression for JSONB variables in audit trail
5. Create materialized view for frequently accessed metadata

### Database Impact
- `code_task_jar` table: JAR content stored as BYTEA (can be large)
- `code_class_metadata` table: Can grow significantly with many JAR uploads
- `code_task_execution` table: Grows with every Code Task execution

## Files Created

1. **Controller**: `src/main/kotlin/com/example/bpm/controller/CodeTaskController.kt` (350 lines)
2. **DTOs** (5 files):
   - `CodeTaskJarUploadRequest.kt`
   - `CodeTaskJarUploadResponse.kt`
   - `ClassMetadataResponse.kt`
   - `CodeTaskExecutionAuditResponse.kt`
3. **Updated Repositories**:
   - `CodeTaskExecutionAuditRepository.kt` (added 3 paginated methods)

## Next Steps

1. ✅ Phase 8.1.9: REST Controller (COMPLETE)
2. 🔄 Phase 8.2: Modeler UI (IN PROGRESS)
3. 🔄 Phase 8.2.1: Code Task Palette & Canvas (STARTED)
4. 🔄 Phase 8.2.2: JAR Upload Panel (STARTED)
5. 🔄 Phase 8.2.3: Variable Mapping UI (STARTED)
6. 🔄 Phase 8.2.4: Deploy & Validation (PENDING)

## Verification Checklist

- ✅ All endpoints implemented
- ✅ DTOs created with proper fields
- ✅ Repositories have required methods
- ✅ Controller has error handling
- ✅ Code compiles successfully
- ⏳ Backend integration tests (Phase 8.4)
- ⏳ API contract tests with Modeler
- ⏳ End-to-end workflow tests

---
**Last Updated**: 2026-04-22 | **Author**: Backend Development Team
