# Phase 8.3 - QA Test Scenarios & Acceptance Criteria

**Document Type**: Quality Assurance Planning  
**Epic**: Phase 8.3 - Code Task Admin UI  
**Date**: April 22, 2026  
**QA Lead**: QA Engineer (Process Orchestrator Team)  
**Test Coverage Target**: 100% of user stories, 80%+ code coverage

---

## Test Scenario Framework

Each scenario follows the **Given-When-Then** format (Gherkin):
- **Given**: Preconditions (data state, page state)
- **When**: User actions
- **Then**: Expected outcomes

---

## Story 8.3.1: Execution List View

### Scenario 1.1: View Default Execution List
```
Given: User navigates to /admin/code-tasks/executions
  AND: 50 Code Task executions exist in database
  AND: User is authenticated
When: Page loads
Then:
  ✓ Page title displays "Code Task Executions"
  ✓ List shows first 20 executions (default page size)
  ✓ Table headers: ID, Instance, JAR, Class, Method, Status, Time, Date
  ✓ Pagination controls show "Page 1 of 3"
  ✓ All 20 rows display without errors
  ✓ Status badges are color-coded (green/red/orange)
  ✓ Execution times formatted in milliseconds (e.g., "45 ms")
  ✓ Dates formatted in ISO 8601 (e.g., "2026-04-22T10:35:00")
  ✓ Page load time < 2 seconds
  
Test Cases:
  1.1.1: Assert table has exactly 20 rows
  1.1.2: Assert pagination shows correct page indicator
  1.1.3: Assert all badge colors are correct
  1.1.4: Assert date formatting is consistent
  1.1.5: Verify page load performance (< 2s)
```

### Scenario 1.2: Column Sorting
```
Given: Execution list is displayed with 20+ executions
  AND: Status column contains both COMPLETED and FAILED
When: User clicks "Status" column header
Then:
  ✓ Table re-sorts by Status (ascending: COMPLETED first, then FAILED)
  ✓ Sort indicator arrow appears next to Status header
  ✓ Re-clicking header reverses sort order (descending)
  ✓ Other columns remain in original order
  
When: User clicks "Date" column header
Then:
  ✓ Table sorts by execution date (newest first)
  ✓ Sort order persists when pagination navigates
  
When: User clicks "Time" column header
Then:
  ✓ Table sorts by execution time (longest first)
  
Test Cases:
  1.2.1: Sort by Status ascending
  1.2.2: Sort by Status descending (toggle)
  1.2.3: Sort by Date (newest first)
  1.2.4: Sort by Execution Time (longest first)
  1.2.5: Verify sort persists on page navigation
```

### Scenario 1.3: Pagination Navigation
```
Given: 50 executions exist (3 pages at 20 per page)
  AND: User is on Page 1
When: User clicks "Next" button
Then:
  ✓ Page changes to Page 2
  ✓ Table shows executions 21-40
  ✓ Pagination indicator shows "Page 2 of 3"
  ✓ "Previous" button becomes enabled
  
When: User clicks "Previous" button
Then:
  ✓ Page returns to Page 1
  ✓ "Previous" button becomes disabled
  
When: User is on Page 3 and clicks "Next"
Then:
  ✓ "Next" button remains disabled (no Page 4)
  ✓ Page stays on Page 3
  
Test Cases:
  1.3.1: Navigate from page 1 → 2 → 3
  1.3.2: Navigate backwards from page 3 → 1
  1.3.3: Verify prev/next buttons enable/disable correctly
  1.3.4: Verify page indicator shows correct page number
```

### Scenario 1.4: Status Badge Colors
```
Given: Table displays executions with all statuses
When: Table renders
Then:
  ✓ COMPLETED executions show green badge (#10b981)
  ✓ FAILED executions show red badge (#ef4444)
  ✓ TIMEOUT executions show orange badge (#f59e0b)
  ✓ Badge text matches status value
  
Test Cases:
  1.4.1: Verify COMPLETED badge color (green)
  1.4.2: Verify FAILED badge color (red)
  1.4.3: Verify TIMEOUT badge color (orange)
```

