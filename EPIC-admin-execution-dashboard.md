# EPIC: BPM Admin Execution Dashboard
**Epic Name**: "Process Execution Overview Dashboard" — Camunda Operate-Inspired Operations Console  
**Epic ID**: ADMIN-DASHBOARD-EPIC  
**Status**: IN PROGRESS  
**Target Release**: Phase 9  
**Effort**: ~48 story points (estimated)  
**Document Version**: 1.0  
**Last Updated**: 2026-05-26

---

## Epic Overview

Transform Easy BPM Admin from a simple instance search tool into a comprehensive **operational dashboard** providing real-time visibility into process execution health, metrics, incidents, and operational insights—inspired by Camunda Operate but tailored to Easy BPM's architecture.

The dashboard must enable:
- **Real-time execution metrics**: Running instances, completed, failed, suspended, incidents
- **Process execution overview**: List of processes with execution statistics per process
- **Incident management**: Detect, track, and resolve process failures and errors
- **Advanced filtering & sorting**: Filter by process, status, time range, assignee, etc.
- **Drill-down analytics**: Click any process/instance to inspect variables, node history, errors
- **Performance insights**: Execution times, bottlenecks, SLA monitoring (future)

**Business Value**: Provide operations teams complete process execution visibility, enabling faster incident detection, root cause analysis, and operational decision-making without context-switching to multiple tools.

---

## Design Inspiration: Camunda Operate vs. Easy BPM Approach

| Feature | Camunda Operate | Easy BPM Approach |
|---------|-----------------|-------------------|
| **Dashboard Layout** | Side-by-side: Metrics + Process List + Filters | Metrics cards + Tabbed process/incident views |
| **Metrics Displayed** | Started, Running, Completed, Failed, Incidents | Total, Active, Completed, Failed, Suspended, Incidents |
| **Process Selection** | Dropdown or left sidebar | Tab-based or sidebar menu |
| **Instance Filtering** | Status, Process, Date, Variables | Status, Process, Nested Level, Assignee, Variables |
| **Incident Visualization** | Dedicated "Incidents" tab | Built into instance detail + dedicated incident view |
| **Real-time Updates** | WebSocket subscriptions | Polling + Optional WebSocket (Phase 10) |
| **Mobile Support** | Responsive design | Responsive Tailwind CSS grid |

---

## Phased Implementation Plan

### Phase 9.1: Dashboard Infrastructure & Core Metrics
**Effort**: 12 story points  
**Duration**: 1.5 weeks  
**Goal**: Build dashboard layout, fetch/display real-time execution metrics

#### Story 9.1.1 — Dashboard Layout & Navigation Structure (4 sp)
**Description**: Create main dashboard page with metric cards, tabbed views, and responsive grid.

**Tasks**:
- [ ] Create `ExecutionDashboard.tsx` component (main container)
- [ ] Design responsive grid layout (4 metric cards on desktop, 2 on tablet, 1 on mobile)
- [ ] Add metric cards component: Total, Running, Completed, Failed, Suspended, Incidents
- [ ] Implement tabbed navigation: "Processes" | "Incidents" | "Activities" (tabs)
- [ ] Add date range selector (Last 24h, 7 days, 30 days, Custom)
- [ ] Add refresh button with loading state
- [ ] Add breadcrumb navigation: "Admin > Dashboard"
- [ ] Integrate into `App.tsx` routing with `path: 'dashboard'`
- [ ] Add "Dashboard" link to `Sidebar.tsx`

**Acceptance Criteria**:
- Dashboard renders without errors
- Metric cards display in responsive grid
- Tab navigation switches between views without page reload
- Date range selector filters data
- Refresh button updates all metrics
- Responsive design on mobile, tablet, desktop
- Loading states show spinners
- Initial metric data loads on mount

---

#### Story 9.1.2 — Real-time Execution Metrics Calculation (4 sp)
**Description**: Implement backend aggregation endpoint for execution statistics.

**Tasks**:
- [ ] Create `ExecutionMetricsService.kt` with aggregation logic
- [ ] Implement method: `getExecutionMetrics(dateRange: DateRange): ExecutionMetricsDto`
- [ ] Calculate: total instances, running, completed, failed, suspended, incidents
- [ ] Implement method: `getMetricsPerProcess(): Map<String, ProcessMetricsDto>`
- [ ] Create `ExecutionMetricsController.kt` with endpoints:
  - `GET /admin/metrics/execution` → returns `ExecutionMetricsDto`
  - `GET /admin/metrics/processes` → returns `List<ProcessMetricsDto>`
  - `GET /admin/metrics/execution?from=2026-05-20&to=2026-05-26` (date range filter)
