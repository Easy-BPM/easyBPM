# Code Task Feature - Quick Start Guide

**For**: Developers integrating Code Task into the BPMN Modeler  
**Last Updated**: April 22, 2026

## 5-Minute Overview

The Code Task feature allows users to:
1. Upload JAR files containing Java code
2. Select specific methods to invoke from those JARs
3. Map process variables to method parameters
4. Capture method return values as process variables
5. Monitor execution in the Admin UI

## Architecture

```
┌─────────────────────────────────────────────┐
│          BPMN Modeler (React 19)            │
├─────────────────────────────────────────────┤
│  CodeTaskModeler                            │
│  ├─ CodeTaskJarUploadPanel (JAR upload)     │
│  ├─ CodeTaskPropertyPanel (config)          │
│  ├─ CodeTaskNode (canvas visual)            │
│  └─ Tabs: Canvas | Properties | Upload      │
└──────────────┬──────────────────────────────┘
               │ HTTP Calls
               ▼
┌─────────────────────────────────────────────┐
│       Spring Boot Backend (Kotlin)          │
├─────────────────────────────────────────────┤
│  CodeTaskController                         │
│  ├─ POST /code-tasks/upload                 │
│  ├─ GET /code-tasks/jar/{id}/classes        │
│  ├─ GET /code-tasks/jar/{id}/classes/{c}/methods
│  └─ GET /code-tasks/executions              │
└──────────────┬──────────────────────────────┘
               │ JPA
               ▼
┌─────────────────────────────────────────────┐
│    PostgreSQL (Flyway V20+)                 │
├─────────────────────────────────────────────┤
│  code_task_jar                              │
│  code_class_metadata                        │
│  code_task_execution                        │
└─────────────────────────────────────────────┘
```

## Backend Setup (Already Done ✅)

### 1. Database
```sql
-- Migration: V20__add_code_task_support.sql
-- Tables: code_task_jar, code_class_metadata, code_task_execution
-- Status: Automatic with Flyway
```

### 2. Spring Components
```
src/main/kotlin/com/example/bpm/
├── controller/CodeTaskController.kt       (REST endpoints)
├── entity/
│   ├── CodeTaskJar.kt
│   ├── CodeClassMetadata.kt
│   └── CodeTaskExecutionAudit.kt
├── repository/
│   ├── CodeTaskJarRepository.kt
│   ├── CodeClassMetadataRepository.kt
│   └── CodeTaskExecutionAuditRepository.kt
├── service/
│   ├── CodeExecutionService.kt
│   ├── CodeClassDiscoveryService.kt
│   └── CodeTaskHandler.kt
└── dto/
    ├── CodeTaskJarUploadRequest.kt
    ├── CodeTaskJarUploadResponse.kt
    ├── ClassMetadataResponse.kt
    └── CodeTaskExecutionAuditResponse.kt
```

## Frontend Setup (Ready to Integrate 🚀)

### 1. Import Components
```typescript
import { CodeTaskModeler } from '@/components/CodeTaskModeler';
import { CodeTaskJarUploadPanel } from '@/components/CodeTaskJarUploadPanel';
import { CodeTaskPropertyPanel } from '@/components/CodeTaskPropertyPanel';
import { CodeTaskNode } from '@/components/CodeTaskNode';
```

### 2. Use in Main Modeler
```typescript
function BPMNModeler() {
  const [processVariables, setProcessVariables] = useState([
    { name: 'order', type: 'object' },
    { name: 'totalAmount', type: 'number' },
    { name: 'taxRate', type: 'number' }
  ]);

  const handleCodeTaskNodeCreated = (nodeConfig) => {
    // Add to process definition
    console.log('Code Task created:', nodeConfig);
  };

  return (
    <div className="flex gap-4">
      <BPMNPalette />
      
      <CodeTaskModeler
        processDefinitionKey="myProcess"
        processVariables={processVariables}
        onNodeCreated={handleCodeTaskNodeCreated}
        onNodeUpdated={(config) => console.log('Updated:', config)}
      />
      
      <AdminPanel />
    </div>
  );
}
```

### 3. File Locations
```
easybpmn-modeler/components/
├── CodeTaskModeler.tsx              (Main component)
├── CodeTaskJarUploadPanel.tsx        (JAR upload)
├── CodeTaskPropertyPanel.tsx         (Node config)
└── CodeTaskNode.tsx                  (Canvas visual)
```

## Common Tasks

### Task 1: Upload and Use a JAR
```typescript
// User workflow:
// 1. Click "Upload JAR" button in Upload tab
// 2. Select calculator-1.0.jar
// 3. See discovered classes and methods
// 4. Go to Canvas tab
// 5. Drag Code Task to canvas
// 6. Select properties
// 7. Choose JAR → Class → Method
// 8. Configure mappings
```

### Task 2: Configure Variable Mappings
```typescript
// Example configuration:

// Process has: order (object), taxRate (number)
// Method: calculateTotal(Order order, double taxRate) → OrderResult

// Input Mappings:
// process var "order" → parameter 0
// process var "taxRate" → parameter 1

// Output Mappings:
// return.total → process var "totalAmount"
```

### Task 3: Deploy Process with Code Task
```typescript
// POST /processes
{
  "key": "orderProcess",
  "definition": {
    "nodes": [
      {
        "id": "codeTask1",
        "type": "codeTask",
        "codeTask": {
          "jarId": 1,
          "className": "com.example.OrderProcessor",
          "methodName": "calculateTotal",
          "inputMappings": { "order": "0", "taxRate": "1" },
          "outputMappings": { "total": "totalAmount" }
        }
      }
    ]
  }
}
```

