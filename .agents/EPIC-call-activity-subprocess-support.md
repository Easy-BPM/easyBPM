---
epic_id: EPIC-CALL-001
epic_name: Call Activity & Subprocess Support
phase: Phase 7
priority: HIGH
status: PLANNING
start_date: 2026-04-22
target_completion: 2026-06-30
estimated_effort: 40 story_points
---

# EPIC: Call Activity & Subprocess Support

**Prepared by**: CTO  
**Date**: 2026-04-22  
**Stakeholders**: Backend Team, Frontend Team, QA, Tech Writer

---

## Executive Summary

Enable process definitions to invoke other process definitions as sub-processes (called activities). This allows building complex workflows through composition, reducing duplication, and improving process maintainability.

**Business Value**:
- Reusable workflow components
- Reduced process complexity
- Better separation of concerns
- Faster development of complex scenarios

**Technical Challenge**:
- Instance context inheritance
- Variable mapping between parent/child
- Error handling and compensation
- Execution state isolation

---

## Goals & Success Criteria

### Primary Goal
Implement RFC-compliant call activity nodes that invoke child process definitions with full variable mapping, error handling, and completion tracking.

### Success Criteria
- ✅ Call Activity nodes executable in process definitions
- ✅ Parent → Child variable input mapping working
- ✅ Child → Parent variable output mapping working
- ✅ Error boundary events on call activities functioning
- ✅ Multi-level nesting supported (subprocess calls subprocess)
- ✅ Execution state properly isolated between instances
- ✅ 95%+ test coverage for call activity features
- ✅ Documentation with examples
- ✅ Admin UI displays nested process details

---

## Problem Statement

### Current Limitations
1. **No Reusability**: Common workflows must be re-modeled in each process
2. **Complexity**: Large processes become difficult to maintain
3. **Duplication**: Same logic repeated across multiple processes
4. **Testability**: Complex workflows hard to unit test

### Example Use Case
```
Order Processing (Main Process)
├─ Validate Order (CallActivity → ValidationProcess)
├─ Process Payment (CallActivity → PaymentProcess)
│  ├─ Charge Card (CallActivity → ChargeProcess)
│  └─ Reconcile (CallActivity → ReconciliationProcess)
└─ Ship Order (CallActivity → ShippingProcess)
```

---

## Architecture Design

### Call Activity Node Structure

```typescript
interface CallActivityNode {
  id: string;
  type: "CallActivity";
  name: string;
  processKey: string;           // Target subprocess key
  
  inputMapping?: {              // Parent → Child
    [childVar: string]: string; // "childVar": "parentVar"
  };
  
  outputMapping?: {             // Child → Parent
    [parentVar: string]: string; // "parentVar": "childVar"
  };
  
  propagateAllVariables?: boolean; // Default: false
  
  config?: {
    waitForCompletion: boolean;   // Default: true
    errorCode?: string;           // For error boundary
    exceptionVariable?: string;    // Error capture
  };
  
  next?: string[];  // Flow connections
}
```

### Execution Flow

```
Parent Process
    ↓
Call Activity Node
    ↓ Input Mapping
Child Process Variables
    ↓
Child Process Execution
    ├─ Execute child nodes
    ├─ Handle child errors
    └─ Child completion
    ↓ Output Mapping
Parent Process Variables
    ↓
Resume Parent Process
```

### Instance Hierarchy

```
process_instance (parent)
├─ id: 1000
├─ processDefinitionKey: "order-process"
├─ parentInstanceId: null
├─ nestedLevel: 0
└─ childInstances: [1001, 1002, 1003]

process_instance (child 1)
├─ id: 1001
├─ processDefinitionKey: "validation-process"
├─ parentInstanceId: 1000
├─ nestedLevel: 1
└─ callActivityNodeId: "validate-order"
```

### Database Changes