### Scenario 1.5: Empty Result State
```
Given: Database has 0 executions
When: User navigates to executions page
Then:
  ✓ Page loads without errors
  ✓ Empty state message displays: "No executions found"
  ✓ Pagination controls are hidden
  ✓ Filter panel still visible
  
Test Cases:
  1.5.1: Verify empty state message
  1.5.2: Verify no table renders
  1.5.3: Verify filters still functional
```

### Scenario 1.6: Loading State
```
Given: Page is fetching executions from API
When: API call is in flight (> 500ms)
Then:
  ✓ Loading spinner appears in table area
  ✓ Previous data is still visible (don't remove)
  ✓ "Loading..." or skeleton loaders display
  
When: API returns data
Then:
  ✓ Loading indicator disappears
  ✓ Table updates with new data
  
Test Cases:
  1.6.1: Verify skeleton/spinner shows during loading
  1.6.2: Verify loading clears when data arrives
```

---

## Story 8.3.2: Execution Details Modal

### Scenario 2.1: Open Modal
```
Given: Execution list is displayed
  AND: User is on any execution row (e.g., ID: 42)
When: User clicks row
Then:
  ✓ Modal opens with overlay
  ✓ Modal shows execution ID (e.g., "Execution #42")
  ✓ Modal shows instance ID, JAR, Class, Method
  ✓ Modal does not close when clicking inside modal content
  ✓ Modal has semi-transparent overlay behind it
  
Test Cases:
  2.1.1: Click row → modal opens
  2.1.2: Verify modal content renders
  2.1.3: Verify overlay is present
```

### Scenario 2.2: View Input Variables
```
Given: Modal is open for an execution
  AND: Execution has input variables: {"order": {...}, "taxRate": 0.08}
When: User looks at "Input Variables" section
Then:
  ✓ JSON displays with proper formatting (indented)
  ✓ JSON is syntax-highlighted (if applicable)
  ✓ Nested objects are collapsed by default
  ✓ User can expand/collapse sections
  ✓ Copy button is present
  
When: User clicks copy button
Then:
  ✓ JSON is copied to clipboard
  ✓ Toast message shows "Copied!"
  ✓ User can paste JSON elsewhere
  
Test Cases:
  2.2.1: Verify JSON formatting is correct
  2.2.2: Expand/collapse nested objects
  2.2.3: Copy JSON to clipboard
```

### Scenario 2.3: View Output Variables
```
Given: Modal is open for a COMPLETED execution
  AND: Execution has output: {"total": 125.50, "status": "success"}
When: User looks at "Output Variables" section
Then:
  ✓ JSON displays formatted
  ✓ Can expand/collapse nested objects
  ✓ Copy button works
  
Given: Modal is open for a FAILED execution
  AND: Output is empty/null
When: User looks at "Output Variables" section
Then:
  ✓ Message shows "No output" or null indicator
  
Test Cases:
  2.3.1: View non-empty output variables
  2.3.2: View empty output (FAILED status)
  2.3.3: Copy output JSON to clipboard
```

### Scenario 2.4: View Error Message
```
Given: Modal is open for a FAILED execution
  AND: Error message is "java.lang.ClassNotFoundException: com.example.Calculator"
When: User looks at modal
Then:
  ✓ Error section is visible
  ✓ Error message displays in readable format
  ✓ Error is highlighted in red color (#ef4444)
  ✓ Stack trace (if available) shows with syntax highlighting
  ✓ Long error messages can be expanded/collapsed
  
Test Cases:
  2.4.1: Verify error message displays
  2.4.2: Verify error color is red
  2.4.3: Expand/collapse long error messages
```

### Scenario 2.5: Close Modal
```
Given: Modal is open
When: User clicks X button in modal header
Then:
  ✓ Modal closes
  ✓ Focus returns to table
  
When: User presses ESC key
Then:
  ✓ Modal closes
  ✓ Focus returns to table
  
When: User clicks overlay outside modal
Then:
  ✓ Modal closes (optional: could be disabled)
  
Test Cases:
  2.5.1: Close via X button
  2.5.2: Close via ESC key
  2.5.3: Verify focus returns to table
```

