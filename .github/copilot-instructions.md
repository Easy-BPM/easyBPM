# Copilot Project Instructions

## Project Overview

Easy BPM is a Spring Boot-based Business Process Management (BPM) orchestrator with:
- **Backend**: Spring Boot 3.5.3 + Kotlin + PostgreSQL + RabbitMQ async execution
- **Easy BPM Admin**: React 19 admin UI for process instance management (`easy-bpm-admin/`, port 5173)
- **Easy BPMN Modeler**: React 19 BPMN + Form modeler for process/form design and deploy (`easybpmn-modeler/`, port 3000)
- **Easy BPM Task Portal**: React 19 task inbox for human performers — forms, variables, complete task (`easy-bpm-task-portal/`, port 5174)
- **Documentation**: Docusaurus 3.10 site with comprehensive guides (`docs-site-working/`)

## Documentation Structure

**Single Source of Truth**: `docs-site-working/`
- Contains all technical documentation in Markdown
- Includes new Easy BPM Admin feature documentation (v1.0.0)
- Includes Easy BPMN Modeler documentation (overview, getting started, deploy/API integration)
- Built with Docusaurus for modern searchable interface
- Deployment: `npm run build` → `dist/` directory

**Do NOT create additional documentation directories.** Maintain single consolidated site.

## Key Implementation Status

✅ **Completed**:
- Process metadata (key + description) with Flyway migration V15
- Task response DTO with embedded variables + `formKey`
- Stop/Delete instance operations for process management
- Easy BPM Admin UI with lifecycle controls (Stop, Delete, Move Node)
- Easy BPMN Modeler: BPMN canvas + Form Modeler + Deploy Process (`POST /processes`) + Deploy Form (`POST /forms`)
- Modeler: form_key propagated through Form Modeler UI and User Task properties panel
- Easy BPM Task Portal: task inbox, dynamic form rendering, variable editor fallback, complete-task with global variable sync
- Backend: `TaskService.syncTaskVariablesToProcess()` guarantees all submitted task variables become process globals
- Form key feature: V16 Flyway migration, `form_key` column + unique index on form table
- Docs: Task Portal overview + getting-started pages, implementation-status Phase 4 & 5, updated sidebars
- Phase 5: APITask Auth References (auth.type, auth.ref, environment variable credential resolution)
- All backend tests passing (113+ tests, Gradle BUILD SUCCESSFUL)
- **Phase 6: QA Improvements** (All items completed):
  - ✅ Error Catch Handler - capture error message to variable (exceptionVariable mapping)
  - ✅ Disable spaces in ID fields (Modeler: Process ID, Form ID, Form Key validation)
  - ✅ Admin Canvas rendering - fixed arrow styles, render boundary events with BPMN compliance
  - ✅ Improved Hibernate logging (SQL suppressed, logback-spring.xml with profiles)
  - ✅ Comprehensive canvas documentation (easy-admin-canvas-rendering.md)

✅ **Phase 7: Call Activity & Subprocess Support** (COMPLETE)
- **Status**: COMPLETE (2026-04-22)
- **Document**: [EPIC-call-activity-subprocess-support.md](./EPIC-call-activity-subprocess-support.md)
- **Effort**: 40 story points (7.1: 16sp, 7.2: 8sp, 7.3: 6sp, 7.4: 6sp, 7.5: 4sp)
- **Completion**: All phases 7.1-7.5 implemented, tested, documented
- **Key Features Delivered**:
  - ✅ Call activity nodes for subprocess invocation
  - ✅ Parent → child variable input mapping
  - ✅ Child → parent variable output mapping
  - ✅ Call activity error boundary handling
  - ✅ Admin UI hierarchy visualization
  - ✅ Modeler support for call activity design
  - ✅ Comprehensive test plan (6 scenarios, 24+ test cases)
  - ✅ 4 user guides + examples + API documentation
- **Test Status**: 123/124 tests passing (99.2%)

