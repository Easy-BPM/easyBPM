---
subject: TASK DELEGATION - EPIC CALL-001 Execution
from: CTO
to: Process Orchestrator Team
date: 2026-04-22
sprint: Sprint 1-4 (4 weeks)
total_effort: 40 story_points
---

# TASK DELEGATION: Call Activity & Subprocess Support

**Epic**: EPIC-CALL-001  
**Duration**: 4 weeks (4 sprints)  
**Kickoff**: 2026-04-23  
**Target Completion**: 2026-06-30

---

## 🎯 Mission Statement

Implement call activity node support enabling process definitions to invoke child subprocesses with full variable mapping, error handling, and execution isolation. This will unlock process composition patterns and reduce workflow duplication.

---

## 📋 Role Assignments & Task Breakdown

### 1️⃣ BACKEND DEVELOPER
**Assigned**: [TBD]  
**Effort**: 16 story_points  
**Duration**: 2 weeks intensive + 1 week integration testing  
**Status**: READY TO START

#### Sprint 1 Tasks

**T7.1.1: Database Migration (V17)** [3 sp]
- **Description**: Extend process_instance table with call activity support
- **Acceptance Criteria**:
  - [ ] Add `parent_instance_id` BIGINT NULL column
  - [ ] Add `call_activity_node_id` VARCHAR(255) NULL column
  - [ ] Add `nesting_level` INT DEFAULT 0 column
  - [ ] Add `completion_node_id` VARCHAR(255) NULL column
  - [ ] Create index on `parent_instance_id`
  - [ ] Create index on `call_activity_node_id`
  - [ ] Create `call_activity_mapping` table with input/output mappings
  - [ ] Flyway migration runs cleanly on fresh DB
  - [ ] Flyway migration runs cleanly on existing DBs (v1-v16)
  - [ ] No data loss on existing instances

**Code Output**: `src/main/resources/db/migration/V17__call_activity_support.sql`

**Definition of Done**:
- [ ] SQL script reviewed by Code Review Engineer
- [ ] Migration tested on PostgreSQL 13+
- [ ] Indexes verify with EXPLAIN ANALYZE
- [ ] Schema diagram updated in documentation

---

**T7.1.2: CallActivityHandler Service** [4 sp]
- **Description**: Create handler for executing call activity nodes
- **Acceptance Criteria**:
  - [ ] Execute call activity node from ProcessService
  - [ ] Create child process instance with parent context
  - [ ] Set `parentInstanceId`, `callActivityNodeId`, `nestingLevel`
  - [ ] Register child in call_activity_mapping table
  - [ ] Block parent execution until child completes
  - [ ] Handle child process completion event
  - [ ] Resume parent on child completion
  - [ ] Support max nesting depth (configurable, default 10)
  - [ ] Reject nesting beyond max depth
  - [ ] Unit tests for all scenarios

**Code Output**:
- `src/main/kotlin/com/easy/bpm/service/CallActivityHandler.kt`
- `src/main/kotlin/com/easy/bpm/repository/CallActivityMappingRepository.kt`
- Integration with `ProcessService.executeNode()`

**Definition of Done**:
- [ ] Code reviewed by Code Review Engineer
- [ ] 90%+ code coverage in unit tests
- [ ] No cyclomatic complexity > 10
- [ ] Follows existing code patterns
- [ ] Error handling comprehensive

---

**T7.1.3: Variable Mapping Engine** [3 sp]
- **Description**: Implement input/output variable mapping for call activities
- **Acceptance Criteria**:
  - [ ] Input mapping: parent variable → child variable
  - [ ] Output mapping: child variable → parent variable
  - [ ] Support `propagateAllVariables` flag
  - [ ] Handle missing input variables gracefully
  - [ ] Support nested property access (e.g., `order.customerId`)
  - [ ] Type preservation (no accidental stringification)
  - [ ] Unit tests for all mapping scenarios
  - [ ] Performance: map 100 variables in < 50ms

