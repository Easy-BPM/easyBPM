# EPIC: Code Task & JAR Execution Support

**Epic ID**: EPIC-Code-Task  
**Status**: 🟡 In Planning  
**Estimated Effort**: 24-32 story points  
**Target Duration**: 4-5 weeks  
**Dependencies**: Phase 7 (Call Activity) - COMPLETE  
**Date Created**: 2026-04-22

---

## Overview

Extend Easy BPM with **Code Task** capability - a new task type that executes custom Java code from uploaded JAR files, enabling complex business logic execution with explicit process variable mapping.

### Business Value

✅ Execute complex business rules without subprocess overhead  
✅ Reuse existing Java libraries and code  
✅ Reduce API dependencies (use local code instead)  
✅ Maintain clean separation: data (variables) ↔ logic (Java code)  
✅ Type-safe variable passing with explicit mapping  
✅ Full audit trail of code execution  

### Key Differentiators vs Alternatives

| Feature | Code Task | Service Task | Call Activity | API Task |
|---------|-----------|--------------|---------------|----------|
| Execute Java | ✅ Direct | ❌ | ✅ Subprocess | ✅ HTTP |
| Upload JAR | ✅ | ❌ | ❌ | ❌ |
| Variable Mapping | ✅ Explicit | Limited | ✅ | Limited |
| Complexity | Complex logic | HTTP calls | Processes | REST APIs |
| Latency | \<100ms | Network | Network | Network |
| Reuse Code | ✅ Yes | ❌ | ✅ Subprocess | ✅ (if public) |

---

## Architecture Overview

### System Design

```
┌─────────────────────────────────────────────────────────┐
│ Process Instance (Parent)                               │
│  Variables: { orderId, amount, items[] }                │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼ reaches Code Task node
┌─────────────────────────────────────────────────────────┐
│ Code Task Handler                                       │
│                                                         │
│  1. Load JAR file from blob storage                     │
│  2. Instantiate Class (reflection)                      │
│  3. Input mapping: process vars → method params         │
│  4. Invoke method with params                           │
│  5. Capture return value (if any)                       │
│  6. Output mapping: return → process vars               │
│  7. Update process variables                            │
│  8. Mark task as completed                              │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼ task complete
┌─────────────────────────────────────────────────────────┐
│ Process Instance (Updated)                              │
│  Variables: { orderId, amount, items[], total, tax }    │
└─────────────────────────────────────────────────────────┘
```

### Component Responsibilities

**CodeTaskHandler.kt** (600+ lines)
- Orchestrate code execution lifecycle
- JAR loading and ClassLoader management
- Method invocation via reflection
- Exception handling and logging

**CodeClassRepository.kt** (CRUD)
- Store uploaded JAR files (BLOB)
- Store class metadata (fully qualified name, method signature)
- Query by process definition
- Version tracking

**CodeExecutionService.kt** (400+ lines)
- Reflection-based method invocation
- Parameter binding (variables → Java types)
- Return value extraction
- Type conversion utilities

**V20 Flyway Migration** (SQL)
- `code_task_jar` table (id, content, hash, upload_date)
- `code_class_metadata` table (id, jar_id, class_name, method_name, params, return_type)
- `code_task_execution` table (id, instance_id, node_id, jar_id, class_name, method, input_vars, output_vars, execution_time, status)

---

## Implementation Phases

### Phase 8.1: Backend Infrastructure (12 story points)

#### Tasks

**T8.1.1: Database Schema & Migrations (2 sp)**
- V20 migration with code_task tables
- Indexes on process_definition_id, instance_id, jar_hash
- Audit trail tables for execution history
- BLOB column for JAR file storage

**T8.1.2: JAR Loading & ClassLoader (3 sp)**
- CodeClassRepository (CRUD operations)
- JAR file persistence (blob storage)
- ClassLoader isolation (prevent conflicts)
- Hash-based deduplication
- Exception handling for corrupt JARs

