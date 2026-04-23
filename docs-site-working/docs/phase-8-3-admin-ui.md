# EPIC: Phase 8.3 - Code Task Admin UI

**Status**: 📋 PLANNED (2026-04-22)  
**Epic Lead**: Process Orchestrator Team  
**Effort**: 4 story points  
**Duration**: ~1 week  
**Target Release**: Phase 8.3 (after 8.1.9 & 8.2)

---

## 1. Epic Overview

Add **Code Task execution monitoring and audit trail visualization** to the Easy BPM Admin UI, enabling users to:
- View all Code Task executions with filtering and sorting
- Monitor execution status (COMPLETED, FAILED, TIMEOUT)
- Inspect variable snapshots (input/output JSONB)
- Analyze execution performance (time, throughput)
- Debug errors with detailed error messages
- Track execution history per process instance

---

## 2. Objectives & Success Criteria

### Objectives
✅ Provide real-time visibility into Code Task executions  
✅ Enable debugging and error analysis  
✅ Support performance monitoring  
✅ Align with existing Admin UI patterns and styling  
✅ Prepare foundation for Phase 9 (advanced monitoring)

### Success Criteria
- [ ] Execution list displays all Code Task executions
- [ ] Filtering works by instanceId, status, JAR, className, methodName
- [ ] Pagination handles 100+ executions smoothly
- [ ] Variable snapshots (input/output) are viewable
- [ ] Error messages are displayed with stack traces (if available)
- [ ] Performance metrics shown (execution time, throughput)
- [ ] Responsive design works on desktop and tablet
- [ ] E2E test scenario passes (create instance → execute Code Task → view in Admin UI)

---

## 3. User Stories & Tasks

### Story 8.3.1: Code Task Execution List View
**Effort**: 2 story points  
**Description**: Create list view showing all Code Task executions with filtering

**Acceptance Criteria**:
- [ ] Table displays: executionId, instanceId, JAR, Class, Method, Status, ExecutedAt
- [ ] Column sorting by: Status, ExecutedAt, ExecutionTime
- [ ] Inline badges for status: green (COMPLETED), red (FAILED), orange (TIMEOUT)
- [ ] Pagination: 20 items per page with prev/next controls
- [ ] Query params preserved in URL (enable bookmarking)
- [ ] Load time < 2 seconds for 100 records
- [ ] Responsive: Stack columns on mobile, preserve table on desktop

**Tasks**:
- [ ] Create `CodeTaskExecutionListPage.tsx` component (250 lines)
- [ ] Create `CodeTaskExecutionTable.tsx` component (200 lines)
- [ ] Add `/admin/code-tasks/executions` route
- [ ] Implement API call: `GET /code-tasks/executions` with filters
- [ ] Add sorting and pagination logic
- [ ] Add TypeScript interfaces for response DTOs
- [ ] Add unit tests for table rendering (Jest)
- [ ] Add E2E test: load page, verify table renders (Playwright)

**Dependencies**: Phase 8.1.9 REST API (already complete)

---

### Story 8.3.2: Execution Details Modal
**Effort**: 1 story point  
**Description**: Click-to-view modal showing full execution details including variables

**Acceptance Criteria**:
- [ ] Modal displays when clicking row in execution list
- [ ] Shows: executionId, instanceId, jarId, className, methodName
- [ ] Shows input variables as formatted JSON (collapsible)
- [ ] Shows output variables as formatted JSON (collapsible)
- [ ] Shows execution time (ms), status, error message (if any)
- [ ] Copy-to-clipboard buttons for JSON
- [ ] Close button and ESC key to dismiss
- [ ] Modal is accessible (ARIA labels, focus trap)

**Tasks**:
- [ ] Create `CodeTaskExecutionDetailsModal.tsx` component (200 lines)
- [ ] Create `JSONViewer.tsx` component for formatted display (100 lines)
- [ ] Add JSON copy utility
- [ ] Add modal styling (Tailwind + custom CSS)
- [ ] Add keyboard event handlers (ESC)
- [ ] Add accessibility labels
- [ ] Add unit tests for modal (Jest)

**Dependencies**: 8.3.1 (list view must exist first)

---