### Scenario 2.6: Modal Accessibility
```
Given: Modal is open
When: Keyboard user tabs through modal
Then:
  ✓ Focus is trapped inside modal
  ✓ Tab order is logical (header → content → buttons)
  ✓ All interactive elements are keyboard accessible
  ✓ Focus indicator is visible
  
Test Cases:
  2.6.1: Tab through modal elements
  2.6.2: Verify focus trap
  2.6.3: Verify ARIA labels present
  2.6.4: Test with screen reader (optional)
```

---

## Story 8.3.3: Filtering & Search

### Scenario 3.1: Filter by Status
```
Given: Execution list shows 50 executions (30 COMPLETED, 15 FAILED, 5 TIMEOUT)
  AND: Filter panel is visible
When: User clicks Status dropdown
Then:
  ✓ Dropdown shows 3 options: COMPLETED, FAILED, TIMEOUT
  
When: User selects "FAILED"
Then:
  ✓ Status filter field shows "FAILED"
  
When: User clicks "Apply Filters" button
Then:
  ✓ Table refreshes
  ✓ Only FAILED executions display (15 rows)
  ✓ Page indicator updates to "Page 1 of 1"
  ✓ URL updates to include ?status=FAILED
  
Test Cases:
  3.1.1: Select FAILED status
  3.1.2: Verify table shows only failed executions
  3.1.3: Verify URL updates with filter
```

### Scenario 3.2: Filter by Instance ID
```
Given: Filter panel is open
When: User types "123" in InstanceId field
Then:
  ✓ Field shows "123"
  ✓ Autocomplete dropdown appears (showing recent instances)
  
When: User clicks Apply Filters
Then:
  ✓ Table refreshes
  ✓ Only executions for instance 123 display
  ✓ URL updates to ?instanceId=123
  
Test Cases:
  3.2.1: Type and select instance ID
  3.2.2: Verify autocomplete works
  3.2.3: Verify table filters correctly
```

### Scenario 3.3: Filter by JAR File
```
Given: 3 JAR files uploaded (calculator.jar, processor.jar, utility.jar)
When: User clicks JAR dropdown
Then:
  ✓ Dropdown shows 3 JAR files with IDs
  
When: User selects "calculator.jar"
Then:
  ✓ Filter updates
  
When: User clicks Apply Filters
Then:
  ✓ Table shows only executions from that JAR
  ✓ URL includes ?jarId=1 (or appropriate ID)
  
Test Cases:
  3.3.1: Select JAR from dropdown
  3.3.2: Verify executions filtered to selected JAR
```

### Scenario 3.4: Filter by Class Name
```
Given: Filter panel is open
When: User types "Calculator" in Class field
Then:
  ✓ Field shows "Calculator"
  
When: User clicks Apply Filters
Then:
  ✓ Table shows only executions where className contains "Calculator"
  ✓ URL updates with ?className=Calculator
  
Test Cases:
  3.4.1: Type class name
  3.4.2: Verify table filters to matching classes
```

### Scenario 3.5: Filter by Method Name
```
Given: Filter panel is open
When: User types "add" in Method field
Then:
  ✓ Field shows "add"
  
When: User clicks Apply Filters
Then:
  ✓ Table shows only executions where methodName contains "add"
  ✓ URL updates with ?methodName=add
  
Test Cases:
  3.5.1: Type method name
  3.5.2: Verify table filters to matching methods
```

### Scenario 3.6: Combine Multiple Filters
```
Given: Filter panel is open
When: User:
  1. Selects Status = FAILED
  2. Types InstanceId = 123
  3. Selects JAR = calculator.jar
Then:
  ✓ All 3 filters show selected values
  
When: User clicks Apply Filters
Then:
  ✓ Table shows executions matching ALL criteria
  ✓ URL includes ?status=FAILED&instanceId=123&jarId=1
  ✓ Result count is subset of individual filters
  
Test Cases:
  3.6.1: Apply 2 filters together
  3.6.2: Apply 3+ filters together
  3.6.3: Verify URL includes all params
  3.6.4: Verify result is intersection of criteria
```

### Scenario 3.7: Clear All Filters
```
Given: Filters are applied (status=FAILED, instanceId=123)
  AND: Table shows filtered results
When: User clicks "Clear All Filters" button
Then:
  ✓ All filter fields reset to empty/default
  ✓ Table refreshes and shows all executions again
  ✓ URL query params removed
  ✓ Page indicator updates (e.g., "Page 1 of 3")
  
Test Cases:
  3.7.1: Apply filters
  3.7.2: Clear all filters
  3.7.3: Verify table shows all data
  3.7.4: Verify URL is clean
```

