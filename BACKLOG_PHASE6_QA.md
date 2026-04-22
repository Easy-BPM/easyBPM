# Phase 6 QA Improvements - Backlog & Tasks

## Overview

4 QA improvements identified from testing rounds, prioritized, and broken into actionable backlog items. Total estimated effort: **7-11 days**.

---

## Priority Matrix

| Task | Priority | Effort | Repos | Status |
|------|----------|--------|-------|--------|
| Error Catch Variable Mapping | 🔴 CRITICAL | 3-5 days | Backend, Modeler, Admin | Not Started |
| Disable Spaces in ID Fields | 🟠 HIGH | 1-2 days | Modeler | Not Started |
| Canvas Arrows + Boundary Events | 🟠 HIGH | 2-3 days | Admin | Not Started |
| Improve Hibernate Logging | 🟡 MEDIUM | 0.5-1 day | Backend | Not Started |

---

## CRITICAL: Error Catch Variable Mapping

### User Story
As an admin, I want error messages from failed tasks to be captured in process variables so I can track what went wrong. When I view an incident in the Admin UI, I should see the error message and incident status.

### Acceptance Criteria
- ✅ Error message is captured from exception and mapped to process variable (if `exceptionVariable` is configured)
- ✅ Modeler Error Boundary UI shows `exceptionVariable` mapping field
- ✅ Admin Incident panel displays error message and status
- ✅ Error tracking works for: system errors, business errors, timeouts, API failures
- ✅ Integration tests validate error-to-variable flow
- ✅ Docs updated with error handling examples

### Tasks

#### Backend - Error Handler Enhancement
```
ID: T6.1.1
Title: Extend ErrorCatchHandler to capture error message
Complexity: Medium
Effort: 1 day

Description:
- Modify ErrorCatchHandler to extract exception message
- Add errorMessage field to error event context
- Apply output mapping for exceptionVariable (if configured)
- Log error with context for observability

Files:
- src/main/kotlin/com/easy/bpm/handler/ErrorCatchHandler.kt
- src/main/kotlin/com/easy/bpm/model/Event.kt (add errorMessage field)

Done Criteria:
- ErrorCatchHandler captures and logs exception message
- Output mapping applies exceptionVariable to process variables
- No regression in existing error handling tests
```

#### Backend - Integration Test
```
ID: T6.1.2
Title: Write integration tests for error-to-variable mapping
Complexity: Medium
Effort: 1.5 days

Description:
- Test error thrown in service task
- Verify error message captured in process variable
- Test with different error types (Exception, TimeoutException, etc.)
- Test incident status propagation
- Test multiple catch handlers in same process

Files:
- src/test/kotlin/com/easy/bpm/ErrorHandlingIntegrationTest.kt

Done Criteria:
- All error scenarios covered
- Tests pass with BUILD SUCCESSFUL
- Error message accuracy verified
```

#### Modeler - Error Boundary Properties
```
ID: T6.1.3
Title: Add exceptionVariable field to Error Boundary UI
Complexity: Medium
Effort: 1 day

Description:
- Add "Exception Variable Name" input field in Error Boundary properties
- Store exceptionVariable in error event XML output
- Show validation error if not a valid variable name
- Display example: "Use exceptionVariable='errorDetails' to capture error"

Files:
- easybpmn-modeler/components/PropertiesPanel.tsx
- easybpmn-modeler/utils/bpmnUtils.ts (XML generation)

Done Criteria:
- exceptionVariable field appears when Error Boundary selected
- Value saved to process XML on deploy
- Validation prevents invalid variable names (spaces, special chars)
```

#### Admin UI - Incident Error Display
```
ID: T6.1.4
Title: Display error message in incident detail view
Complexity: Low
Effort: 0.5 days

Description:
- Fetch incident details including error message
- Add error message panel/section in incident detail view
- Display incident status (e.g., ACTIVE, RESOLVED)
- Format error message for readability (code blocks, line breaks)

Files:
- easy-bpm-admin/components/IncidentPanel.tsx
- easy-bpm-admin/services/bpmService.ts (fetch incident details)

Done Criteria:
- Error message visible in incident view
- Incident status displayed
- Error formatting is readable
```

#### Documentation
```
ID: T6.1.5
Title: Document error handling with variable mapping
Complexity: Low
Effort: 0.5 days

Description:
- Add section to error handling guide
- Provide example: Service Task → Error Boundary → Capture to Variable
- Show Admin UI screenshot of incident with error
- Best practices for error tracking

Files:
- docs-site-working/docs/error-handling.md (new section)
- docs-site-working/docs/easy-admin-features.md (update incident section)

Done Criteria:
- Documentation is clear and includes examples
- Screenshots show UI elements
```

