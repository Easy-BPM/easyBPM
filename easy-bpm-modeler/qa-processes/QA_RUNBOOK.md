# QA Runbook

Use this runbook to execute the quality phase consistently.

## 1. Prepare Environment

```powershell
docker compose up -d postgres rabbitmq
.\gradlew bootRun
.\gradlew :worker:bootRun
cd easy-bpm-modeler; npm run dev
cd easy-bpm-task-portal; npm run dev
cd easy-bpm-admin; npm run dev
```

Open:

| App | URL |
| --- | --- |
| Backend API | `http://localhost:8080` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| RabbitMQ | `http://localhost:15672` |
| Modeler | Vite URL for `easy-bpm-modeler` |
| Task Portal | Vite URL for `easy-bpm-task-portal` |
| Admin | Vite URL for `easy-bpm-admin` |

## 2. Run Automated Gates

```powershell
.\gradlew test
.\gradlew :worker:classes
cd easy-bpm-modeler; npm run lint
cd easy-bpm-task-portal; npm run lint
cd easy-bpm-admin; npm run test -- --runInBand
```

If an npm project has no `test` script, record that as "not configured" and rely on type check plus manual/E2E coverage.

## 3. Deploy Forms

Deploy all backend form schemas:

```powershell
$headers = @{ "Content-Type" = "application/json" }
Get-ChildItem easy-bpm-modeler\qa-processes\forms-backend\*.json | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/forms" -Headers $headers -InFile $_.FullName
}
```

Then import all `forms-modeler/*.modeler.json` through the Modeler UI and confirm editable state matches the backend schema.

## 4. Deploy Agent Process

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/agent-processes" `
  -Headers @{ "Content-Type" = "application/json" } `
  -InFile "easy-bpm-modeler\qa-processes\agent-processes\customer-support-resolution.agent-process.json"
```

For provider-backed testing, confirm the provider endpoint and credential reference are reachable before using this as a hard pass/fail gate.

## 5. Deploy BPM Processes

Deploy these as runtime candidates:

```text
qa-user-task-forms.json
qa-gateway-routing.json
qa-api-components.json
qa-message-timer-events.json
qa-agent-process-call.json
```

Treat `qa-code-task-component.json` as component/contract coverage until CodeTask is routed by the runtime engine.

## 6. Execute Manual Runtime Cases

For each deployed process:

1. Start an instance from the Task Portal or `POST /processes/{id}/start`.
2. Record process definition id and process instance id.
3. Complete human tasks from the Task Portal.
4. Verify process variables and task variables.
5. Verify timeline/event rows.
6. Verify final status or expected waiting status.

Suggested evidence table:

| Process | Definition id | Instance id | Expected status | Actual status | Evidence |
| --- | --- | --- | --- | --- | --- |
| User task forms | | | COMPLETED | | |
| Gateway routing | | | COMPLETED | | |
| API components | | | COMPLETED or expected wait | | |
| Message/timer events | | | COMPLETED after correlation/timer | | |
| Agent process call | | | Human review waiting, then COMPLETED | | |

## 7. Defect Classification

| Severity | Definition |
| --- | --- |
| P0 | Data loss, security bypass, process corruption, unrecoverable runtime failure in a supported gate |
| P1 | Supported process cannot deploy/start/complete, incorrect routing, broken form submission, failed worker callback |
| P2 | UI state loss, unclear validation, partial observability, non-critical admin/reporting issue |
| P3 | Copy, layout, minor polish, test-only fixture cleanup |

Known gap for this cycle:

- CodeTask full process runtime execution is not a pass gate until `ProcessExecutionEngine` invokes `CodeTaskHandler`.