- [ ] Create DTOs: `ExecutionMetricsDto`, `ProcessMetricsDto`
- [ ] Add database query optimization (single query with group-by if possible)
- [ ] Add caching layer for metrics (TTL: 30 seconds)

**Acceptance Criteria**:
- Endpoints return correct aggregated data
- Date range filtering works correctly
- Metrics match manual count verification
- Response time < 500ms for 10k instances
- Caching reduces database load
- 95%+ test coverage for metrics calculation

---

#### Story 9.1.3 — Dashboard Data Integration & Polling (4 sp)
**Description**: Fetch metrics from backend and display with polling/refresh.

**Tasks**:
- [ ] Extend `adminService.ts` with metrics endpoints:
  - `getExecutionMetrics(dateRange?: DateRange): Promise<ExecutionMetricsDto>`
  - `getMetricsPerProcess(): Promise<List<ProcessMetricsDto>>`
- [ ] Implement `useEffect()` in `ExecutionDashboard.tsx` to fetch metrics on mount
- [ ] Add polling timer: Auto-refresh every 30 seconds (configurable)
- [ ] Add manual refresh button that fetches immediately
- [ ] Display loading spinners while fetching
- [ ] Handle API errors with user-friendly error messages
- [ ] Add metric card animations (number transitions)
- [ ] Add timestamp of last update ("Updated at HH:MM")

**Acceptance Criteria**:
- Metrics load and display within 2 seconds of component mount
- Auto-refresh works every 30 seconds
- Manual refresh button triggers immediate update
- Metrics animate when updating
- Error messages display if fetch fails
- Polling stops if component unmounts

---

### Phase 9.2: Process & Incident Views with Filtering
**Effort**: 16 story points  
**Duration**: 2 weeks  
**Goal**: Implement tabbed "Processes" view with list/grid and "Incidents" view with drill-down

#### Story 9.2.1 — Process Execution List & Statistics Grid (5 sp)
**Description**: Display processes with execution stats: count, running, failed, avg execution time.

**Tasks**:
- [ ] Create `ProcessListView.tsx` component
- [ ] Design process grid with columns: Process ID, Process Name, Total, Running, Failed, Avg Time, Last Executed
- [ ] Fetch `GET /admin/metrics/processes` data
- [ ] Implement clickable rows → navigate to Instance Search filtered by process
- [ ] Add sorting by column headers (Name, Total, Running, Failed, Avg Time)
- [ ] Add process search/filter input (fuzzy search on process name)
- [ ] Show process metadata: owner, tags, created date (optional columns)
- [ ] Color-code status cells: Running (blue), Failed (red), Completed (green)
- [ ] Add "View Instances" button per process row
- [ ] Pagination: Show 10 processes per page (configurable)

**Acceptance Criteria**:
- Process list renders with correct statistics
- Sorting works on all columns
- Search filters processes in real-time
- Row click navigates to Instance Search with process filter
- Color coding provides clear visual status
- Responsive on mobile (stack into card view)
- Pagination controls visible and functional

---

#### Story 9.2.2 — Advanced Filtering Engine (5 sp)
**Description**: Implement multi-criteria filter UI and backend filter support.

**Tasks**:
- [ ] Create `FilterPanel.tsx` component with filter controls
- [ ] Implement filters:
  - **Status**: Checkboxes (Running, Completed, Failed, Suspended, Incident)
  - **Process**: Dropdown or multi-select (auto-populated from available processes)
  - **Date Range**: From/To date pickers (or quick presets)
  - **Nesting Level**: Dropdown (Root, Nested, Any)
  - **Assignee**: Input field or user dropdown (if user management exists)
- [ ] Create filter state management (useState or Context API)
- [ ] Add "Apply Filters" button and "Clear Filters" button
- [ ] Add filter pills display showing active filters with X to remove
- [ ] Extend backend `GET /admin/processes/instances` with query parameters:
  - `?status=RUNNING&process=ProcessID&from=2026-05-20&to=2026-05-26&nestingLevel=ROOT`
- [ ] Implement backend filter logic in `ProcessService.kt`
- [ ] Add saved filter presets: "My Running Instances", "Failed Last 24h", etc.

**Acceptance Criteria**:
- All filters apply correctly to results
- Backend query parameters sanitized (prevent SQL injection)
- Filter pills show active filters clearly
- Clear filters button resets all selections
- Filters are URL-preservable (query string state)
- Performance maintained with 10k instances and complex filters