---

## HIGH: Disable Spaces in ID Fields (Modeler)

### User Story
As a modeler, I want to ensure process and form IDs don't contain spaces so they work correctly with the backend API and process lookups.

### Acceptance Criteria
- ✅ Process ID field rejects spaces and shows error message
- ✅ Form ID field rejects spaces and shows error message
- ✅ Form Key field (in User Task) rejects spaces and shows error message
- ✅ Regex pattern: `^[a-zA-Z0-9_-]+$` (alphanumeric, underscore, hyphen only)
- ✅ User-friendly error messages displayed inline
- ✅ Copy-paste with spaces is handled gracefully

### Tasks

#### Modeler - Process ID Validation
```
ID: T6.2.1
Title: Add validation to Process ID field
Complexity: Low
Effort: 0.5 days

Description:
- Add regex validation: ^[a-zA-Z0-9_-]+$
- Show error on blur if space detected
- Show red border on invalid input
- Prevent deploy if Process ID is invalid

Files:
- easybpmn-modeler/components/Modeler.tsx (Process ID input)
- easybpmn-modeler/utils/validation.ts (add ID validation helper)

Done Criteria:
- Space characters rejected
- Error message shown: "Process ID can only contain letters, numbers, hyphens, and underscores"
- No deploy allowed with invalid ID
```

#### Modeler - Form ID Validation
```
ID: T6.2.2
Title: Add validation to Form ID field
Complexity: Low
Effort: 0.5 days

Description:
- Add regex validation: ^[a-zA-Z0-9_-]+$
- Show error on blur if space detected
- Show red border on invalid input
- Prevent save if Form ID is invalid

Files:
- easybpmn-modeler/components/FormModeler.tsx (Form ID input)
- easybpmn-modeler/utils/validation.ts (reuse validation helper)

Done Criteria:
- Space characters rejected
- Error message shown
- No save allowed with invalid ID
```

#### Modeler - Form Key Validation (User Task)
```
ID: T6.2.3
Title: Add validation to Form Key field in User Task
Complexity: Low
Effort: 0.5 days

Description:
- Add regex validation: ^[a-zA-Z0-9_-]+$
- Show error on blur if space detected
- Show red border on invalid input
- User Task should not allow save with invalid Form Key

Files:
- easybpmn-modeler/components/PropertiesPanel.tsx (Form Key field)
- easybpmn-modeler/utils/validation.ts (reuse validation helper)

Done Criteria:
- Space characters rejected in Form Key
- Error message shown
- Invalid Form Key prevents User Task deployment
```

#### Testing
```
ID: T6.2.4
Title: Test ID field validation with various inputs
Complexity: Low
Effort: 0.5 days

Description:
- Test with spaces: "my process" (should fail)
- Test with special chars: "my-process!", "my_form#" (should fail)
- Test with valid chars: "my-process", "myForm_01" (should pass)
- Test copy-paste with spaces
- Test unicode characters

Files:
- Manual testing (or add UI tests if available)

Done Criteria:
- All scenarios tested
- Error messages clear and helpful
```

---

## HIGH: Admin Canvas Rendering (Arrows + Boundary Events)

### User Story
As an admin, I want to see process diagrams rendered correctly in the Admin UI with proper arrow styling and boundary events visible so I can understand the process structure.

### Acceptance Criteria
- ✅ Arrows render with correct BPMN styling (line weight, color, markers)
- ✅ Boundary events are detected from process definition and rendered
- ✅ Complex processes with multiple boundary events render correctly
- ✅ Visual consistency with Easy BPMN Modeler
- ✅ No rendering errors in browser console

### Tasks

#### Admin UI - Canvas Rendering Audit
```
ID: T6.3.1
Title: Audit and document current canvas rendering code
Complexity: Medium
Effort: 1 day

Description:
- Review SVG/canvas rendering implementation
- Identify arrow rendering logic
- Identify boundary event detection logic
- Document current limitations
- Compare with Easy BPMN Modeler source

Files:
- easy-bpm-admin/components/ (diagram/canvas components)
- easy-bpm-admin/services/diagramService.ts (if exists)

Done Criteria:
- Rendering code documented
- Issues identified and listed
- Comparison with modeler completed
```

#### Admin UI - Fix Arrow Styling
```
ID: T6.3.2
Title: Fix SVG arrow styling to match BPMN standard
Complexity: Medium
Effort: 1 day

Description:
- Update arrow line styling (width, color)
- Fix marker-end (arrow head) rendering
- Ensure consistent style across all connections
- Test with various edge cases (long connections, overlapping paths)

Files:
- easy-bpm-admin/components/ProcessCanvas.tsx (or similar)
- easy-bpm-admin/styles/ (add/update arrow styles if needed)

Done Criteria:
- Arrows render with correct style
- Arrow heads visible and properly positioned
- No visual regression
```