**Code Output**:
- `src/main/kotlin/com/easy/bpm/service/VariableMappingService.kt`
- Tests in `src/test/kotlin/.../VariableMappingServiceTest.kt`

**Definition of Done**:
- [ ] All mapping test cases passing (15+ test cases)
- [ ] No data loss or type corruption
- [ ] Handles edge cases (null, missing, complex objects)

---

**T7.1.4: Instance Lifecycle Management** [3 sp]
- **Description**: Manage parent-child instance relationships and execution state
- **Acceptance Criteria**:
  - [ ] Parent instance suspended while child executes
  - [ ] Parent currentNode updated when entering call activity
  - [ ] Parent currentNode cleared when child completes
  - [ ] Child instance status = ACTIVE while executing
  - [ ] Child instance status = COMPLETED on finish
  - [ ] Proper state transitions for all scenarios
  - [ ] Parent-child relationship persisted correctly
  - [ ] Query methods to get parent instance from child
  - [ ] Query methods to get all children of parent

**Code Output**:
- Updated `ProcessService.executeNode()` and `finishProcess()`
- New repository methods in `ProcessInstanceRepository`
- Tests in `ProcessServiceTest.kt`

**Definition of Done**:
- [ ] State machine diagram verified by CTO
- [ ] No orphaned instances in database
- [ ] Parent-child queries optimized with indexes

---

**T7.1.5: Error Handling & Propagation** [2 sp]
- **Description**: Handle errors in child processes and propagate to parent
- **Acceptance Criteria**:
  - [ ] Child process error → parent error boundary
  - [ ] Error message from child available in parent
  - [ ] exceptionVariable in parent receives child error
  - [ ] Parent can compensate on child failure
  - [ ] Proper error state tracking
  - [ ] Unit tests for error scenarios (5+ cases)
  - [ ] Integration test for end-to-end error flow

**Code Output**:
- Error handling in `CallActivityHandler`
- Tests in `ErrorHandlingIntegrationTest`

**Definition of Done**:
- [ ] Error flows tested from child to parent
- [ ] Compensation chains work correctly

---

**T7.1.6: Integration Tests & Validation** [1 sp]
- **Description**: Comprehensive integration test suite
- **Acceptance Criteria**:
  - [ ] Test: Simple call activity execution
  - [ ] Test: Variable input mapping
  - [ ] Test: Variable output mapping
  - [ ] Test: 2-level nesting (A → B)
  - [ ] Test: 3-level nesting (A → B → C)
  - [ ] Test: Error in child, parent handles
  - [ ] Test: Timeout in child
  - [ ] Test: Circular reference detection
  - [ ] Test: 95%+ code coverage overall
  - [ ] All tests pass in CI/CD

**Code Output**: Integration tests in `src/test/kotlin/`

**Definition of Done**:
- [ ] `./gradlew test` all pass
- [ ] Coverage report > 95%
- [ ] No flaky tests

---

#### Sprint 3 Tasks

**T7.1.7: Performance Optimization** [Adjustment]
- Profile child instance creation
- Optimize variable mapping for large datasets
- Add caching for process definition lookups
- Benchmark nesting depth impact

---

### 2️⃣ FRONTEND DEVELOPER (Modeler)
**Assigned**: [TBD]  
**Effort**: 8 story_points (Modeler) + 6 story_points (Admin) = 14 sp total  
**Duration**: 2.5 weeks  
**Status**: READY TO START

#### Sprint 2 Tasks (Modeler)

**T7.2.1: Call Activity Palette Item** [2 sp]
- **Description**: Add call activity icon to modeler palette
- **Acceptance Criteria**:
  - [ ] CallActivity icon added to node palette
  - [ ] Icon visually distinct from regular tasks
  - [ ] Drag-drop from palette to canvas works
  - [ ] Node created with type = "CallActivity"
  - [ ] Default properties applied
  - [ ] Icon consistent with BPMN 2.0 standard

**File**: `easybpmn-modeler/components/Palette.tsx`