---

#### Story 9.2.3 — Incidents & Errors View (6 sp)
**Description**: Create dedicated "Incidents" tab showing failed/suspended instances with error details.

**Tasks**:
- [ ] Create `IncidentsView.tsx` component
- [ ] Fetch instances with status IN (FAILED, SUSPENDED, ERROR)
- [ ] Design incidents table: Instance ID, Process, Status, Error Message, Created At, Actions
- [ ] Add error detail preview (truncated error message in table)
- [ ] Implement click-to-expand error details (modal or side panel)
- [ ] Show error stack trace and node where error occurred
- [ ] Add "Retry" button for suspended instances (retries failed node)
- [ ] Add "Retry All" button to retry all selected incidents (bulk action)
- [ ] Add "Delete Instance" button to clean up resolved incidents
- [ ] Sort by: Latest, Oldest, Status, Process
- [ ] Add filter: Incident type (Timeout, Validation Error, External API Error, etc.)
- [ ] Add escalation indicator (for incidents > 1 hour old)

**Acceptance Criteria**:
- Incidents load and display correctly
- Error details expand/collapse without page reload
- Retry button successfully retries failed nodes
- Bulk retry works for multiple incidents
- Error classification works correctly
- Escalation indicators appear for old incidents
- Delete instance removes from incidents list

---

### Phase 9.3: Performance & Analytics Extensions
**Effort**: 12 story points  
**Duration**: 1.5 weeks  
**Goal**: Add execution performance insights, SLA tracking, and basic analytics

#### Story 9.3.1 — Execution Time Analytics & Trends (4 sp)
**Description**: Display process execution time statistics and historical trends.

**Tasks**:
- [ ] Create `PerformanceMetricsService.kt` in backend
- [ ] Implement method: `getExecutionTimeStats(processId?: String): ExecutionTimeStatsDto`
- [ ] Calculate: avg execution time, min, max, p50, p95, p99 latency
- [ ] Implement method: `getExecutionTrendOverTime(processId: String, bucketSize: Int): List<TrendDataPoint>`
- [ ] Create `PerformanceTab.tsx` component
- [ ] Render line chart showing execution time trend (Chart.js or Recharts)
- [ ] Display stat cards: Avg, Min, Max, P95 latency
- [ ] Add process selector to drill down into specific process performance
- [ ] Add date range picker for trend window
- [ ] Color alerts for P95/P99 spikes (yellow/red when exceeds baseline)

**Acceptance Criteria**:
- Stats calculate correctly and match manual verification
- Trend chart displays smoothly
- Performance < 1s for 1-year trend data (10k instances)
- Process selector filters to specific process
- Alerts trigger for latency spikes
- Chart responsive on mobile

---

#### Story 9.3.2 — SLA Monitoring & Compliance (4 sp)
**Description**: Track SLA compliance and flag instances violating time thresholds.

**Tasks**:
- [ ] Create `SLAConfig` entity: process, thresholdMinutes, severity
- [ ] Create `SLAService.kt` to evaluate SLA compliance
- [ ] Add `GET /admin/sla/status` endpoint
- [ ] Implement method: `evaluateSLACompliance(instance: ProcessInstance): SLAStatus`
- [ ] Create `SLAStatus` enum: COMPLIANT, AT_RISK (80%+), VIOLATED
- [ ] Create `SLAMonitoringTab.tsx` component
- [ ] Display table: Instance ID, Process, SLA Target, Time Elapsed, Status
- [ ] Color-code rows: Green (compliant), Yellow (at-risk), Red (violated)
- [ ] Add SLA configuration UI (admin page) to set thresholds per process
- [ ] Add SLA alerts in incident view
- [ ] Export SLA compliance report (CSV)

**Acceptance Criteria**:
- SLA evaluation accurate to second
- At-risk flagged at 80% threshold
- Color coding clear and consistent
- SLA config CRUD works
- Report export includes all required fields
- Performance < 500ms for 10k instances

---

#### Story 9.3.3 — Real-time Activity Feed (4 sp)
**Description**: Create scrollable activity feed showing recent process events.

**Tasks**:
- [ ] Create `ActivityFeed.tsx` component
- [ ] Implement "Activities" tab in dashboard
- [ ] Fetch recent events: Instance started, Node completed, Task assigned, Instance failed, etc.
- [ ] Display feed items: Timestamp, Event Type, Process, Instance ID, Status Badge
- [ ] Add event filters: All, Started, Completed, Failed, Tasks, etc.
- [ ] Click event → navigate to instance detail
- [ ] Auto-scroll to latest events (or fixed position with scroll)
- [ ] Add time grouping: "Today", "Yesterday", "This Week", "Older"
- [ ] Pagination: Load more older events (lazy load)

