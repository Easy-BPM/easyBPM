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
- Docs: Task Portal overview + getting-started pages, implementation-status Phase 4, updated sidebars
- All backend tests passing (113+ tests, Gradle BUILD SUCCESSFUL)

⏳ **Pending / Next Sprint**:
- CORS configuration for localhost:5173, 5174, and 3000 (all three UIs)
- POST /login endpoint (auth integration — JWT or session)
- Auth token propagation in all three UI services
- POST /processes/{key}/start in Task Portal UI (start new instances from portal)
- Task Portal: filter/search tasks by process key or assignee
- Integration tests: complete-task → assert all submitted variables promoted to process globals

## Quick Commands

**Backend Tests**: `.\gradlew test` (BUILD SUCCESSFUL)

**Admin UI Dev**: `cd easy-bpm-admin && npm run dev` (http://localhost:5173)

**Modeler Dev**: `cd easybpmn-modeler && npm run dev` (http://localhost:3000)

**Task Portal Dev**: `cd easy-bpm-task-portal && npm run dev` (http://localhost:5174)

**Docs Dev**: `cd docs-site-working && npm start`

**Docs Build**: `cd docs-site-working && npm run build`

## Keep This File Updated

Document major changes, milestone completions, and architecture decisions here.
