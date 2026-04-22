---
sidebar_position: 90
---

# Phase 7: Call Activity & Subprocess QA Test Plan

## Overview

Comprehensive testing strategy for Call Activity and Subprocess support in Easy BPM. This document outlines test scenarios, coverage matrices, and validation procedures for the new subprocess invocation feature.

## Test Execution Strategy

| Phase | Duration | Effort | Focus |
|-------|----------|--------|-------|
| **Unit Testing** | Week 1 | 2 sp | Component-level logic, isolation |
| **Integration Testing** | Week 2 | 2 sp | End-to-end workflows, database interactions |
| **E2E Testing** | Week 3 | 2 sp | User workflows, UI interactions, API |

---

## Test Scenarios

### Scenario 1: Basic Call Activity Execution

**Objective**: Verify subprocess invocation and parent-child state transitions

#### Test Cases

**T4.1.1: Simple Subprocess Invocation**
- **Setup**: Parent process with Call Activity node → Child process definition deployed
- **Steps**:
  1. Start parent instance
  2. Execute to Call Activity node
  3. Verify child instance created
  4. Child executes to completion
  5. Parent resumes from next node
- **Expected**: 
  - ✅ Child instance created with `parentInstanceId` = parent.id
  - ✅ Parent suspended with status SUSPENDED
  - ✅ Child executes independently
  - ✅ Parent status changes to ACTIVE after child completes

**T4.1.2: Call Activity Completion**
- **Setup**: Parent and child processes
- **Steps**:
  1. Parent suspended at Call Activity
  2. Child completes successfully
  3. Child status changes to COMPLETED
  4. Parent automatically resumes
- **Expected**:
  - ✅ Parent-child completion synchronized
  - ✅ Parent continues execution flow
  - ✅ Completion marker cleared

**T4.1.3: Multiple Child Invocations**
- **Setup**: Parent process with 2 sequential Call Activity nodes
- **Steps**:
  1. Invoke first subprocess
  2. First subprocess completes
  3. Invoke second subprocess
  4. Second subprocess completes
- **Expected**:
  - ✅ Both children created
  - ✅ Sequential execution (not parallel)
  - ✅ Parent resumes after each child

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ✅

---

### Scenario 2: Variable Mapping

**Objective**: Verify correct transfer of variables between parent and child instances

#### Test Cases

**T4.2.1: Input Variable Mapping**
- **Setup**:
  - Parent variables: `orderId=123`, `customerName=John`, `amount=1000`
  - Input mapping: `orderId→order_id`, `amount→order_amount`
- **Steps**:
  1. Parent at Call Activity with mappings
  2. Child instance created
  3. Verify child variables populated
- **Expected**:
  - ✅ `child.order_id = 123`
  - ✅ `child.order_amount = 1000`
  - ✅ `child.customerName = undefined` (not mapped)

**T4.2.2: Output Variable Mapping**
- **Setup**:
  - Output mapping: `result→approval_result`, `status→order_status`
  - Child sets: `result=APPROVED`, `status=PROCESSED`
- **Steps**:
  1. Child execution with variable assignments
  2. Child completes
  3. Verify parent variables updated
- **Expected**:
  - ✅ `parent.approval_result = APPROVED`
  - ✅ `parent.order_status = PROCESSED`
  - ✅ Original child variables not copied to parent

**T4.2.3: Multiple Variable Mappings**
- **Setup**:
  - 5+ input mappings and 3+ output mappings
  - Complex variable names and nested properties
- **Steps**:
  1. Map variables with different types (string, number, boolean, object)
  2. Execute parent and child
  3. Verify all mappings applied correctly
- **Expected**:
  - ✅ All input mappings applied
  - ✅ All output mappings applied
  - ✅ Type preservation (JsonNode types)

**T4.2.4: Propagate All Variables**
- **Setup**:
  - Parent with 10 variables
  - `propagateAllVariables = true`
- **Steps**:
  1. Child instance created
  2. Verify child received all parent variables
- **Expected**:
  - ✅ Child has all 10 parent variables
  - ✅ Explicit mappings ignored (all propagated instead)
  - ✅ Values preserved exactly

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ✅

---

### Scenario 3: Error Handling

**Objective**: Verify error propagation and boundary event handling

#### Test Cases

**T4.3.1: Child Process Failure**
- **Setup**:
  - Child process with error at task node
  - Error boundary attached to Call Activity in parent
  - Exception variable mapping: `exception→child_error`
- **Steps**:
  1. Parent invokes child
  2. Child fails with exception
  3. Error boundary triggers
  4. Exception captured to variable
  5. Parent executes error handler
- **Expected**:
  - ✅ Child status = FAILED
  - ✅ Parent status = ACTIVE (continues)
  - ✅ `parent.child_error` contains error message
  - ✅ Error handler executes

**T4.3.2: Error Without Boundary**
- **Setup**:
  - Child fails
  - No error boundary on Call Activity