**Definition of Done**:
- [ ] TypeScript compilation zero errors
- [ ] Icon renders correctly at all zoom levels
- [ ] Drag-drop works smoothly

---

**T7.2.2: Call Activity Properties Panel** [3 sp]
- **Description**: UI for configuring call activity
- **Acceptance Criteria**:
  - [ ] Process key selector (dropdown from deployed processes)
  - [ ] Input variable mapping editor (add/remove rows)
  - [ ] Output variable mapping editor (add/remove rows)
  - [ ] Propagate all variables checkbox
  - [ ] Error boundary config section
  - [ ] Real-time validation
  - [ ] Error messages for invalid mappings
  - [ ] TypeScript strict mode compliance

**File**: `easybpmn-modeler/components/PropertiesPanel.tsx` (extend)

**Definition of Done**:
- [ ] All input fields functional
- [ ] Validation working
- [ ] State management correct
- [ ] No TypeScript errors

---

**T7.2.3: Canvas Rendering for Call Activities** [1.5 sp]
- **Description**: Visual representation on canvas
- **Acceptance Criteria**:
  - [ ] Call activity rendered as distinct node shape
  - [ ] Subprocess icon indicator on node
  - [ ] Tooltip showing target process name
  - [ ] Color scheme matches BPMN standard
  - [ ] No performance regression on large processes

**File**: `easybpmn-modeler/components/Canvas.tsx` (extend)

**Definition of Done**:
- [ ] Renders correctly on all browsers
- [ ] Canvas zoom/pan still works

---

**T7.2.4: Process Key Validation & Auto-Complete** [1.5 sp]
- **Description**: Validate and assist with process selection
- **Acceptance Criteria**:
  - [ ] Load list of available process definitions
  - [ ] Process key dropdown with search
  - [ ] Warn if target process doesn't exist
  - [ ] Show target process details
  - [ ] Prevent circular references (A → B → A)
  - [ ] Detect nesting depth exceeds limit

**File**: `easybpmn-modeler/services/bpmService.ts` (extend)

**Definition of Done**:
- [ ] Dropdown populated correctly
- [ ] Circular reference detection works
- [ ] Performance acceptable for 100+ processes

---

#### Sprint 2-3 Tasks (Admin UI)

**T7.3.1: Instance Hierarchy Visualization** [2 sp]
- **Description**: Show parent-child instance relationships
- **Acceptance Criteria**:
  - [ ] Display parent instance ID in child view
  - [ ] Breadcrumb navigation: child → parent → root
  - [ ] List all child instances of a parent
  - [ ] Click to navigate between parent/child
  - [ ] Visual tree structure showing nesting

**File**: `easy-bpm-admin/components/InstanceDetail.tsx` (new section)

**Definition of Done**:
- [ ] Navigation works smoothly
- [ ] Responsive on mobile
- [ ] No TypeScript errors

---

**T7.3.2: Child Instance Details** [2 sp]
- **Description**: View detailed info about child instances
- **Acceptance Criteria**:
  - [ ] Show child process definition name
  - [ ] Display child execution history
  - [ ] Show child process variables
  - [ ] Display mapping applied (input/output)
  - [ ] Show child status and completion time
  - [ ] Link to child process definition

**File**: `easy-bpm-admin/components/InstanceDetail.tsx` (extend)

**Definition of Done**:
- [ ] All information displays correctly
- [ ] No performance issues with many children

---

**T7.3.3: Variable Mapping Audit Trail** [2 sp]
- **Description**: Display variable mapping details
- **Acceptance Criteria**:
  - [ ] Show input mappings applied
  - [ ] Display output mappings applied
  - [ ] Show before/after variable values
  - [ ] Timeline of mapping events
  - [ ] Export audit trail as JSON

**File**: `easy-bpm-admin/components/VariableMappingAudit.tsx` (new)

**Definition of Done**:
- [ ] Audit trail complete and accurate
- [ ] Performance acceptable

---

### 3️⃣ QA ENGINEER
**Assigned**: [TBD]  
**Effort**: 6 story_points  
**Duration**: 3 weeks (parallel with development)  
**Status**: READY TO START