#### New Column: process_instance
```sql
-- Call activity support
ALTER TABLE process_instance ADD COLUMN parent_instance_id BIGINT NULL;
ALTER TABLE process_instance ADD COLUMN call_activity_node_id VARCHAR(255) NULL;
ALTER TABLE process_instance ADD COLUMN nesting_level INT DEFAULT 0;

-- Indexes for parent-child lookup
CREATE INDEX idx_process_instance_parent_id ON process_instance(parent_instance_id);
CREATE INDEX idx_process_instance_call_node ON process_instance(call_activity_node_id);
```

#### New Table: call_activity_mapping
```sql
CREATE TABLE call_activity_mapping (
  id BIGSERIAL PRIMARY KEY,
  parent_instance_id BIGINT NOT NULL,
  child_instance_id BIGINT NOT NULL,
  call_activity_node_id VARCHAR(255) NOT NULL,
  input_mappings JSONB,
  output_mappings JSONB,
  status VARCHAR(50),
  created_at TIMESTAMP,
  completed_at TIMESTAMP,
  
  FOREIGN KEY (parent_instance_id) REFERENCES process_instance(id),
  FOREIGN KEY (child_instance_id) REFERENCES process_instance(id),
  UNIQUE(parent_instance_id, call_activity_node_id)
);

CREATE INDEX idx_call_mapping_parent ON call_activity_mapping(parent_instance_id);
CREATE INDEX idx_call_mapping_child ON call_activity_mapping(child_instance_id);
```

---

## Implementation Strategy

### Phase 7.1: Backend Core (Sprint 1-2)
**Role**: Backend Developer  
**Effort**: 16 story_points  
**Duration**: 2 weeks

#### Tasks
1. **T7.1.1**: Database migrations (Flyway V17)
   - Add parent_instance_id, call_activity_node_id, nesting_level
   - Create call_activity_mapping table
   - Add indexes

2. **T7.1.2**: CallActivityHandler service
   - Execute call activity node
   - Create child process instance
   - Handle parent-child context

3. **T7.1.3**: Variable mapping engine
   - Input mapping (parent vars → child vars)
   - Output mapping (child vars → parent vars)
   - Propagate all variables option

4. **T7.1.4**: Instance lifecycle management
   - Track parent-child relationships
   - Block parent while child executes
   - Resume parent on child completion

5. **T7.1.5**: Error handling for call activities
   - Error boundary attachment to call activities
   - Error propagation from child → parent
   - Compensation in parent on child failure

6. **T7.1.6**: Integration tests
   - Simple call activity execution
   - Variable mapping (input/output)
   - Multi-level nesting
   - Error scenarios
   - Concurrent child instances

---

### Phase 7.2: Modeler Support (Sprint 2)
**Role**: Frontend Developer  
**Effort**: 8 story_points  
**Duration**: 1 week

#### Tasks
1. **T7.2.1**: Call Activity palette item
   - Add CallActivity icon to modeler palette
   - Drag-drop onto canvas

2. **T7.2.2**: Call Activity properties panel
   - Process key selector (dropdown)
   - Input variable mapping UI
   - Output variable mapping UI
   - Propagate all variables checkbox

3. **T7.2.3**: Canvas rendering for nested processes
   - Show subprocess icon indicator
   - Visual distinction from tasks
   - Tooltip showing target process

4. **T7.2.4**: Process key validation
   - Check process exists before deploy
   - Warn on non-existent process
   - Auto-suggest available processes

5. **T7.2.5**: Export/deploy validation
   - Validate input/output mappings
   - Warn on missing variables
   - Prevent circular references

---

### Phase 7.3: Admin UI Enhancements (Sprint 2-3)
**Role**: Frontend Developer  
**Effort**: 6 story_points  
**Duration**: 1.5 weeks

#### Tasks
1. **T7.3.1**: Instance hierarchy visualization
   - Show parent-child relationships
   - Breadcrumb navigation
   - Instance tree view

2. **T7.3.2**: Child instance inspection
   - View child process variables
   - View child execution history
   - Link to child process definition