- **Steps**:
  1. Child execution fails
  2. Verify parent gets marked FAILED
- **Expected**:
  - ✅ Parent status = FAILED
  - ✅ Execution stops
  - ✅ Error propagated up

**T4.3.3: Child Timeout**
- **Setup**:
  - Child process with long-running task
  - Parent timeout configured
- **Steps**:
  1. Child exceeds timeout
  2. Verify compensation
- **Expected**:
  - ✅ Child execution halted
  - ✅ Parent compensates or marks failed
  - ✅ Timeout logged

**T4.3.4: Missing Child Process Definition**
- **Setup**:
  - Call Activity references non-existent process
- **Steps**:
  1. Execute Call Activity
  2. Verify error handling
- **Expected**:
  - ✅ Clear error message: "Process definition not found"
  - ✅ Parent marked FAILED
  - ✅ Error logged with process key

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ✅

---

### Scenario 4: Multi-Level Nesting

**Objective**: Verify nested subprocess chains and variable isolation

#### Test Cases

**T4.4.1: Two-Level Nesting (A→B→C)**
- **Setup**:
  - Process A calls B
  - Process B calls C
  - Each has variables and mappings
- **Steps**:
  1. Start A instance
  2. A invokes B
  3. B invokes C
  4. C completes and returns to B
  5. B completes and returns to A
- **Expected**:
  - ✅ All 3 instances created with correct nesting levels
  - ✅ Instance hierarchy: A (level 0) → B (level 1) → C (level 2)
  - ✅ Sequential execution maintained
  - ✅ Proper suspension/resume at each level

**T4.4.2: Three-Level Nesting (A→B→C→D)**
- **Setup**:
  - 4-process call chain
  - Variables at each level
- **Steps**:
  1. Execute full chain
  2. Verify nesting depth tracking
- **Expected**:
  - ✅ Max nesting level (default 10) enforced
  - ✅ All 4 levels execute correctly
  - ✅ Variable isolation maintained

**T4.4.3: Variable Isolation Between Levels**
- **Setup**:
  - A sets x=1, calls B
  - B sets x=2 (own copy), calls C
  - C sets x=3 (own copy)
- **Steps**:
  1. Execute A→B→C
  2. Check variable values at each level
- **Expected**:
  - ✅ A.x = 1 (unchanged)
  - ✅ B.x = 2 (independent)
  - ✅ C.x = 3 (independent)
  - ✅ No cross-level contamination

**T4.4.4: Circular Reference Detection**
- **Setup**:
  - A calls B
  - B calls A (circular)
- **Steps**:
  1. Execute circular chain
  2. Verify prevention
- **Expected**:
  - ✅ Circular reference detected
  - ✅ Error thrown: "Circular subprocess reference detected"
  - ✅ Execution blocked

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ⏳ (manual testing priority)

---

### Scenario 5: Execution States

**Objective**: Verify correct state transitions and status tracking

#### Test Cases

**T4.5.1: Parent Suspension During Child Execution**
- **Setup**:
  - Parent with Call Activity
  - Child with 5-second execution time
- **Steps**:
  1. Start parent
  2. Execute to Call Activity
  3. Check parent status immediately
  4. Wait for child completion
  5. Check parent status again
- **Expected**:
  - ✅ Parent status = SUSPENDED (during child)
  - ✅ Parent currentNode = Call Activity ID
  - ✅ Parent status = ACTIVE after child completes
  - ✅ Parent currentNode moves to next node

**T4.5.2: Child Status Tracking**
- **Setup**:
  - Child process with multiple nodes
- **Steps**:
  1. Monitor child instance status
  2. Track state transitions: CREATED → ACTIVE → COMPLETED
- **Expected**:
  - ✅ Status changes logged
  - ✅ Each status change timestamped
  - ✅ Transitions match execution flow

**T4.5.3: Proper Completion Detection**
- **Setup**:
  - Child reaches end event
- **Steps**:
  1. Execute child to completion
  2. Verify completion signal sent to parent
- **Expected**:
  - ✅ Parent receives completion notification
  - ✅ No orphaned child instances
  - ✅ Completion timestamp recorded

**T4.5.4: Status Consistency in Admin UI**
- **Setup**:
  - Parent-child instances
  - Admin UI querying status
- **Steps**:
  1. Query parent status
  2. Query child status
  3. Verify consistency
- **Expected**:
  - ✅ Admin UI shows correct parent status (SUSPENDED/ACTIVE)
  - ✅ Child status reflects actual execution
  - ✅ Hierarchy displayed correctly

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ✅

---

### Scenario 6: Edge Cases

**Objective**: Handle unusual but valid conditions

#### Test Cases

**T4.6.1: Call Activity with No Target Process**
- **Setup**:
  - Call Activity node with empty `callActivityProcessKey`
- **Steps**:
  1. Execute Call Activity
  2. Verify error handling