**Acceptance Criteria**:
- Activity feed loads recent events correctly
- Filtering works without page reload
- Click navigates to correct instance
- Time grouping organizes feed logically
- Pagination loads older events
- Feed updates in real-time if WebSocket available (fallback to polling)

---

### Phase 9.4: UI/UX Polish & Accessibility
**Effort**: 8 story points  
**Duration**: 1 week  
**Goal**: Refine dashboard UX, accessibility, and mobile responsiveness

#### Story 9.4.1 — Responsive Design & Mobile Optimization (3 sp)
**Description**: Ensure dashboard is fully responsive and mobile-friendly.

**Tasks**:
- [ ] Test on mobile (iPhone 12), tablet (iPad), desktop (1920x1080)
- [ ] Refactor metric cards to single column on mobile
- [ ] Convert process list table to card view on mobile
- [ ] Collapse filter panel on mobile (toggle button)
- [ ] Stack tabs vertically on small screens (or horizontal scroll)
- [ ] Test touch interactions (hover effects, click targets)
- [ ] Optimize chart sizing for mobile
- [ ] Add viewport meta tags
- [ ] Test with Chrome DevTools mobile emulation

**Acceptance Criteria**:
- Dashboard renders correctly on all screen sizes
- Touch targets >= 44px on mobile
- No horizontal scroll on mobile
- Charts responsive and readable on mobile
- Performance acceptable on mobile devices
- Accessibility score >= 95 on Lighthouse

---

#### Story 9.4.2 — Accessibility & Keyboard Navigation (3 sp)
**Description**: Ensure dashboard meets WCAG 2.1 AA standards.

**Tasks**:
- [ ] Add ARIA labels to all interactive elements
- [ ] Implement keyboard navigation: Tab, Enter, Escape
- [ ] Add skip-to-content link
- [ ] Ensure color contrast >= 4.5:1 for text
- [ ] Test with screen reader (NVDA/JAWS)
- [ ] Add focus indicators on all buttons
- [ ] Implement tab order (logical flow through dashboard)
- [ ] Test with keyboard only (no mouse)

**Acceptance Criteria**:
- Axe DevTools scan: 0 violations
- Keyboard navigation works for all controls
- Screen reader announces all important content
- Color contrast passes WCAG AA
- Focus indicators visible and clear
- Skip-to-content link functional

---

#### Story 9.4.3 — Dark Mode Support & Theme Consistency (2 sp)
**Description**: Extend dashboard colors to support dark mode toggle.

**Tasks**:
- [ ] Add dark mode class toggle (add to `<html>` element or Context)
- [ ] Review all Tailwind colors: Ensure dark variants applied (e.g., `dark:bg-slate-900`)
- [ ] Test metric card colors in dark mode
- [ ] Test chart colors legible in dark mode (Chart.js dark theme)
- [ ] Add theme toggle button in dashboard header or user menu
- [ ] Persist theme preference in localStorage
- [ ] Ensure consistent colors across modeler and admin
- [ ] Test print mode (print stylesheet for reports)

**Acceptance Criteria**:
- Dark mode toggle works and persists
- All colors legible in dark mode
- Charts render correctly in dark mode
- Theme consistent across all admin pages
- Print stylesheet renders correctly

---

### Phase 9.5: Documentation & QA
**Effort**: Additional 0 sp (covered by tech writer in parallel)  
**Duration**: Ongoing  
**Goal**: Document dashboard features, create test scenarios, and prepare user guides

#### Story 9.5.1 — User Guide & Dashboard Documentation
**Description**: Create comprehensive dashboard documentation for operations teams.

**Tasks**:
- [ ] Create `docs/easy-admin-dashboard-overview.md`
- [ ] Document dashboard sections: Metrics, Processes, Incidents, Activities, Performance
- [ ] Add screenshots with annotations
- [ ] Create quick-start guide for new operators
- [ ] Document filtering syntax and best practices
- [ ] Create troubleshooting guide: Common issues and solutions
- [ ] Add video tutorial (optional)
- [ ] Document keyboard shortcuts and accessibility

**Documentation Topics**:
- Dashboard layout and components
- Metric definitions and calculations
- How to filter and search instances
- How to investigate incidents
- How to interpret performance metrics
- SLA configuration and monitoring
- Activity feed interpretation
- Best practices for operations