### Story 8.3.3: Filtering & Search
**Effort**: 1 story point  
**Description**: Add filters for instanceId, status, JAR, class, method

**Acceptance Criteria**:
- [ ] Filter panel with 5 filter fields (expandable)
- [ ] Filter by Status (dropdown: COMPLETED, FAILED, TIMEOUT)
- [ ] Filter by InstanceId (text input with autocomplete)
- [ ] Filter by JAR ID (dropdown from discovered JARs)
- [ ] Filter by Class Name (text input)
- [ ] Filter by Method Name (text input)
- [ ] Apply Filters button applies all filters
- [ ] Clear All Filters button resets all
- [ ] URL query params sync with filter state
- [ ] Filters persist across page navigation

**Tasks**:
- [ ] Create `CodeTaskExecutionFilterPanel.tsx` component (150 lines)
- [ ] Create filter state management (useState hooks)
- [ ] Add API parameters: status, instanceId, jarId, className, methodName
- [ ] Add InstanceId autocomplete API call
- [ ] Add URL query param sync
- [ ] Add unit tests for filter logic (Jest)
- [ ] Add E2E test: apply filters, verify table updates (Playwright)

**Dependencies**: 8.3.1 (list view must exist)

---

### Story 8.3.4: Performance & Analytics
**Effort**: 0.5 story points  
**Description**: Show performance metrics and execution statistics

**Acceptance Criteria**:
- [ ] Dashboard cards showing:
  - Total executions (count)
  - Success rate (%) - green
  - Failed rate (%) - red
  - Average execution time (ms)
  - Throughput (executions/min)
- [ ] Cards update when filters change
- [ ] Trend sparkline charts (optional)
- [ ] Hover tooltips for explanations

**Tasks**:
- [ ] Create `CodeTaskExecutionMetrics.tsx` component (150 lines)
- [ ] Add metrics calculation logic (count, success%, avg time)
- [ ] Add dashboard card layout
- [ ] Add sparkline charts (recharts library)
- [ ] Add unit tests for calculations (Jest)

**Dependencies**: 8.3.1 (needs execution data)

---

### Story 8.3.5: Error Analysis & Debugging
**Effort**: 0.5 story points  
**Description**: Display and search error messages for failed executions

**Acceptance Criteria**:
- [ ] Error messages shown in execution list (truncated, expandable)
- [ ] Full error message in details modal (with syntax highlighting)
- [ ] Error type detection (ClassNotFoundException, MethodNotFound, TimeoutException, etc.)
- [ ] Error categorization badge (e.g., "JAR Not Found", "Method Mismatch")
- [ ] Search by error message
- [ ] Copy error for bug reports

**Tasks**:
- [ ] Enhance `CodeTaskExecutionDetailsModal` with error display
- [ ] Add error parsing logic (extract error type)
- [ ] Add error categorization
- [ ] Add syntax highlighting for stack traces (prism.js)
- [ ] Add unit tests for error parsing (Jest)

**Dependencies**: 8.3.2 (details modal must exist)

---

## 4. Architecture & Design

### Component Hierarchy
```
AdminUI
├── CodeTaskExecutionPage
│   ├── CodeTaskExecutionMetrics (stats cards)
│   ├── CodeTaskExecutionFilterPanel (filters)
│   ├── CodeTaskExecutionTable (list)
│   │   └── Row (click → details modal)
│   └── CodeTaskExecutionDetailsModal
│       ├── JSONViewer (input vars)
│       └── JSONViewer (output vars)
```

### Data Flow
```
AdminUI Page Load
  ↓
GET /code-tasks/executions (default: page=0, size=20)
  ↓
Response: { content: [], totalElements, totalPages, currentPage }
  ↓
Render CodeTaskExecutionTable
  ↓
User applies filters
  ↓
GET /code-tasks/executions?status=FAILED&instanceId=123
  ↓
Update table with filtered results
  ↓
User clicks row
  ↓
Show CodeTaskExecutionDetailsModal
  ↓
Display inputVariables, outputVariables, error message
```