### Scenario 3.8: Filter Panel Collapse/Expand
```
Given: Filter panel is open
When: User clicks collapse button
Then:
  ✓ Filter panel collapses
  ✓ Table area expands to fill space
  ✓ Filter icon shows panel is collapsed
  
When: User clicks expand button
Then:
  ✓ Filter panel expands
  ✓ Applied filters still visible
  
Test Cases:
  3.8.1: Collapse and expand filter panel
  3.8.2: Verify responsive layout adjusts
```

---

## Story 8.3.4: Performance Metrics

### Scenario 4.1: Display Metric Cards
```
Given: User navigates to executions page
  AND: 50 executions exist (40 COMPLETED, 10 FAILED)
When: Page loads
Then:
  ✓ 5 metric cards display at top:
    1. Total Executions: 50
    2. Success Rate: 80%
    3. Failed Rate: 20%
    4. Avg Execution Time: XXX ms (average of all)
    5. Throughput: X execs/min
  ✓ Cards show appropriate icons
  ✓ Cards have color coding (green for good, red for bad)
  
Test Cases:
  4.1.1: Verify all 5 cards render
  4.1.2: Verify metrics calculate correctly
  4.1.3: Verify color coding is appropriate
```

### Scenario 4.2: Metrics Update with Filters
```
Given: Metrics show overall data
When: User applies filter Status = FAILED
Then:
  ✓ Metrics recalculate for filtered data
  ✓ Total Executions shows 10
  ✓ Success Rate shows 0%
  ✓ Failed Rate shows 100%
  ✓ Avg Execution Time recalculates
  
When: User clears filters
Then:
  ✓ Metrics return to overall values
  
Test Cases:
  4.2.1: Filter to FAILED, verify metrics update
  4.2.2: Filter to COMPLETED, verify metrics update
  4.2.3: Clear filters, verify metrics reset
```

### Scenario 4.3: Metric Accuracy
```
Given: 100 executions (60 COMPLETED, 40 FAILED)
  AND: Execution times: [10, 20, 30, ..., 1000] (total = 50,500 ms)
When: Metrics are calculated
Then:
  ✓ Total Executions = 100 (exact count)
  ✓ Success Rate = 60% (exactly)
  ✓ Failed Rate = 40% (exactly)
  ✓ Avg Execution Time = 505 ms (50,500 / 100)
  ✓ Throughput = X.XX execs/min (based on timespan)
  
Test Cases:
  4.3.1: Verify total count accuracy
  4.3.2: Verify success/failure percentages
  4.3.3: Verify average time calculation
  4.3.4: Verify throughput calculation
```

### Scenario 4.4: Sparkline Trends (Optional)
```
Given: 20 recent executions exist
When: Metrics cards display
Then:
  ✓ Small sparkline chart appears in card (optional)
  ✓ Shows trend of last 10 executions
  ✓ Green line for success rate trend
  ✓ Red line for failure rate trend
  
Test Cases:
  4.4.1: Sparklines render (if included)
  4.4.2: Trend line shows correct data points
```

---

## Story 8.3.5: Error Analysis

### Scenario 5.1: Error Display in Table
```
Given: Table shows execution with status FAILED
  AND: Error message is "java.lang.ClassNotFoundException"
When: User looks at table row
Then:
  ✓ Error message preview shows (truncated, first 50 chars)
  ✓ Error category badge shows "ClassNotFound" (parsed)
  ✓ Badge color is red
  
Test Cases:
  5.1.1: Verify error preview displays
  5.1.2: Verify error category badge shows
  5.1.3: Verify badge color is red
```

### Scenario 5.2: Error Details in Modal
```
Given: Modal is open for FAILED execution
  AND: Error is "java.lang.ClassNotFoundException: com.example.Calculator"
When: User views error section
Then:
  ✓ Full error message displays
  ✓ Error category shows (e.g., "Class Not Found")
  ✓ Stack trace shows with syntax highlighting
  ✓ Stack trace is readable with proper formatting
  ✓ Copy button copies full error + stack trace
  
Test Cases:
  5.2.1: Verify full error message displays
  5.2.2: Verify error category is detected
  5.2.3: Verify stack trace is formatted
  5.2.4: Copy error to clipboard
```