**T8.1.3: CodeTaskHandler Service (4 sp)**
- Lifecycle orchestration (load → invoke → capture → save)
- Input mapping (process vars → method parameters)
- Output mapping (return value → process vars)
- Variable isolation
- Error propagation with exception variables

**T8.1.4: Reflection & Invocation (2 sp)**
- Method discovery via reflection
- Parameter type matching
- Return value extraction
- Null handling
- Type conversion utilities

**T8.1.5: Integration Tests (1 sp)**
- Happy path: upload JAR → invoke method → update vars
- Error scenarios: JAR not found, method not found, type mismatch
- Variable mapping: simple → complex types
- Edge cases: null inputs, void methods, exceptions

---

### Phase 8.2: Modeler UI (8 story points)

#### Tasks

**T8.2.1: Palette & Canvas (2 sp)**
- Add Code Task to palette (calculator/code icon)
- Canvas rendering (orange color, custom icon)
- Drag-drop functionality
- Node styling

**T8.2.2: JAR Upload Properties Panel (3 sp)**
- JAR file upload input (drag-drop or file picker)
- Upload progress bar
- JAR metadata display (class list, methods available)
- Select target class and method from dropdown
- Display method signature

**T8.2.3: Variable Mapping UI (2 sp)**
- Input mapping: process var → method parameter
- Output mapping: return value → process var
- Visual mapping table (source, target, type)
- Add/remove mapping rows
- Type hints from method signature

**T8.2.4: Validation (1 sp)**
- Code task ID validation (no spaces)
- JAR upload required
- Class/method selection required
- Input mappings match method signature
- Deploy validation

---

### Phase 8.3: Admin UI (4 story points)

#### Tasks

**T8.3.1: Code Task Execution View (2 sp)**
- Display code task execution details
- Show uploaded JAR info
- Display executed class and method
- Show execution time
- Display input/output variables

**T8.3.2: Execution History & Audit (2 sp)**
- Timeline of code task executions
- Input variable snapshots
- Output variable snapshots
- Exception messages (if failed)
- Performance metrics (execution duration)

---

### Phase 8.4: QA Testing (4 story points)

#### Tasks

**T8.4.1: Test Plan (2 sp)**
- 5+ test scenarios
- Unit test matrix (JAR loading, reflection, invocation)
- Integration tests (process → code task → variables)
- E2E tests (end-to-end workflow)
- Performance tests (JAR loading overhead)

**T8.4.2: Test Implementation (2 sp)**
- Execute test scenarios
- Validate variable mapping
- Error handling tests
- Performance baseline

---

### Phase 8.5: Documentation (2 story points)

#### Tasks

**D8.5.1: User Guide (1 sp)**
- How to create a Code Task
- JAR file requirements
- Method signature conventions
- Variable mapping guide
- Best practices

**D8.5.2: Examples & API Reference (1 sp)**
- 3+ real-world examples
- Order calculation JAR
- Business rule evaluation JAR
- Data transformation JAR
- API reference for CodeTaskHandler

---

## Success Criteria

### Functional
✅ Upload JAR files to Easy BPM  
✅ Select class and method from JAR  
✅ Map input variables to method parameters  
✅ Map method return to output variables  
✅ Execute code task and update process variables  
✅ Handle exceptions gracefully  
✅ Audit trail of all executions  

### Quality
✅ ≥95% code coverage  
✅ All test scenarios passing  
✅ No breaking changes to existing features  
✅ Type-safe implementation (Kotlin/TypeScript)  
✅ Performance: code execution < 5 seconds per invocation  

### Usability
✅ Easy JAR upload process  
✅ Clear method selection UI  
✅ Visual variable mapping  
✅ Helpful error messages  
✅ Comprehensive documentation  

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| ClassLoader conflicts | Medium | High | Use isolated ClassLoaders per JAR |
| Type conversion issues | Medium | Medium | Implement flexible type coercion |
| JAR size overhead | Low | Low | Compress JARs, limit size |
| Security vulnerability | Medium | High | Validate JAR files, sandbox execution |
| Performance degradation | Low | Medium | Cache ClassLoaders, benchmark |