### API Contract (Already Defined in Phase 8.1.9)
```typescript
// GET /code-tasks/executions
interface ExecutionAuditPageResponse {
  content: CodeTaskExecutionAuditResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

interface CodeTaskExecutionAuditResponse {
  executionId: number;
  instanceId: number;
  nodeId: string;
  jarId: number;
  className: string;
  methodName: string;
  inputVariables: string;  // JSON string
  outputVariables: string; // JSON string
  executionTimeMs: number;
  status: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  errorMessage: string | null;
  executedAt: string;      // ISO 8601 timestamp
}
```

### UI/UX Design

**Color Scheme** (consistent with existing Admin UI):
- Primary: Blue (#2563eb)
- Success: Green (#10b981)
- Error: Red (#ef4444)
- Warning: Orange (#f59e0b)
- Background: Gray (#f9fafb)
- Text: Dark gray (#111827)

**Status Badges**:
- COMPLETED → Green circle + "Completed"
- FAILED → Red circle + "Failed"
- TIMEOUT → Orange circle + "Timeout"

**Layout**:
- Metrics cards at top (4 columns)
- Filter panel (collapsible, left side)
- Execution table (right side, responsive)
- Click row → Modal overlay

### TypeScript Interfaces
```typescript
// easybpmn-modeler/components/CodeTaskExecutionListPage.tsx
interface CodeTaskExecutionAuditResponse {
  executionId: number;
  instanceId: number;
  nodeId: string;
  jarId: number;
  className: string;
  methodName: string;
  inputVariables: string;
  outputVariables: string;
  executionTimeMs: number;
  status: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  errorMessage: string | null;
  executedAt: string;
}

interface ExecutionAuditPageResponse {
  content: CodeTaskExecutionAuditResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

interface FilterState {
  status?: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  instanceId?: number;
  jarId?: number;
  className?: string;
  methodName?: string;
  page: number;
  size: number;
}
```

---

## 5. File Structure

### Frontend Files to Create
```
easy-bpm-admin/components/
├── CodeTaskExecutionPage.tsx              (250 lines)
│   └── Main page component with tabs
├── CodeTaskExecutionTable.tsx             (200 lines)
│   └── Table with sorting + pagination
├── CodeTaskExecutionFilterPanel.tsx       (150 lines)
│   └── 5 filter fields
├── CodeTaskExecutionDetailsModal.tsx      (200 lines)
│   └── JSON viewer + error display
├── CodeTaskExecutionMetrics.tsx           (150 lines)
│   └── Dashboard cards + sparklines
├── JSONViewer.tsx                         (100 lines)
│   └── Collapsible JSON display
└── useCodeTaskExecutions.ts               (100 lines)
    └── Custom hook for API calls

easy-bpm-admin/services/
├── codeTaskService.ts                     (50 lines)
    └── API calls to /code-tasks/executions
```

### Test Files to Create
```
easy-bpm-admin/__tests__/
├── CodeTaskExecutionPage.test.tsx         (100 lines)
├── CodeTaskExecutionTable.test.tsx        (80 lines)
├── CodeTaskExecutionFilterPanel.test.tsx  (80 lines)
├── CodeTaskExecutionMetrics.test.tsx      (60 lines)
└── codeTaskService.test.ts                (60 lines)
```

---

## 6. Acceptance Criteria & QA Scenarios

### Scenario 1: View All Executions
```
Given: 50 Code Task executions exist in database
When: User navigates to /admin/code-tasks/executions
Then:
  - Page loads in < 2 seconds
  - Table shows 20 executions (first page)
  - Pagination controls show "Page 1 of 3"
  - Status badges are color-coded (green/red/orange)
  - Columns display: ID, Instance, JAR, Class, Method, Status, Time, Date
```

### Scenario 2: Filter by Status
```
Given: User is on executions list page
When: User selects "FAILED" in Status filter and clicks "Apply"
Then:
  - Table refreshes and shows only failed executions
  - Count updates to show "5 failed"
  - URL updates to ?status=FAILED
  - Success rate metric shows 0%
```

### Scenario 3: View Execution Details
```
Given: Execution list is displayed
When: User clicks row for execution ID 42
Then:
  - Modal opens with execution details
  - Input variables shown as formatted JSON
  - Output variables shown as formatted JSON
  - Error message displayed (if status is FAILED)
  - Copy buttons work
  - ESC key closes modal
```

### Scenario 4: Performance Metrics
```
Given: 50 executions exist (45 succeeded, 5 failed)
When: Page loads
Then:
  - Total Executions card shows "50"
  - Success Rate card shows "90%"
  - Failed Rate card shows "10%"
  - Average Time card shows calculated average
  - Throughput card shows executions per minute
  - Cards update when filters change
```

### Scenario 5: Search by Error
```
Given: User is on executions list page
When: User types "ClassNotFound" in search and presses Enter
Then:
  - Table filters to show executions with that error message
  - Matching text is highlighted in error preview
  - Result count updates
```

---

## 7. Dependencies & Blockers

### Dependencies
- ✅ Phase 8.1.9: REST API endpoints (COMPLETE)
- ✅ Phase 8.2: Modeler UI (COMPLETE)
- ✅ Existing Admin UI infrastructure (already present)
- React 19 + TypeScript (already available)
- Tailwind CSS (already available)
- Lucide React icons (already available)
- @radix-ui components (already available)

### Potential Blockers
- [ ] Need to confirm Admin UI routing structure (how to add new route)
- [ ] Need to confirm API base URL configuration for Admin UI
- [ ] Large dataset performance (pagination handles 1000+ records?)

---

## 8. Testing Strategy

### Unit Tests
- Filter logic (state management, URL params)
- Metrics calculations (success %, avg time)
- Error parsing and categorization
- JSON viewer formatting

### Integration Tests (Phase 8.4)
- Full flow: Load page → Apply filters → Click row → View modal
- API call with various filter combinations
- Pagination navigation
- Error handling (API errors, empty results)

### E2E Tests (Phase 8.4)
1. Create process with Code Task
2. Execute Code Task (from Task Portal)
3. Navigate to Admin UI
4. Verify execution appears in list
5. Verify correct status, metrics
6. Verify details modal shows correct data

---

## 9. Roadmap & Future Enhancements

### Phase 8.3 (Current)
✅ Execution list, filtering, details modal, metrics, error analysis

### Phase 9 (Future)
- Real-time execution streaming (WebSocket)
- Execution timeline/gantt chart
- Performance trending (historical graphs)
- JAR usage analytics
- Error pattern detection
- Retry failed executions UI
- Bulk operations (delete, re-run)

### Phase 10+ (Backlog)
- Code coverage metrics
- Memory profiling
- Dependency tracking
- Execution logs (stdout/stderr capture)

---

## 10. Documentation Plan

### User Guide (Phase 8.5)
- "How to Monitor Code Task Executions"
- Filtering and searching executions
- Interpreting metrics and charts
- Debugging failed executions
- Performance optimization tips

### Developer Guide (Phase 8.5)
- Admin UI component architecture
- API integration patterns
- Extending filters and metrics
- Troubleshooting common issues

### API Documentation (Already in 8.1.9)
- `GET /code-tasks/executions` endpoint reference
- Filter parameter examples
- Response schema

---

## 11. Sprint Breakdown

### Sprint Planning Meeting
**Estimated**: 4 story points  
**Duration**: ~1 week (5 business days)

**Week 1**:
- Day 1-2: Core list view + API integration (Story 8.3.1)
- Day 2-3: Details modal (Story 8.3.2)
- Day 3-4: Filtering (Story 8.3.3)
- Day 4-5: Metrics + Error analysis (Stories 8.3.4, 8.3.5)
- Day 5: Testing + documentation

**Daily Standup**:
- What did we build yesterday?
- What are we building today?
- Any blockers?

---

## 12. Success Metrics

| Metric | Target | Status |
|--------|--------|--------|
| Feature completeness | 100% acceptance criteria met | 📋 Planned |
| Code coverage | > 80% | 📋 Pending |
| Page load time | < 2 seconds | 📋 Pending |
| Test pass rate | 100% | 📋 Pending |
| Documentation completeness | All stories documented | 📋 Pending |
| User feedback | Positive | 📋 Pending |

---

## 13. Sign-Off & Approval

**Epic Owner**: Process Orchestrator Team  
**Status**: 📋 Ready for Implementation  
**Next Action**: Create sprint board + assign developers

---

**Created**: April 22, 2026  
**Last Updated**: April 22, 2026  
**Document**: `/docs-site-working/docs/epics/epic-code-task-admin-ui.md`
