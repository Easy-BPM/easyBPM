# AGENTS.md

## Big picture
- `:` is the Spring Boot/Kotlin monolith; `:worker` is a second Spring Boot app that consumes RabbitMQ work and reuses backend classes via `implementation(project(":"))` (`build.gradle.kts`, `worker/build.gradle.kts`).
- Runtime is normally: PostgreSQL + RabbitMQ + backend + worker + 3 Vite apps. See ports and env wiring in `docker-compose.yml`.
- Process definitions are **not** stored as BPMN XML at runtime; the backend persists an internal JSON graph in `ProcessDefinition.definitionJson`. The main export shape is built in `easybpmn-modeler/App.tsx` (`buildExportObject()`).
- Engine orchestration lives in `src/main/kotlin/com/easy/bpm/service/ProcessService.kt`: deploy -> start -> `executeNodes()` -> persist `current_nodes` / `node_history` as JSONB.
- Human work is split into `task` + `task_variable` rows. Task completion logic is in `src/main/kotlin/com/easy/bpm/service/TaskService.kt`; task APIs return typed variables through `TaskResponseDto`.
- Call activity/subprocess support is real code, not just docs: parent instances suspend to `WAITING`, child instances carry `parentInstanceId`, `callActivityNodeId`, `nestingLevel`, and mappings are persisted through `CallActivityHandler.kt` + `ProcessInstance.kt`.
- External/API work is async by default: backend publishes to RabbitMQ, `worker/src/main/kotlin/com/easy/bpm/worker/WorkerListener.kt` performs HTTP/auth/retry/idempotency, and `RabbitListenerService.kt` resumes the process on completion/DLQ.
- Code-task support exists in backend/modeler/admin. Backend endpoints start at `/code-tasks` (`CodeTaskController.kt`); reflection execution lives in `CodeExecutionService.kt`.

## Developer workflows
- Backend + worker local startup from repo root:
```bash
cd /Users/nathanyel/IdeaProjects/easyBPM
docker-compose up -d
./gradlew bootRun
./gradlew :worker:bootRun
```
- Frontend/dev docs servers:
```bash
cd /Users/nathanyel/IdeaProjects/easyBPM/easy-bpm-admin && npm install && npm run dev
cd /Users/nathanyel/IdeaProjects/easyBPM/easybpmn-modeler && npm install && npm run dev
cd /Users/nathanyel/IdeaProjects/easyBPM/easy-bpm-task-portal && npm install && npm run dev
cd /Users/nathanyel/IdeaProjects/easyBPM/docs-site-working && npm install && npm start
```
- Primary backend verification is `./gradlew test`. Integration tests use the shared PostgreSQL Testcontainer in `src/test/kotlin/com/easy/bpm/integration/IntegrationTestBase.kt`; do not assume H2 behavior matches them.
- For frontend sanity checks, prefer `npm run build` per app. Only `easybpmn-modeler` currently exposes `npm run lint` (`tsc --noEmit`).

## Codebase-specific conventions
- Prefer **code over docs** when they disagree. Example: the public start endpoint is `POST /processes/{processId}/start` by process key in `ProcessController.kt`, while older docs/examples still show numeric definition IDs.
- The modeler uses lowercase/kebab internal node types (`'user-task'`, `'api-task'`, `'parallel-gateway'`), then exports backend-facing PascalCase names such as `HumanTask`, `APITask`, `ParallelGateway` in `easybpmn-modeler/App.tsx`.
- User-task form references are stable string IDs (`formId`/form key), not just DB IDs. Resolution order is: configured `config.formId` -> numeric DB id fallback -> form name fallback (`resolveUserTaskForm()` in both `ProcessService.kt` and `TaskService.kt`).
- Keep variable payloads as native JSON. `TaskController.kt` accepts raw JSON maps, and both process/task variables are stored as JSONB; avoid serializing everything to strings unless the existing API already does so.
- Paging/sorting is intentionally sanitized in services (`ProcessService.kt`, `TaskService.kt`); if a new sortable field is added, update the allow-lists or the UI sort will silently fall back.
- Documentation is centralized in `docs-site-working/`. Do not add parallel docs folders.

## Integration points and gotchas
- All three frontends default to `http://localhost:8080` and optionally honor `VITE_API_BASE_URL` (`easy-bpm-admin/services/adminService.ts`, `easybpmn-modeler/services/processService.ts`, `easy-bpm-task-portal/services/bpmService.ts`).
- Admin and Task Portal login are currently UI-local placeholders. `bpmService.login()` explicitly falls back when `/login` is missing; don’t build features that assume real auth already exists.
- `WebConfig.kt` currently allows `*` CORS, even though roadmap docs mention tighter localhost-only CORS later.
- Admin UI already calls subprocess hierarchy endpoints (`/processes/instances/{id}/children`, `/parent`, `/mapping`) in `adminService.ts`; verify backend support before changing those screens, because matching controller routes were not found in the backend code.
- Worker auth resolution is environment-based, not model-stored secrets: `bearer -> $REF`, `basic -> ${REF}_USERNAME/${REF}_PASSWORD`, `apikey -> $REF` (`WorkerListener.kt`).
- Message-event examples and debugging queries live in `src/test/resources/examples/README.md`; executable coverage is mainly in `ProcessIntegrationTest.kt` and `CallActivityIntegrationTest.kt`.
- Some docs mention `TaskService.syncTaskVariablesToProcess()`, but that symbol is absent from the current `TaskService.kt`; inspect the current completion flow before refactoring around older docs.