#### Sprint 1-3 Tasks

**T7.4.1: Test Plan & Scenarios** [1 sp]
- **Description**: Define comprehensive test scenarios
- **Deliverables**:
  - [ ] 40+ test cases documented
  - [ ] Test matrix (scenarios × test types)
  - [ ] Edge case identification
  - [ ] Performance test plan
  - [ ] Load test plan for nesting depth

**Definition of Done**:
- [ ] Test plan reviewed by CTO
- [ ] All scenarios traced to acceptance criteria

---

**T7.4.2: Basic Call Activity Tests** [1 sp]
- **Description**: Unit and integration tests for core functionality
- **Test Cases**:
  - [ ] T1: Simple call activity execution
  - [ ] T2: Call activity with input mapping
  - [ ] T3: Call activity with output mapping
  - [ ] T4: Call activity with all variables propagated
  - [ ] T5: Call activity completes and resumes parent

**Definition of Done**:
- [ ] All tests passing
- [ ] Automated in CI/CD

---

**T7.4.3: Nesting & Complex Scenarios** [1.5 sp]
- **Description**: Test multi-level nesting
- **Test Cases**:
  - [ ] T6: 2-level nesting (A → B)
  - [ ] T7: 3-level nesting (A → B → C)
  - [ ] T8: Variables flow through all levels
  - [ ] T9: Error in child propagates to root parent
  - [ ] T10: Timeout at any level handled correctly

**Definition of Done**:
- [ ] All tests passing
- [ ] Performance acceptable

---

**T7.4.4: Error Handling & Edge Cases** [1.5 sp]
- **Description**: Test failure scenarios
- **Test Cases**:
  - [ ] T11: Child process fails, parent catches error
  - [ ] T12: Child timeout, parent continues
  - [ ] T13: Missing target process definition
  - [ ] T14: Variable mapping with nulls/missing values
  - [ ] T15: Circular reference detected and rejected
  - [ ] T16: Max nesting depth exceeded
  - [ ] T17: Concurrent children execution

**Definition of Done**:
- [ ] All error cases covered
- [ ] No unhandled exceptions

---

**T7.4.5: Performance & Load Testing** [1 sp]
- **Description**: Verify performance under load
- **Tests**:
  - [ ] Benchmark: child creation < 500ms
  - [ ] Benchmark: variable mapping < 50ms per variable
  - [ ] Load test: 1000 concurrent parent instances
  - [ ] Load test: 5 nesting levels with 100 children each
  - [ ] Memory profile: no memory leaks

**Definition of Done**:
- [ ] Performance metrics documented
- [ ] No regressions in other features

---

### 4️⃣ TECH WRITER
**Assigned**: [TBD]  
**Effort**: 4 story_points  
**Duration**: 1.5 weeks (Sprint 3-4)  
**Status**: READY TO START

#### Sprint 3-4 Tasks

**T7.5.1: Call Activity Guide** [1 sp]
- **Deliverable**: `docs-site-working/docs/easy-modeler-call-activity.md`
- **Contents**:
  - [ ] What are call activities
  - [ ] When to use them vs. inline logic
  - [ ] Step-by-step: create a call activity
  - [ ] Screenshots of modeler UI
  - [ ] Common patterns
  - [ ] Best practices

**Definition of Done**:
- [ ] Document reviewed by Backend Developer
- [ ] Examples runnable
- [ ] Screenshots current

---

**T7.5.2: Variable Mapping Tutorial** [1 sp]
- **Deliverable**: `docs-site-working/docs/call-activity-variable-mapping.md`
- **Contents**:
  - [ ] Input mapping explanation with examples
  - [ ] Output mapping explanation with examples
  - [ ] Propagate all variables usage
  - [ ] Variable name conflict resolution
  - [ ] JSON/complex object mapping
  - [ ] Common mistakes & how to fix them

**Definition of Done**:
- [ ] Examples tested
- [ ] Diagrams clear

---