#### Admin UI - Boundary Event Rendering
```
ID: T6.3.3
Title: Add boundary event detection and rendering
Complexity: High
Effort: 1.5 days

Description:
- Parse process definition for boundary events
- Identify parent task/subprocess for each boundary event
- Render boundary events on parent boundary (left side)
- Handle multiple boundary events on same parent
- Style boundary events consistently

Files:
- easy-bpm-admin/components/ProcessCanvas.tsx
- easy-bpm-admin/services/diagramService.ts

Done Criteria:
- Boundary events rendered on parent boundaries
- No overlapping or positioning issues
- Works with multiple boundary events
- Consistent visual style
```

#### Admin UI - Testing & Validation
```
ID: T6.3.4
Title: Test canvas rendering with complex processes
Complexity: Medium
Effort: 0.5 days

Description:
- Deploy test process with multiple boundary events
- Deploy process with long sequences and overlapping paths
- Deploy process with nested tasks
- Verify rendering in browser at different zoom levels
- Check browser console for errors

Files:
- Manual testing using Admin UI

Done Criteria:
- No rendering errors
- All elements visible and correctly positioned
- Works at different zoom levels
- No console errors
```

#### Documentation
```
ID: T6.3.5
Title: Document canvas rendering capabilities and limitations
Complexity: Low
Effort: 0.5 days

Description:
- Update Admin UI guide with rendering info
- Document boundary event visualization
- Note any known limitations
- Provide troubleshooting tips

Files:
- docs-site-working/docs/easy-admin-features.md (update canvas section)

Done Criteria:
- Documentation is clear
- Limitations documented
```

---

## MEDIUM: Improve Hibernate Logging

### User Story
As a developer, I want to disable noisy Hibernate query logging so I can see important application logs without SQL query pollution.

### Acceptance Criteria
- ✅ SQL query logging is disabled in all environments
- ✅ Error and warning logs are still visible
- ✅ Docker-compose environment has clean logs
- ✅ Configuration documented

### Tasks

#### Backend - Configure Logging
```
ID: T6.4.1
Title: Disable Hibernate query logging
Complexity: Low
Effort: 0.5 days

Description:
- Update logback-spring.xml to disable Hibernate debug logs
- Set spring.jpa.show-sql=false
- Set Hibernate logging level to WARN (not DEBUG)
- Test in local, test, and docker environments

Files:
- src/main/resources/logback-spring.xml
- src/main/resources/application.properties (or application.yml)

Done Criteria:
- No SQL queries in logs
- Warnings and errors still visible
- Clean log output in docker-compose logs
```

#### Documentation
```
ID: T6.4.2
Title: Document logging configuration
Complexity: Low
Effort: 0.5 days

Description:
- Add logging configuration section to developer guide
- Document how to change log levels for troubleshooting
- Show how to enable query logging if needed (for debugging)

Files:
- docs-site-working/docs/developer-quick-reference.md (add logging section)

Done Criteria:
- Configuration documented
- Instructions clear
```

---

## Implementation Roadmap

### Phase 6 Sprint 1 (This Sprint)
1. **T6.1.1** - Error Handler Enhancement (Backend)
2. **T6.2.1-2.3** - ID Field Validation (Modeler, 3 tasks)
3. **T6.1.3** - Error Boundary UI (Modeler)

**Sprint 1 Effort**: ~4 days
**Sprint 1 Repos**: Backend, Modeler

### Phase 6 Sprint 2 (Next Sprint)
1. **T6.1.2** - Error Integration Tests
2. **T6.3.1-3.4** - Canvas Rendering (Admin, 4 tasks)
3. **T6.4.1** - Hibernate Logging (Backend)
4. **T6.1.4-1.5 + T6.3.5 + T6.4.2** - Documentation & Admin UI

**Sprint 2 Effort**: ~7 days
**Sprint 2 Repos**: Backend, Admin, Documentation

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Error mapping affects existing error flow | High | Comprehensive integration tests before merging |
| Canvas rendering changes break existing displays | High | Test with all known process types |
| ID validation too strict | Medium | Support common patterns (hyphens, underscores) |
| Hibernate logging breaks observability | Low | Keep error/warning levels active |

---

## Success Criteria

- ✅ All 4 improvements implemented and tested
- ✅ No regression in existing functionality (tests pass)
- ✅ Error tracking provides actionable error details
- ✅ ID fields accept only valid characters
- ✅ Canvas renders correctly with boundary events
- ✅ Logs are clean and focused
- ✅ Documentation updated for all changes
- ✅ Team can understand and maintain new code