- **Expected**:
  - ✅ Validation error caught
  - ✅ Process marked FAILED
  - ✅ Clear error message

**T4.6.2: Empty Variable Mappings**
- **Setup**:
  - Call Activity with no input/output mappings
  - `propagateAllVariables = false`
- **Steps**:
  1. Execute Call Activity
  2. Check child instance variables
- **Expected**:
  - ✅ Child created with no parent variables
  - ✅ Execution continues normally
  - ✅ No errors thrown

**T4.6.3: Variable Name Conflicts**
- **Setup**:
  - Parent with variable `status=PENDING`
  - Child receives input: `status→status` (same name)
  - Child sets `status=ACTIVE`
  - Output mapping: `status→status`
- **Steps**:
  1. Execute parent-child
  2. Check final parent.status value
- **Expected**:
  - ✅ Child overwrites parent variable
  - ✅ Final parent.status = ACTIVE
  - ✅ No conflict errors

**T4.6.4: Missing Variables in Mapping**
- **Setup**:
  - Input mapping: `xyz→abc`
  - Parent doesn't have `xyz` variable
- **Steps**:
  1. Execute Call Activity
  2. Check child.abc value
- **Expected**:
  - ✅ Child.abc = null or undefined
  - ✅ No error thrown
  - ✅ Execution continues

**T4.6.5: Disabled Child Process Definition**
- **Setup**:
  - Child process definition marked disabled/archived
- **Steps**:
  1. Execute Call Activity to disabled process
  2. Verify handling
- **Expected**:
  - ✅ Error: "Process definition not active"
  - ✅ Parent marked FAILED
  - ✅ Clear error message

**Test Matrix**: Unit ✅ | Integration ✅ | E2E ⏳

---

## Test Coverage Matrix

| Scenario | Unit | Integration | E2E | Priority |
|----------|------|-------------|-----|----------|
| **1. Basic Call Activity** | ✅ | ✅ | ✅ | CRITICAL |
| **2. Variable Mapping** | ✅ | ✅ | ✅ | CRITICAL |
| **3. Error Handling** | ✅ | ✅ | ✅ | CRITICAL |
| **4. Nesting (2-level)** | ✅ | ✅ | ✅ | CRITICAL |
| **4. Nesting (3+ level)** | ✅ | ✅ | ⏳ | HIGH |
| **5. Execution States** | ✅ | ✅ | ✅ | HIGH |
| **6. Edge Cases** | ✅ | ✅ | ⏳ | MEDIUM |

**Legend**:
- ✅ = Fully automated/implemented
- ⏳ = Manual testing or follow-up phase
- Color: Green = ready, Yellow = in-progress, Red = blocked

---

## Test Metrics & Success Criteria

### Code Coverage Targets
- **Unit Tests**: ≥95% code coverage for `CallActivityHandler.kt`, `VariableMappingService.kt`
- **Integration Tests**: All 6 scenario paths covered
- **E2E Tests**: Critical paths (scenarios 1-3, 5) fully automated

### Performance Targets
- Call Activity invocation: < 100ms
- Variable mapping: < 50ms per variable
- Nesting depth 10: < 500ms total overhead

### Error Handling
- All error cases caught with meaningful messages
- No silent failures
- All errors logged with context

### Data Consistency
- No orphaned instances
- Parent-child relationships maintained
- Variable isolation guaranteed
- No data loss on failures

---

## Test Execution Timeline

### Week 1: Unit Tests
- Date: April 22-26, 2026
- Tasks: T4.1.1-1.3, T4.2.1-2.4, T4.3.1-3.4, T4.4.1-4.3, T4.5.1-5.4, T4.6.1-6.5
- Output: Test results, coverage report
- Status: 🟢 In Progress

### Week 2: Integration Tests
- Date: April 29 - May 3, 2026
- Tasks: Full scenario execution
- Output: Integration test report
- Status: ⏳ Planned

### Week 3: E2E Tests
- Date: May 6-10, 2026
- Tasks: User workflow validation
- Output: E2E test report, UAT sign-off
- Status: ⏳ Planned

---

## Regression Testing

Verify no impact on existing functionality:
- Standard process execution (non-call-activity processes)
- Variable assignment and retrieval
- Error boundary handling (non-call-activity)
- Admin UI operations
- Modeler functionality

---

## Reporting

Each test week will generate:
1. Test Execution Report (pass/fail counts, coverage)
2. Defect Report (bugs found, severity, resolution)
3. Performance Report (throughput, latency metrics)
4. Coverage Report (code coverage %, gap analysis)

---

## Sign-Off Criteria

Phase 7.4 QA Testing is complete when:
- ✅ All CRITICAL scenarios pass (1-3, 5)
- ✅ All HIGH scenarios pass (4.1-4.2)
- ✅ Code coverage ≥95% for call activity code
- ✅ No critical or high-severity defects
- ✅ Performance within targets
- ✅ UAT sign-off from product owner