3. **T7.3.3**: Variable mapping display
   - Show what variables were mapped
   - Display before/after values
   - Audit trail for mapping changes

---

### Phase 7.4: QA & Testing (Sprint 1-3)
**Role**: QA Engineer  
**Effort**: 6 story_points  
**Duration**: 3 weeks

#### Test Scenarios
1. **Basic Call Activity**
   - Simple subprocess invocation
   - Subprocess completes
   - Parent resumes

2. **Variable Mapping**
   - Input: parent X → child Y
   - Output: child Z → parent W
   - Multiple variable mappings
   - Propagate all variables

3. **Error Handling**
   - Child subprocess fails
   - Error boundary captures error
   - Parent compensates
   - Error message propagated

4. **Nesting**
   - 2-level nesting (A calls B calls C)
   - 3+ level nesting
   - Variable isolation between levels
   - Instance creation order

5. **Execution States**
   - Parent suspended during child execution
   - Child status tracked
   - Proper completion detection
   - Timeout handling

6. **Edge Cases**
   - Call activity with no children defined
   - Circular references (A → B → A)
   - Variable name conflicts
   - Missing variables in mapping
   - Child process disabled/not found

#### Test Matrix
| Scenario | Unit | Integration | E2E |
|----------|------|-------------|-----|
| Basic Call Activity | ✅ | ✅ | ✅ |
| Variable Mapping | ✅ | ✅ | ✅ |
| Error Handling | ✅ | ✅ | ✅ |
| Nesting (2-level) | ✅ | ✅ | ✅ |
| Nesting (3+ level) | ⏳ | ✅ | ⏳ |
| Edge Cases | ✅ | ✅ | - |

---

### Phase 7.5: Documentation (Sprint 3)
**Role**: Tech Writer  
**Effort**: 4 story_points  
**Duration**: 1 week

#### Documentation Tasks
1. **D7.5.1**: Call Activity Guide (`easy-modeler-call-activity.md`)
   - What are call activities
   - When to use them
   - How to create them
   - Variable mapping rules

2. **D7.5.2**: Variable Mapping Tutorial
   - Input mapping examples
   - Output mapping examples
   - Propagate all variables
   - Best practices

3. **D7.5.3**: Error Handling Guide
   - Error boundaries on call activities
   - Error propagation
   - Compensation strategies

4. **D7.5.4**: Real-world Examples
   - Order processing with subprocess
   - Payment processing chain
   - Multi-level approval workflow

5. **D7.5.5**: API Reference Updates
   - New POST /processes/{id}/call-activity endpoint
   - New GET /instances/{id}/children endpoint
   - Variable mapping schema

---

## Role-Based Task Delegation

### Backend Developer (16 sp)
**Tasks**: T7.1.1, T7.1.2, T7.1.3, T7.1.4, T7.1.5, T7.1.6

**Deliverables**:
- Flyway migration V17
- CallActivityHandler.kt
- VariableMappingService.kt
- Updated ProcessService.kt
- Integration tests (6 scenarios)
- Code review ready

**Success Criteria**:
- All tests pass
- 95%+ code coverage
- No circular references in nested calls
- Parent blocked while child executes
- Variable isolation maintained

---

### Frontend Developer (14 sp)
**Tasks**: T7.2.1-T7.2.5, T7.3.1-T7.3.3

**Deliverables**:
- CallActivity component in modeler
- Properties panel UI
- Process key selector
- Variable mapping UI (input/output)
- Admin instance hierarchy view
- Modeler deploy validation

**Success Criteria**:
- No TypeScript errors
- Responsive UI on all screen sizes
- Intuitive variable mapping interface
- Process validation prevents invalid deploys
- Admin UI shows parent-child relationships

---

### QA Engineer (6 sp)
**Tasks**: Test scenario definition, test execution matrix, edge case testing

**Deliverables**:
- Test plan (30+ test cases)
- Integration test suite
- Edge case documentation
- Performance test results
- Acceptance criteria validation

