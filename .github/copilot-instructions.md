# Copilot Project Instructions

## Project Overview

Easy BPM is a Spring Boot-based Business Process Management (BPM) orchestrator with:
- **Backend**: Spring Boot 3.5.3 + Kotlin + PostgreSQL + RabbitMQ async execution
- **Frontend**: React 19 admin UI (Easy BPM Admin) for process instance management
- **Modeling UI**: React 19 Easy BPMN Modeler integrated in-repo for process/form modeling and deploy
- **Documentation**: Docusaurus 3.10 site with comprehensive guides

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
- Task response DTO with embedded variables
- Stop/Delete instance operations for process management
- Easy BPM Admin UI with lifecycle controls (Stop, Delete, Move Node)
- Comprehensive documentation for Admin feature (5 documents, 10K+ lines)
- Easy BPMN Modeler integrated into `bpm/easybpmn-modeler` with Deploy Process button (`POST /processes`)
- Modeler form deployment wired to backend forms API (`POST /forms`)
- Modeler and admin docs/API references aligned to backend default `http://localhost:8085`

⏳ **Pending**:
- POST /login endpoint (auth integration)
- CORS configuration for localhost:5173 and localhost:3001
- POST /processes/{key}/start endpoint (deprioritized per user request)

## Quick Commands

**Backend Tests**: `.\gradlew test` (113 tests passing)

**Admin UI Dev**: `cd easy-bpm-admin && npm run dev` (http://localhost:5173)

**Docs Build**: `cd docs-site-working && npm run build`

## Keep This File Updated

Document major changes, milestone completions, and architecture decisions here.
