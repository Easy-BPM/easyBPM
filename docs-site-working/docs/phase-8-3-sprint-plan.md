# Phase 8.3 - Sprint Plan & Task Breakdown

**Sprint Name**: Code Task Admin UI  
**Sprint Duration**: 1 week (5 days)  
**Team Size**: 2-3 developers  
**Total Effort**: 4 story points  
**Start Date**: April 23, 2026  
**Target Completion**: April 29, 2026

---

## Sprint Goals

1. ✅ Deploy Code Task execution monitoring to Admin UI
2. ✅ Enable filtering and searching executions
3. ✅ Provide detailed execution analysis with variable inspection
4. ✅ Display performance metrics and execution statistics
5. ✅ Ensure > 80% test coverage and all acceptance criteria met

---

## Task Board

### 🟦 Story 8.3.1: Execution List View (2 SP)

**Lead Developer**: Frontend Developer  
**Estimated Days**: 2 days  
**Status**: 📋 NOT STARTED

#### Subtasks

**Task 8.3.1.1: Create CodeTaskExecutionListPage Component**
- [ ] Create `easy-bpm-admin/components/CodeTaskExecutionListPage.tsx` (250 lines)
- [ ] Add page layout with header, metrics area, filter panel, table area
- [ ] Import and integrate child components
- [ ] Set up state management for filters, pagination, sorting
- [ ] Add tabs navigation (if needed with other pages)
- **Effort**: 0.5 SP (4 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Page renders without errors
  - [ ] All child components mount
  - [ ] State updates trigger re-renders
  - [ ] URL params sync with state

**Task 8.3.1.2: Create CodeTaskExecutionTable Component**
- [ ] Create `easy-bpm-admin/components/CodeTaskExecutionTable.tsx` (200 lines)
- [ ] Table columns: ID, InstanceId, JAR, Class, Method, Status, Time, Date
- [ ] Add column sorting (click header to sort)
- [ ] Add row click handler (opens details modal)
- [ ] Add status badges with color coding (green/red/orange)
- [ ] Add loading state (skeleton or spinner)
- [ ] Add empty state message
- **Effort**: 0.75 SP (6 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Table renders 20 rows per page
  - [ ] Sorting works for each column
  - [ ] Status badges display correctly
  - [ ] Row click triggers modal (via callback)
  - [ ] Responsive on mobile/tablet

**Task 8.3.1.3: Create useCodeTaskExecutions Hook**
- [ ] Create `easy-bpm-admin/services/codeTaskService.ts` (50 lines)
- [ ] Implement `GET /code-tasks/executions` API call
- [ ] Handle query parameters: status, instanceId, page, size
- [ ] Parse response and return typed data
- [ ] **Effort**: 0.25 SP (2 hours)
- [ ] **Assignee**: Frontend Developer
- [ ] **Acceptance Criteria**:
  - [ ] API call uses correct endpoint
  - [ ] Query params passed correctly
  - [ ] Response parsed and returned
  - [ ] Error handling in place

**Task 8.3.1.4: Add Pagination Logic**
- [ ] Implement previous/next page buttons
- [ ] Track current page in URL params
- [ ] Update table when page changes
- [ ] Show page indicator (e.g., "Page 1 of 3")
- **Effort**: 0.25 SP (2 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Pagination buttons appear only when needed
  - [ ] URL updates with page param
  - [ ] Table refreshes on page change
  - [ ] Page indicator shows correct count

**Task 8.3.1.5: Write Unit Tests**
- [ ] Create `easy-bpm-admin/__tests__/CodeTaskExecutionPage.test.tsx` (100 lines)
- [ ] Test page renders with mock data
- [ ] Test sorting functionality
- [ ] Test pagination navigation
- [ ] Test API call with filters
- **Effort**: 0.25 SP (2 hours)
- **Assignee**: QA Engineer / Frontend Developer
- **Acceptance Criteria**:
  - [ ] 8+ test cases pass
  - [ ] Coverage > 80%
  - [ ] Mock API calls work
  - [ ] User interactions tested

---

### 🟦 Story 8.3.2: Execution Details Modal (1 SP)

**Lead Developer**: Frontend Developer  
**Estimated Days**: 1 day  
**Status**: 📋 NOT STARTED

#### Subtasks

**Task 8.3.2.1: Create CodeTaskExecutionDetailsModal Component**
- [ ] Create `easy-bpm-admin/components/CodeTaskExecutionDetailsModal.tsx` (200 lines)
- [ ] Modal displays execution details (executionId, instanceId, jar, class, method)
- [ ] Show input variables (formatted JSON, collapsible)
- [ ] Show output variables (formatted JSON, collapsible)
- [ ] Show error message if status is FAILED
- [ ] Add copy-to-clipboard buttons
- [ ] Add close button and ESC key handler
- **Effort**: 0.6 SP (5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Modal opens on row click
  - [ ] All details displayed correctly
  - [ ] JSON is properly formatted
  - [ ] Copy buttons work
  - [ ] ESC closes modal
  - [ ] Accessibility: ARIA labels, focus trap

**Task 8.3.2.2: Create JSONViewer Component**
- [ ] Create `easy-bpm-admin/components/JSONViewer.tsx` (100 lines)
- [ ] Display JSON with syntax highlighting
- [ ] Add collapse/expand for nested objects
- [ ] Add pretty-print formatting
- [ ] Add copy button
- **Effort**: 0.25 SP (2 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] JSON renders with proper indentation
  - [ ] Collapse/expand works
  - [ ] Copy to clipboard works
  - [ ] Handles empty/null values

**Task 8.3.2.3: Add Error Display Logic**
- [ ] Show error message in modal when status is FAILED
- [ ] Truncate long messages with expand option
- [ ] Add syntax highlighting for stack traces (prism.js)
- [ ] Show error type badge
- **Effort**: 0.1 SP (1 hour)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Error message displays
  - [ ] Stack trace is readable
  - [ ] Long messages are truncated/expandable

**Task 8.3.2.4: Write Unit Tests**
- [ ] Create unit tests for modal rendering
- [ ] Test JSON viewer formatting
- [ ] Test copy to clipboard
- [ ] Test error display
- **Effort**: 0.05 SP (0.5 hours)
- **Assignee**: QA Engineer
- **Acceptance Criteria**:
  - [ ] 6+ test cases pass
  - [ ] All user interactions tested

---

### 🟦 Story 8.3.3: Filtering & Search (1 SP)

**Lead Developer**: Frontend Developer  
**Estimated Days**: 1 day  
**Status**: 📋 NOT STARTED

#### Subtasks

**Task 8.3.3.1: Create Filter Panel Component**
- [ ] Create `easy-bpm-admin/components/CodeTaskExecutionFilterPanel.tsx` (150 lines)
- [ ] Add 5 filter fields:
  - Status dropdown (COMPLETED, FAILED, TIMEOUT)
  - InstanceId text input
  - JAR dropdown (from API)
  - Class name text input
  - Method name text input
- [ ] Add "Apply Filters" button
- [ ] Add "Clear All" button
- [ ] Make panel collapsible
- **Effort**: 0.5 SP (4 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] All 5 filters render
  - [ ] Apply button works
  - [ ] Clear All button resets all filters
  - [ ] Panel collapsible

**Task 8.3.3.2: Implement Filter State Management**
- [ ] Create filter state in CodeTaskExecutionListPage
- [ ] Sync filter state with URL query params
- [ ] Update API call with filter parameters
- [ ] Persist filters across navigation
- **Effort**: 0.3 SP (2.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Filter state updates on change
  - [ ] URL query params match filter state
  - [ ] API call includes filter params
  - [ ] Filters persist on page reload

**Task 8.3.3.3: Add InstanceId Autocomplete**
- [ ] Load list of recent instanceIds from process instances
- [ ] Add autocomplete dropdown in InstanceId field
- [ ] Show instance key/status in dropdown
- **Effort**: 0.2 SP (1.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Autocomplete shows suggestions
  - [ ] Selection updates filter
  - [ ] Works with API pagination

**Task 8.3.3.4: Write Unit Tests**
- [ ] Test filter state updates
- [ ] Test URL param sync
- [ ] Test API call with filters
- [ ] Test clear all functionality
- **Effort**: 0.1 SP (1 hour)
- **Assignee**: QA Engineer
- **Acceptance Criteria**:
  - [ ] 8+ test cases pass
  - [ ] Filter combinations tested

---

### 🟥 Story 8.3.4: Performance Metrics (0.5 SP)

**Lead Developer**: Frontend Developer  
**Estimated Days**: 0.5 day  
**Status**: 📋 NOT STARTED

#### Subtasks

**Task 8.3.4.1: Create Metrics Cards Component**
- [ ] Create `easy-bpm-admin/components/CodeTaskExecutionMetrics.tsx` (150 lines)
- [ ] Display 5 metric cards:
  - Total Executions (count)
  - Success Rate (%)
  - Failed Rate (%)
  - Average Execution Time (ms)
  - Throughput (execs/min)
- [ ] Update metrics when filters change
- [ ] Add color coding (green for good, red for bad)
- **Effort**: 0.3 SP (2.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] All 5 cards render
  - [ ] Metrics calculate correctly
  - [ ] Cards update on filter change
  - [ ] Color coding is clear

**Task 8.3.4.2: Add Sparkline Charts**
- [ ] Add mini trend sparklines to metrics cards (optional)
- [ ] Use recharts or chart.js library
- [ ] Show last 10 executions trend
- **Effort**: 0.2 SP (1.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Sparklines render
  - [ ] Correct data points shown
  - [ ] Responsive on mobile

---

### 🟥 Story 8.3.5: Error Analysis (0.5 SP)

**Lead Developer**: Frontend Developer  
**Estimated Days**: 0.5 day  
**Status**: 📋 NOT STARTED

#### Subtasks

**Task 8.3.5.1: Add Error Parsing Logic**
- [ ] Extract error type from error message
- [ ] Categorize errors: JAR Not Found, Method Mismatch, Timeout, etc.
- [ ] Add error category badge in table
- **Effort**: 0.2 SP (1.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Error categories identified
  - [ ] Badges display in table
  - [ ] Categories are correct

**Task 8.3.5.2: Enhance Error Display in Modal**
- [ ] Show error category badge
- [ ] Show error message (formatted)
- [ ] Add stack trace with syntax highlighting
- [ ] Add "Copy Error" button for bug reports
- **Effort**: 0.2 SP (1.5 hours)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Error displayed in modal
  - [ ] Stack trace readable
  - [ ] Copy button works

**Task 8.3.5.3: Add Error Search**
- [ ] Allow searching by error message
- [ ] Highlight matching text in results
- **Effort**: 0.1 SP (1 hour)
- **Assignee**: Frontend Developer
- **Acceptance Criteria**:
  - [ ] Search filters table
  - [ ] Matching text highlighted

---

## Daily Standup Schedule

**Time**: 9:00 AM - 9:15 AM  
**Format**: 3 questions per person
1. What did I complete yesterday?
2. What am I working on today?
3. Are there any blockers?

### Day 1 (April 23)
**Focus**: Story 8.3.1 - List view & table
- Task 8.3.1.1: CodeTaskExecutionListPage (Frontend Dev)
- Task 8.3.1.2: CodeTaskExecutionTable (Frontend Dev)
- Task 8.3.1.3: API service (Frontend Dev)

### Day 2 (April 24)
**Focus**: Story 8.3.1 - Pagination & tests, Start 8.3.2
- Task 8.3.1.4: Pagination (Frontend Dev)
- Task 8.3.1.5: Unit tests (QA Engineer)
- Task 8.3.2.1: Details modal (Frontend Dev)

### Day 3 (April 25)
**Focus**: Story 8.3.2 complete, Start 8.3.3
- Task 8.3.2.2: JSONViewer (Frontend Dev)
- Task 8.3.2.3: Error display (Frontend Dev)
- Task 8.3.2.4: Tests (QA Engineer)
- Task 8.3.3.1: Filter panel (Frontend Dev)

### Day 4 (April 26)
**Focus**: Story 8.3.3 complete, Start 8.3.4 & 8.3.5
- Task 8.3.3.2: Filter state mgmt (Frontend Dev)
- Task 8.3.3.3: Autocomplete (Frontend Dev)
- Task 8.3.3.4: Tests (QA Engineer)
- Task 8.3.4.1: Metrics (Frontend Dev)

### Day 5 (April 27)
**Focus**: Complete metrics, error analysis, testing, documentation
- Task 8.3.4.2: Sparklines (Frontend Dev)
- Task 8.3.5.1-3: Error analysis (Frontend Dev)
- Integration tests (QA Engineer)
- Documentation (Tech Writer)

---

## Risk & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| Admin UI routing unknown | High | Medium | Ask CTO day 1, check existing code |
| API pagination slow (1000+ records) | Medium | Low | Implement server-side pagination |
| Missing process instance API | High | Low | Fall back to manual instanceId input |
| Complexity of error parsing | Low | Medium | Create error utility module |
| Performance: load time > 2s | Medium | Low | Lazy load modal, memoize calculations |

---

## Acceptance & Sign-Off

### Story 8.3.1 Acceptance
- [ ] List page renders correctly
- [ ] Table shows 20 executions per page
- [ ] Sorting works on all columns
- [ ] Pagination navigates correctly
- [ ] Status badges color-coded
- [ ] Unit tests pass (> 80% coverage)

### Story 8.3.2 Acceptance
- [ ] Modal opens on row click
- [ ] Details display correctly
- [ ] JSON viewer shows formatted data
- [ ] Error message shows (if FAILED)
- [ ] Copy buttons work
- [ ] ESC closes modal

### Story 8.3.3 Acceptance
- [ ] 5 filter fields render
- [ ] Apply filters button works
- [ ] URL params sync with filters
- [ ] Filters persist on reload
- [ ] InstanceId autocomplete works
- [ ] Unit tests pass

### Story 8.3.4 Acceptance
- [ ] 5 metric cards render
- [ ] Calculations correct
- [ ] Cards update on filter change
- [ ] Sparklines optional but nice

### Story 8.3.5 Acceptance
- [ ] Errors display in table
- [ ] Error categories identified
- [ ] Full error in modal
- [ ] Stack traces readable
- [ ] Search by error works

---

## Definition of Done (per task)

✅ **Code**:
- [ ] Written in React 19 with TypeScript
- [ ] Follows existing code style and patterns
- [ ] No console warnings or errors
- [ ] No hardcoded values (use constants)
- [ ] Proper error handling

✅ **Testing**:
- [ ] Unit tests written (Jest)
- [ ] Test coverage > 80%
- [ ] All tests pass
- [ ] Mocked API calls

✅ **Documentation**:
- [ ] JSDoc comments for functions
- [ ] README or inline docs for complex logic
- [ ] Component props documented

✅ **Review**:
- [ ] Code reviewed by peer
- [ ] Feedback addressed
- [ ] Approved by lead

✅ **Integration**:
- [ ] Integrated into AdminUI
- [ ] Works with existing components
- [ ] No regression in other features

---

## Tools & Environment

**Frontend Stack**:
- React 19
- TypeScript 5+
- Tailwind CSS
- Jest (testing)
- Lucide React (icons)
- @radix-ui (accessible components)
- recharts (optional: charts)
- prism.js (optional: syntax highlighting)

**API Base URL**: 
- Dev: `http://localhost:8085` (or `$VITE_API_BASE_URL`)
- Prod: Environment variable

**Development Commands**:
```bash
# Start Admin UI dev server
cd easy-bpm-admin && npm run dev

# Run tests
npm test

# Build for production
npm run build
```

---

## Success Metrics

| Metric | Target | Owner |
|--------|--------|-------|
| Tasks completed | 100% | Scrum Master |
| Code coverage | > 80% | QA Engineer |
| Test pass rate | 100% | QA Engineer |
| Page load time | < 2 seconds | Frontend Dev |
| User acceptance | All criteria met | Product Owner |
| Documentation | Complete | Tech Writer |

---

## Continuation Planning

### Phase 8.3 Dependencies Satisfied?
- ✅ Phase 8.1.9 API exists
- ✅ Modeler UI complete
- ⏳ Admin UI structure confirmed

### Phase 8.4 (Next)
- Prepare integration test suite
- Prepare E2E test scenarios
- Begin Code Task workflow testing

### Phase 8.5 (After 8.4)
- Write user guide for Admin UI
- Create example workflow documentation
- Record tutorial video (optional)

---

**Created**: April 22, 2026  
**Last Updated**: April 22, 2026  
**Sprint Master**: Process Orchestrator Team  
**Status**: 📋 Ready to Start