### Scenario 5.3: Error Categories
```
Given: Multiple FAILED executions with different errors
When: Errors are categorized
Then:
  ✓ "ClassNotFoundException" → "Class Not Found" badge
  ✓ "NoSuchMethodException" → "Method Not Found" badge
  ✓ "TimeoutException" → "Timeout" badge
  ✓ "NullPointerException" → "Null Pointer" badge
  ✓ Unknown errors → "Unknown Error" badge
  
Test Cases:
  5.3.1: ClassNotFoundException detected
  5.3.2: MethodNotFoundException detected
  5.3.3: TimeoutException detected
  5.3.4: Unknown error handled
```

### Scenario 5.4: Error Search
```
Given: Table displays 10 FAILED executions
  AND: Filter has error search field
When: User types "ClassNotFound" in error search
Then:
  ✓ Table filters to show only matching errors
  ✓ Matching text is highlighted in error preview
  ✓ Result count updates
  
Test Cases:
  5.4.1: Search for specific error type
  5.4.2: Verify matching results highlighted
  5.4.3: Verify non-matching results hidden
```

---

## Integration Test Scenarios (Phase 8.4)

### Scenario I.1: End-to-End Workflow
```
Given: Backend is running
  AND: Admin UI is running
When:
  1. Create Code Task process (Modeler)
  2. Upload test JAR (Modeler or Admin)
  3. Deploy process
  4. Start new instance from Task Portal
  5. Task Portal completes Code Task
Then:
  ✓ Code Task executes successfully
  ✓ Execution appears in Admin UI list within 5 seconds
  ✓ Execution shows COMPLETED status
  ✓ Input/output variables correct
  
Test Cases:
  I.1.1: Full successful workflow
  I.1.2: Workflow with FAILED status
  I.1.3: Workflow with TIMEOUT
```

### Scenario I.2: Real-Time Updates
```
Given: Admin UI list page is open
  AND: Parallel process instance executing
When: Code Task completes in parallel process
Then:
  ✓ New execution appears in list (within 5 seconds)
  ✓ Metrics update automatically
  ✓ User doesn't need to manually refresh
  
Test Cases:
  I.2.1: Check for auto-refresh
  I.2.2: Verify metrics update
```

---

## Test Data Requirements

### Test Fixtures (Setup Data)

**Fixture 1: Sample JARs**
```
- calculator.jar (contains Calculator class with add, subtract methods)
- processor.jar (contains OrderProcessor with calculateTotal method)
- utils.jar (contains StringUtils with concat, reverse methods)
```

**Fixture 2: Sample Executions**
```
50 executions with distribution:
- 30 COMPLETED (varied execution times: 10-500ms)
- 15 FAILED (various error types)
- 5 TIMEOUT (execution time > 5000ms)

Across multiple process instances (10 different instances)
```

**Fixture 3: Error Examples**
```
- ClassNotFoundException
- NoSuchMethodException
- TimeoutException
- NullPointerException
- IllegalArgumentException
- Custom application errors
```

---

## Test Execution Plan

### Phase 1: Unit Tests (During Development)
- Run per-component tests during development
- Jest test suite
- Coverage target: 80%+

### Phase 2: Integration Tests (Post-Development)
- Test API integration
- Test state management
- Test filter logic
- Coverage target: 90%+

### Phase 3: E2E Tests (Before Release)
- Full workflow testing
- Cross-component testing
- Performance testing
- User acceptance testing

---

## Success Criteria

| Category | Criteria | Status |
|----------|----------|--------|
| **Functionality** | All scenarios pass | ⏳ Pending |
| **Performance** | Page load < 2s | ⏳ Pending |
| **Code Coverage** | > 80% | ⏳ Pending |
| **Accessibility** | WCAG 2.1 AA | ⏳ Pending |
| **Responsiveness** | Mobile/Tablet OK | ⏳ Pending |
| **Error Handling** | Graceful failures | ⏳ Pending |

---

**Document Status**: 📋 Ready for QA Implementation  
**Test Lead**: QA Engineer  
**Last Updated**: April 22, 2026
