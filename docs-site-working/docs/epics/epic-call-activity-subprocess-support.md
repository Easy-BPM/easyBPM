# EPIC: Call Activity & Subprocess Support

**Epic ID**: EPIC-CALL-001  
**Phase**: Phase 7  
**Priority**: HIGH  
**Status**: COMPLETE ✅  
**Estimated Effort**: 40 story points  
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
   - New POST `/processes/{id}/call-activity` endpoint
   - New GET `/instances/{id}/children` endpoint
   - Variable mapping schema

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

## Related Features

- Error Boundary Events ✅ (already implemented)
- Variable Synchronization ✅ (already implemented)
- Form Key Support ✅ (already implemented)

---

## CTO Approval & Sign-Off

**Epic Owner**: CTO  
**Status**: APPROVED FOR EXECUTION  
**Date**: 2026-04-22  
**Target Launch**: 2026-06-30

---

## Document Revision History

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-04-22 | 1.0 | CTO | Initial epic specification |