**Success Criteria**:
- 100% scenario coverage
- All edge cases documented
- Performance acceptable (<500ms per level)
- Nested call limits defined (max 10 levels)

---

### Tech Writer (4 sp)
**Tasks**: D7.5.1-D7.5.5

**Deliverables**:
- 5 documentation files
- Comprehensive examples
- API reference updates
- Troubleshooting guide
- Video tutorials (optional)

**Success Criteria**:
- Documentation complete and reviewed
- Examples runnable
- Clear diagrams for nested flow
- FAQ section

---

### Code Review Engineer
**Tasks**: Continuous code review, architecture validation, debt assessment

**Deliverables**:
- Code review checklist
- Architecture validation report
- Technical debt tracking
- Refactoring suggestions
- Production readiness assessment

---

## Timeline & Milestones

```
Week 1-2 (Sprint 1)
├─ Backend: Database + CallActivityHandler + VariableMapping
├─ QA: Test plan definition
└─ Modeler: Prototype UI

Week 2-3 (Sprint 2)
├─ Backend: Error handling + Integration tests
├─ Modeler: Complete implementation + validation
├─ Admin: Start hierarchy visualization
└─ QA: Execute test scenarios

Week 3-4 (Sprint 3)
├─ Admin: Complete enhancements
├─ Tech Writer: Documentation
├─ QA: Edge case testing
└─ Team: Code review + refinements

Week 4 (Sprint 4 - Optional)
├─ Performance optimization
├─ Documentation refinement
└─ Production deployment prep
```

---

## Dependencies & Risks

### Internal Dependencies
- ✅ Phase 6 (QA improvements) must be complete
- ✅ Error boundary feature (already implemented)
- ✅ Variable synchronization (already implemented)

### External Dependencies
- Process definition API stable
- Database connection stable
- RabbitMQ worker available

### Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Circular references | High | Critical | Implement depth checker + cycle detection |
| Variable name conflicts | High | Medium | Clear mapping rules + validation |
| Performance degradation | Medium | High | Load test with 5+ nesting levels |
| Parent-child sync issues | Medium | High | Explicit state machine + integration tests |
| Nesting limit exceeded | Low | Medium | Define max nesting (e.g., 10) + enforce |

---

## Acceptance Criteria

### Functional
- [ ] Call activity node executes child process
- [ ] Input variables mapped from parent → child
- [ ] Output variables mapped from child → parent
- [ ] Parent blocked during child execution
- [ ] Child completion resumes parent
- [ ] Error boundary works on call activities
- [ ] 2+ level nesting works correctly
- [ ] Circular references prevented
- [ ] Variable isolation maintained

### Non-Functional
- [ ] Child process execution < 500ms overhead
- [ ] 5+ nesting levels supported
- [ ] 95%+ test coverage
- [ ] Zero data integrity issues
- [ ] Documentation complete
- [ ] Admin UI responsive
- [ ] No TypeScript errors

### Quality Gates
- [ ] 40+ integration tests passing
- [ ] All code reviewed and approved
- [ ] Performance tests < 2s for 5 levels
- [ ] Security: No variable leakage between instances

---

## Effort Estimation & Budget

### Total Effort
**40 story_points** across 4 weeks

| Role | Tasks | Effort | Duration |
|------|-------|--------|----------|
| Backend | 6 | 16 sp | 2 weeks |
| Frontend | 8 | 14 sp | 2 weeks |
| QA | 6 | 6 sp | 3 weeks |
| Tech Writer | 5 | 4 sp | 1 week |
| Total | 25 | 40 sp | 4 weeks |

### Resource Allocation
- 1 Backend Developer (full-time)
- 1 Frontend Developer (full-time)
- 1 QA Engineer (50% allocation)
- 1 Tech Writer (25% allocation)
- Code Review Engineer (continuous review)

---

## Success Metrics

### Adoption
- ✅ Call activities used in 3+ production processes within 30 days
- ✅ Reduction in process duplication