---

#### Story 9.5.2 — QA Test Scenarios & Acceptance Criteria
**Description**: Define comprehensive test scenarios and acceptance criteria matrix.

**QA Test Scenarios**:
1. **Metrics Accuracy**: Verify all metrics match manual count (10 scenarios)
2. **Filtering**: Test each filter combination (25 scenarios)
3. **Performance**: Load time < 2s with 10k instances (5 scenarios)
4. **Incident Management**: Retry, escalate, delete incidents (8 scenarios)
5. **Mobile Responsiveness**: Test on 5 device types (5 scenarios)
6. **Accessibility**: Screen reader + keyboard nav (8 scenarios)
7. **Real-time Updates**: Verify polling works and updates display (4 scenarios)
8. **Error Handling**: Test error states and user feedback (6 scenarios)

**Total QA Scenarios**: 61 automated + manual tests

---

## Success Criteria & Metrics

| Metric | Target | Validation Method |
|--------|--------|-------------------|
| **Dashboard Load Time** | < 2 seconds | Browser DevTools Network tab |
| **Metric Accuracy** | 100% match with manual count | Query verification |
| **Filter Performance** | < 500ms response time | Performance profiling |
| **Mobile Score** | >= 90 Lighthouse score | Lighthouse CI |
| **Accessibility** | 0 Axe violations (WCAG AA) | Axe DevTools scan |
| **Test Coverage** | >= 90% unit + integration tests | Jest coverage report |
| **Incidents Resolved** | 90% of incidents resolved within SLA | Incident resolution time tracking |
| **User Adoption** | 80% of ops team using dashboard daily | Usage analytics |

---

## Technical Architecture

### Backend Changes
```
Backend Responsibilities:
├── ExecutionMetricsService (aggregations, calculations)
├── ExecutionMetricsController (REST endpoints)
├── SLAService (SLA evaluation)
├── ProcessService (existing, extended with filtering)
├── New Database Indexes: (status, createdAt, processId) for performance
└── Caching Layer: 30-second TTL on metrics
```

### Frontend Changes
```
Frontend Responsibilities:
├── ExecutionDashboard.tsx (main container, layout)
├── ProcessListView.tsx (processes table/grid)
├── IncidentsView.tsx (failed/suspended instances)
├── PerformanceMetricsTab.tsx (charts and analytics)
├── ActivityFeed.tsx (recent events)
├── FilterPanel.tsx (multi-criteria filters)
├── adminService.ts (API integration)
└── Tailwind CSS responsive grid + Chart.js for visualizations
```

---

## Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| **Performance degradation with 100k+ instances** | High | High | Database indexing, caching strategy, pagination limits |
| **Real-time update lag (polling vs. WebSocket)** | Medium | Medium | Implement polling first; upgrade to WebSocket in Phase 10 |
| **Metrics calculation bugs** | Medium | High | Comprehensive unit tests, manual verification before release |
| **Mobile performance issues** | Medium | Medium | Optimize charts, lazy load sections, responsive design testing |
| **Accessibility compliance gaps** | Low | Medium | Third-party accessibility audit, automated testing |

---

## Acceptance Checklist

- [ ] All 5 phases implemented and tested
- [ ] Dashboard metrics accurate to business requirements
- [ ] Filtering supports all defined criteria
- [ ] Incident management workflows complete
- [ ] Performance meets targets (< 2s load time)
- [ ] Mobile responsive on all tested devices
- [ ] Accessibility >= WCAG AA compliance
- [ ] Documentation complete and reviewed
- [ ] 61 QA test scenarios executed and passed
- [ ] 95%+ test coverage (unit + integration)
- [ ] User acceptance testing (UAT) passed
- [ ] Production deployment ready

---

## Dependencies & Notes

**External Dependencies**:
- Chart.js or Recharts for performance trend visualization
- Lucide React icons (already in use)
- Tailwind CSS (already configured)

**Internal Dependencies**:
- Existing `ProcessService` and `ProcessController`
- Existing `ProcessInstance` and `ProcessDefinition` models
- PostgreSQL JSONB for flexible event storage (Phase 9.3.3)

**Future Enhancements** (Phase 10+):
- WebSocket real-time metrics push (replace polling)
- Configurable dashboard widgets
- Export dashboards as PDF reports
- Scheduled SLA reports via email
- Machine learning for anomaly detection
- Integration with Slack/Teams for alerts

---

## Document Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-26 | Process Orchestrator Team | Initial epic creation: 5 phases, 13 user stories, 48 sp total |