**T7.5.3: Error Handling & Compensation** [1 sp]
- **Deliverable**: `docs-site-working/docs/call-activity-error-handling.md`
- **Contents**:
  - [ ] Error boundaries on call activities
  - [ ] Error propagation from child → parent
  - [ ] Compensation strategies
  - [ ] Examples: payment rollback on validation error
  - [ ] Troubleshooting guide

**Definition of Done**:
- [ ] Verified by Backend Developer
- [ ] Examples realistic

---

**T7.5.4: Real-World Examples** [0.5 sp]
- **Deliverable**: `docs-site-working/docs/call-activity-examples.md`
- **Contents**:
  - [ ] Example 1: Order processing with subprocesses
  - [ ] Example 2: Payment processing chain
  - [ ] Example 3: Multi-level approval workflow
  - [ ] Example 4: Error handling scenarios
  - [ ] Each with process JSON + explanation

**Definition of Done**:
- [ ] All examples deployable
- [ ] Run through modeler successfully

---

**T7.5.5: API Reference Updates** [0.5 sp]
- **Deliverable**: Update `docs-site-working/docs/api-controllers.md`
- **Contents**:
  - [ ] New endpoints documented
  - [ ] Call activity request/response schemas
  - [ ] Variable mapping schema
  - [ ] Error scenarios
  - [ ] Code examples

**Definition of Done**:
- [ ] API calls verified working
- [ ] Examples executable

---

### 5️⃣ CODE REVIEW ENGINEER
**Assigned**: [TBD]  
**Role**: Continuous during all sprints  
**Effort**: 3-4 hours per sprint  
**Status**: READY TO START

#### Continuous Review Tasks

**T7.R.1: Architecture Review**
- Review call activity handler design for correctness
- Validate parent-child isolation
- Verify variable mapping correctness
- Check error handling completeness
- Confirm performance characteristics

**T7.R.2: Code Quality Review**
- Ensure code follows existing patterns
- Check for technical debts
- Verify test coverage > 95%
- Validate error messages are clear
- Confirm no security issues

**T7.R.3: Integration Testing Review**
- Verify test scenarios comprehensive
- Check for edge case coverage
- Validate performance tests
- Confirm CI/CD integration

**T7.R.4: Production Readiness**
- Database migration safety
- Error handling robustness
- Performance under load
- Documentation quality
- Deployment readiness

---

## 📅 Sprint Schedule

### Sprint 1 (Week 1-2)
**Focus**: Backend core implementation

**Backend**:
- T7.1.1: Database migration ✅
- T7.1.2: CallActivityHandler ✅
- T7.1.3: Variable mapping engine ✅
- T7.1.4: Instance lifecycle (in progress)

**QA**:
- T7.4.1: Test plan definition ✅

**Review**: Daily code reviews, architecture validation

**Outcomes**:
- [ ] Core backend feature functional
- [ ] Basic tests passing
- [ ] Database clean migration

---

### Sprint 2 (Week 2-3)
**Focus**: Modeler UI + continue backend

**Backend**:
- T7.1.4: Instance lifecycle (complete)
- T7.1.5: Error handling
- Begin T7.1.6: Integration tests

**Frontend (Modeler)**:
- T7.2.1: Palette item ✅
- T7.2.2: Properties panel ✅
- T7.2.3: Canvas rendering ✅
- T7.2.4: Validation ✅

**QA**:
- T7.4.2: Basic call activity tests
- Exploratory testing

**Outcomes**:
- [ ] Modeler supports creating call activities
- [ ] Backend handles call execution
- [ ] Basic tests passing

---

### Sprint 3 (Week 3-4)
**Focus**: Admin UI + comprehensive testing + documentation

**Backend**:
- T7.1.6: Integration tests (complete)
- Performance profiling

**Frontend (Admin)**:
- T7.3.1: Instance hierarchy visualization ✅
- T7.3.2: Child instance details ✅
- T7.3.3: Variable mapping audit ✅

**QA**:
- T7.4.3: Nesting tests
- T7.4.4: Error handling tests
- T7.4.5: Performance tests