---

## Security Considerations

⚠️ **Critical**: JAR files can contain malicious code

**Mitigation Strategies**:
1. ✅ JAR signature verification (optional)
2. ✅ File type validation (magic bytes)
3. ✅ Size limits (max 50MB per JAR)
4. ✅ Execution timeout (30 seconds)
5. ✅ ClassLoader sandboxing (prevent System.exit, etc.)
6. ✅ Audit all uploads and executions
7. ✅ Admin approval workflow (optional flag)

---

## Integration Points

### With Existing Components

**Call Activity**: Code Task can be called from subprocess  
**Error Handling**: Code Task supports error boundaries  
**Variable System**: Full integration with process variables  
**Admin UI**: Execution history and audit trail  
**Documentation**: New section in modeler guides  

### External Integrations

**Maven Repository**: Download JARs (future enhancement)  
**Blob Storage**: S3/Azure for JAR persistence (future)  
**Code Signing**: Verify JAR authenticity (future)  

---

## Examples

### Example 1: Order Total Calculation
```
JAR: order-calculator-1.0.jar
Class: com.acme.OrderCalculator
Method: calculateTotal(items[], taxRate) -> OrderTotal
Input Mapping:
  items -> items
  taxRate -> taxRate
Output Mapping:
  subtotal -> orderSubtotal
  tax -> orderTax
  total -> orderTotal
```

### Example 2: Business Rule Evaluation
```
JAR: approval-rules-2.1.jar
Class: com.acme.ApprovalEngine
Method: evaluateRequest(request) -> ApprovalDecision
Input Mapping:
  expenseAmount -> request.amount
  category -> request.category
Output Mapping:
  decision -> approvalDecision
  reason -> approvalReason
```

### Example 3: Data Transformation
```
JAR: data-transform-1.5.jar
Class: com.acme.DataMapper
Method: transformOrder(order) -> TransformedOrder
Input Mapping:
  order_json -> order
Output Mapping:
  customer_name -> transformed_customer_name
  address -> transformed_address
```

---

## Definition of Done

✅ All code committed to main branch  
✅ All tests passing (≥95% coverage)  
✅ Code review approved  
✅ Documentation complete and reviewed  
✅ Admin can upload and manage JAR files  
✅ Modeler can create Code Tasks  
✅ Process can execute Code Tasks with variable mapping  
✅ Audit trail visible in Admin UI  
✅ No breaking changes to existing features  
✅ Performance baseline established  

---

## Success Metrics

After implementation:
- Time to execute business logic via Code Task vs API Task (expected: 10x faster)
- Number of reused JARs (measure code reuse benefit)
- Code coverage (target: ≥95%)
- Customer satisfaction with feature
- Performance: execution time < 100ms (90th percentile)

---

## Next Steps

1. ✅ Stakeholder review of epic plan
2. ✅ Approval to proceed with Phase 8.1
3. 🔲 Create task breakdown for Phase 8.1
4. 🔲 Begin backend implementation (V20 migration)

---

## Appendix: FAQ

**Q: Can Code Tasks call other Code Tasks?**  
A: Yes, through the process flow - Code Task A completes → Code Task B starts with updated variables.

**Q: How large can JAR files be?**  
A: Recommended max 50MB (configurable). Large JARs impact startup time.

**Q: Can multiple Code Tasks use the same JAR?**  
A: Yes! Hash-based deduplication prevents duplicate storage. Multiple processes can reference same JAR.

**Q: What if Code Task throws exception?**  
A: Exception is captured as variable (exceptionMessage), error boundary can handle it.

**Q: Can I use external libraries in my JAR?**  
A: Yes - shade/fat JAR your dependencies into a single JAR file.

**Q: Is there a sandbox/security boundary?**  
A: Execution timeout (30s) + ClassLoader isolation. For critical security: implement admin approval workflow.

---

## Document Revision History

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-04-22 | 1.0 | Planning | Initial epic specification |