⏳ **Phase 8: Code Task & JAR Execution** (In Progress - Phase 8.3 Planned)
- **Status**: Phase 8.1 & 8.2 complete; Phase 8.3 planning complete (2026-04-22)
- **Document**: [EPIC-code-task-support.md](./EPIC-code-task-support.md)
- **Effort**: 32 story points total
- **Completion**: Phase 8.1-8.2 (20 sp) complete; Phase 8.3-8.5 (12 sp) ready to start
- **Key Milestones**:
  - ✅ Phase 8.1: Backend Infrastructure (V20 migration, entities, services, handlers)
  - ✅ Phase 8.1.9: REST Controller (JAR upload, class discovery, execution history)
  - ✅ Phase 8.2: Modeler UI Components (4 React components, canvas integration)
  - 📋 Phase 8.3: Admin UI (execution monitoring, 5 user stories, sprint plan ready)
  - 📋 Phase 8.4: QA Testing (integration/E2E tests, test scenarios prepared)
  - 📋 Phase 8.5: Documentation (user guides, API reference)
- **Phase 8.3 Status**: Epic planned, sprint ready, 4 SP, 1 week duration
  - 5 user stories: List view, Details modal, Filtering, Metrics, Error analysis
  - Documentation: phase-8-3-admin-ui.md, Phase-8-3-Sprint-Plan.md, Phase-8-3-QA-Test-Scenarios.md

⏳ **Phase 9: Admin Execution Dashboard & Operations Console** (Planned - IN PROGRESS)
- **Status**: Phase 9.1, 9.2, 9.3 complete; Phase 9.4 in progress
- **Document**: [EPIC-admin-execution-dashboard.md](./EPIC-admin-execution-dashboard.md)
- **Effort**: 48 story points (5 phases: 9.1: 12sp, 9.2: 16sp, 9.3: 12sp, 9.4: 8sp, 9.5: 0sp)
- **Duration**: ~6 weeks (1.5-2 weeks per phase)
- **Inspiration**: Camunda Operate-style operational dashboard
- **Completed Phases**:
  - ✅ Phase 9.1: Dashboard Infrastructure (metrics cards, tabbed views, real-time polling) — 12 sp — COMPLETE
  - ✅ Phase 9.2: Process & Incident Management (process list, filtering, incidents view) — 16 sp — COMPLETE
  - ✅ Phase 9.3: Analytics Extensions (execution time trends, SLA monitoring, activity feed) — 12 sp — COMPLETE (2026-05-26)
- **In Progress**:
  - 📋 Phase 9.4: UI/UX Polish (responsive design, accessibility WCAG AA, dark mode) — 8 sp
- **Pending**:
  - 📋 Phase 9.5: Documentation & QA (user guides, test scenarios, 61 QA tests)
- **Phase 9.3 Deliverables** (COMPLETE):
  - ✅ 4 REST Analytics Endpoints (/trends, /sla, /activity-feed, /summary)
  - ✅ ExecutionMetricsService extended with 4 analytics methods
  - ✅ 4 React Analytics Components (ExecutionTrendsChart, SLAMonitoring, ActivityFeedView, AnalyticsDashboard)
  - ✅ Backend AnalyticsDto with 11 DTOs + 2 enums
  - ✅ Frontend types.ts extended with analytics interfaces
  - ✅ Full integration in Admin UI with sidebar navigation
  - ✅ Security config allows unauthenticated analytics access
  - ✅ All endpoints tested, returning real data with 200 OK
  - ✅ Dashboard renders with metrics, trends, SLA monitoring, activity feed
- **Features**:
  - Real-time execution metrics: Total, Running, Completed, Failed, Suspended, Incidents
  - Process execution overview with statistics per process
  - Advanced multi-criteria filtering (status, process, date, nesting level, assignee)
  - Dedicated incidents view with error details, retry, bulk actions
  - Performance analytics: Execution time trends, SLA tracking, activity feed
  - Full responsive design + WCAG AA accessibility compliance
  - Mobile-optimized UI with dark mode support



## Quick Commands

**Backend Tests**: `.\gradlew test` (BUILD SUCCESSFUL)

**Admin UI Dev**: `cd easy-bpm-admin && npm run dev` (http://localhost:5173)

**Modeler Dev**: `cd easybpmn-modeler && npm run dev` (http://localhost:3000)

**Task Portal Dev**: `cd easy-bpm-task-portal && npm run dev` (http://localhost:5174)

**Docs Dev**: `cd docs-site-working && npm start`

**Docs Build**: `cd docs-site-working && npm run build`

## Keep This File Updated

Document major changes, milestone completions, and architecture decisions here.