**Tech Writer**:
- T7.5.1: Call activity guide
- T7.5.2: Variable mapping tutorial
- T7.5.3: Error handling guide

**Outcomes**:
- [ ] 95%+ test coverage
- [ ] All tests passing
- [ ] Documentation complete

---

### Sprint 4 (Week 4 - Optional)
**Focus**: Polish, optimization, deployment prep

**All Teams**:
- Bug fixes from testing
- Performance optimization
- Documentation refinement
- Production readiness verification

**Outcomes**:
- [ ] Ready for production deployment
- [ ] All stakeholders signed off
- [ ] Runbooks prepared

---

## ✅ Definition of Done

### For Each Task
- [ ] Code written to specification
- [ ] Unit tests > 80% coverage
- [ ] Integration tests passing
- [ ] Code reviewed and approved
- [ ] No TypeScript/Kotlin errors
- [ ] Documentation updated
- [ ] Performance acceptable

### For Sprint
- [ ] All tasks completed
- [ ] All tests passing
- [ ] No regression in other features
- [ ] Code review approved
- [ ] Ready for next sprint

### For Epic
- [ ] All 25 tasks completed
- [ ] 95%+ test coverage
- [ ] Documentation comprehensive
- [ ] Performance benchmarks met
- [ ] Security validated
- [ ] Production ready

---

## 🚀 Execution Guidelines

### Daily Standups
- **Time**: 09:00 UTC
- **Duration**: 15 minutes
- **Topics**: Blockers, progress, dependencies
- **Attendees**: Backend, Frontend, QA, Code Review

### Code Review Process
1. Developer opens pull request
2. Code Review Engineer reviews within 24h
3. Feedback provided (changes or approval)
4. Developer addresses feedback
5. Reviewer approves when satisfied
6. Merge to develop branch

### Escalation Path
1. **Task-level**: Discuss in daily standup
2. **Sprint-level**: Discuss in sprint review
3. **Epic-level**: Escalate to CTO

### Risk Mitigation
- Circular reference detection is non-negotiable
- Performance testing mandatory before launch
- Database migration tested on all versions
- Error handling comprehensive before code review approval

---

## 📊 Success Metrics

### By Role

**Backend Developer**:
- [ ] All backend tasks completed on time
- [ ] 95%+ code coverage
- [ ] All integration tests passing
- [ ] Zero production issues in first month

**Frontend Developer**:
- [ ] Modeler + Admin UI complete
- [ ] Zero TypeScript errors
- [ ] Responsive on all devices
- [ ] User-friendly variable mapping UI

**QA Engineer**:
- [ ] 40+ test cases executed
- [ ] All edge cases covered
- [ ] Performance benchmarks met
- [ ] Zero escaped defects to production

**Tech Writer**:
- [ ] 5 comprehensive documents
- [ ] All examples runnable
- [ ] User satisfaction > 4/5 stars
- [ ] Zero documentation bugs

**Code Review Engineer**:
- [ ] Zero critical issues missed
- [ ] All code reviewed within SLA
- [ ] Technical debt tracked
- [ ] Architecture decisions validated

---

## 🎯 Next Steps

1. **Today (2026-04-22)**:
   - [ ] Assign roles to team members
   - [ ] Schedule kickoff meeting
   - [ ] Set up project board
   - [ ] Create Jira epics/stories

2. **This Week**:
   - [ ] Kickoff meeting with full team
   - [ ] Architecture walkthrough by CTO
   - [ ] Setup development environment
   - [ ] Begin Sprint 1 planning

3. **Next Week**:
   - [ ] Sprint 1 starts
   - [ ] Daily standups begin
   - [ ] Code reviews active
   - [ ] Progress tracking

---

**Prepared by**: CTO  
**Date**: 2026-04-22  
**Status**: APPROVED FOR DELEGATION  
**Next Review**: 2026-04-29 (Sprint 1 review)

**Questions?** Contact CTO via Slack #epic-call-001 channel.