## API Quick Reference

### Upload JAR
```bash
curl -X POST http://localhost:8085/code-tasks/upload \
  -F "jarFile=@calculator.jar" \
  -F "description=Calculator utilities"

Response:
{
  "jarId": 1,
  "fileName": "calculator.jar",
  "fileHash": "abc123...",
  "classCount": 3,
  "methodCount": 12,
  "classes": ["com.example.Calculator", ...]
}
```

### List Classes
```bash
curl http://localhost:8085/code-tasks/jar/1/classes

Response:
{
  "jarId": 1,
  "fileName": "calculator.jar",
  "classes": ["com.example.Calculator", "com.example.Statistics"]
}
```

### List Methods
```bash
curl http://localhost:8085/code-tasks/jar/1/classes/com.example.Calculator/methods

Response:
{
  "className": "com.example.Calculator",
  "methods": [
    {
      "methodName": "add",
      "returnType": "int",
      "signature": "public int add(int, int)",
      "parameters": ["int", "int"],
      "parameterNames": ["param0", "param1"]
    }
  ]
}
```

### List Executions
```bash
curl "http://localhost:8085/code-tasks/executions?instanceId=123&status=COMPLETED&page=0&size=10"

Response:
{
  "content": [
    {
      "executionId": 1,
      "instanceId": 123,
      "className": "com.example.Calculator",
      "methodName": "add",
      "inputVariables": "{\"a\": 5, \"b\": 3}",
      "outputVariables": "{\"sum\": 8}",
      "executionTimeMs": 45,
      "status": "COMPLETED"
    }
  ],
  "totalElements": 57,
  "totalPages": 6,
  "currentPage": 0
}
```

## Component Props

### CodeTaskModeler
```typescript
interface Props {
  processDefinitionKey?: string;           // For debugging
  processVariables?: Array<{               // From process form
    name: string;
    type?: string;
  }>;
  onNodeCreated?: (config: any) => void;   // Save to process
  onNodeUpdated?: (config: any) => void;   // Update process
  existingNodes?: any[];                   // Load saved nodes
}
```

### CodeTaskNode
```typescript
interface Props {
  id: string;                              // Unique node ID
  x: number; y: number;                    // Canvas position
  methodName?: string;                     // Display label
  className?: string;                      // Class display
  isSelected?: boolean;                    // Blue highlight
  onClick?: () => void;
  onDoubleClick?: () => void;
}
```

## Troubleshooting

### Issue: JAR upload fails with 400
**Solution**: Ensure file is valid ZIP format
```bash
file calculator.jar  # Should show: Zip archive data
```

### Issue: Classes not showing in dropdown
**Solution**: Check backend logs for class discovery errors
```bash
# Check if classes were discovered
curl http://localhost:8085/code-tasks/jar/1/classes
```

### Issue: Methods not loading
**Solution**: Verify class name is URL-encoded
```bash
# Correct:
/jar/1/classes/com.example.Calculator/methods

# Wrong:
/jar/1/classes/com.example.Calculator/methods  (space encoded as %20)
```

### Issue: Variable mapping not working
**Solution**: Ensure process variables exist in process definition
```typescript
// In process form:
const variables = [
  { name: 'order', type: 'object' },
  { name: 'totalAmount', type: 'number' }
];

// In Code Task:
// input: order → param0
// output: total → totalAmount
```

## Testing

### Manual Test Workflow
1. **Upload JAR**
   ```
   Button: "Upload JAR" in Upload tab
   Select: calculator-1.0.jar
   Verify: Classes show up
   ```

2. **Create Node**
   ```
   Tab: Canvas
   Action: Drag Code Task from palette
   Result: Blue node appears on canvas
   ```

3. **Configure Node**
   ```
   Tab: Properties
   JAR: Select calculator-1.0.jar
   Class: Select com.example.Calculator
   Method: Select add(int, int)
   Mappings: Add input/output mappings
   Verify: No validation errors
   ```

4. **Deploy Process**
   ```
   Button: Deploy (in modeler)
   Check: Code Task included in BPMN XML
   Verify: Process creates instance
   ```

5. **Execute**
   ```
   Portal: Start new process instance
   Verify: Code Task executes
   Check: Admin UI shows execution history
   ```

## Files to Know

| File | Purpose |
|------|---------|
| CodeTaskModeler.tsx | Main orchestrator component |
| CodeTaskController.kt | REST endpoints |
| CodeClassDiscoveryService.kt | JAR class/method discovery |
| CodeTaskHandler.kt | Execution orchestration |
| V20 migration | Database schema |

## Support & Contact

- **Backend Issues**: See `CodeTaskController` logs
- **Frontend Issues**: Browser console
- **Database Issues**: Check PostgreSQL logs
- **API Issues**: Use Postman to test endpoints

## Next Steps

1. ✅ Backend API ready (`./gradlew build`)
2. ✅ Frontend components ready (import and use)
3. ⏳ Integration test suite (Phase 8.4)
4. ⏳ Admin UI execution monitoring (Phase 8.3)
5. ⏳ User documentation (Phase 8.5)

---

**Ready to integrate?** Import `CodeTaskModeler` and add to your main modeler component!