### Quality
- ✅ Zero data integrity incidents
- ✅ 95%+ test coverage maintained
- ✅ Zero production issues in first month

### Performance
- ✅ Child process invocation < 500ms
- ✅ Support 5+ nesting levels
- ✅ 10000+ concurrent parent-child relationships

---

## Launch Plan

### Pre-Launch (Day -3)
- ✅ All code reviewed and merged
- ✅ All tests passing (100%)
- ✅ Documentation live
- ✅ Team trained on feature

### Launch Day (Day 0)
- ✅ Feature flag enabled for canary (5% users)
- ✅ Monitoring enabled (metrics + logs)
- ✅ Support team briefed
- ✅ Rollback plan tested

### Post-Launch (Day 1-7)
- ✅ Monitor error rates
- ✅ Collect performance metrics
- ✅ Gradual rollout to 25%, 50%, 100%
- ✅ Daily standups for issues

### Post-Launch (Week 2+)
- ✅ Monitor production usage
- ✅ Collect user feedback
- ✅ Document lessons learned
- ✅ Plan optimization work

---

## Related Epics & Features

### Phase 7 Roadmap
- EPIC-CALL-001: Call Activity & Subprocess Support (THIS)
- EPIC-TIMER-001: Timer Events & Boundary Timers
- EPIC-PROC-ARCH-001: Process Instance Archival
- EPIC-REPORT-001: Advanced Reporting

### Feature Dependencies
- Error Boundary Events ✅ (already implemented)
- Variable Synchronization ✅ (already implemented)
- Form Key Support ✅ (already implemented)

---

## CTO Approval & Sign-Off

**Epic Owner**: CTO  
**Status**: APPROVED FOR EXECUTION  
**Date**: 2026-04-22  
**Target Launch**: 2026-06-30

**Notes**:
- This epic addresses a critical gap in process composition
- Proper architecture review needed for variable isolation
- Load testing mandatory before production
- Circular reference detection non-negotiable for stability

---

## Appendix: Example Call Activity Process

### Use Case: Order Processing with Subprocesses

```json
{
  "processId": "order-processing",
  "nodes": [
    {
      "id": "validate",
      "type": "CallActivity",
      "name": "Validate Order",
      "processKey": "order-validation",
      "inputMapping": {
        "orderId": "orderId",
        "items": "items",
        "customerId": "customerId"
      },
      "outputMapping": {
        "validationStatus": "status",
        "validationErrors": "errors"
      },
      "next": ["pay"]
    },
    {
      "id": "pay",
      "type": "CallActivity",
      "name": "Process Payment",
      "processKey": "payment-processing",
      "inputMapping": {
        "orderId": "orderId",
        "amount": "totalAmount",
        "paymentMethod": "paymentMethod"
      },
      "outputMapping": {
        "transactionId": "txnId",
        "paymentStatus": "status"
      },
      "config": {
        "errorCode": "PAYMENT_FAILED"
      },
      "next": ["ship"]
    },
    {
      "id": "ship",
      "type": "CallActivity",
      "name": "Ship Order",
      "processKey": "shipping-process",
      "inputMapping": {
        "orderId": "orderId",
        "address": "shippingAddress",
        "items": "items"
      },
      "outputMapping": {
        "trackingNumber": "trackingId",
        "estimatedDelivery": "eta"
      },
      "next": ["end"]
    },
    {
      "id": "end",
      "type": "EndEvent"
    }
  ]
}
```

### Execution Example
1. Parent: order-processing/1000 starts
2. Enters: validate node (CallActivity)
3. Creates: order-validation/1001 (child instance)
4. Maps: orderId → orderId, items → items
5. Waits: for order-validation/1001 to complete
6. Resumes: with validationStatus, validationErrors
7. Continues: to payment node (CallActivity)
8. Creates: payment-processing/1002 (child instance)
9. And so on...

---

**Next Steps**: Team to review epic, provide estimates feedback, and begin Sprint 1 planning.
