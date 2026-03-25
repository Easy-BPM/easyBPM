# Easy BPM — Local dev notes

Overview
- This repository is a Spring Boot BPM engine. Recent changes add RabbitMQ-based async execution for `ServiceTask` nodes and a small `worker/` service that consumes service-task requests and publishes completions.

Key changes
- Added RabbitMQ service to `docker-compose.yml`.
- Monolith publishes service-task requests (see `src/main/kotlin/com/easy/bpm/messaging/RabbitPublisher.kt`).
- Monolith listens for completions in `src/main/kotlin/com/easy/bpm/messaging/RabbitListenerService.kt` and `ProcessService` has `handleServiceTaskCompleted(...)` to continue execution.
- A simple worker scaffold lives in `worker/` and executes HTTP-style integrations, publishing completion events back to the exchange.

Prerequisites
- Java 21
- Docker & Docker Compose
- (Windows) use `gradlew.bat` or WSL; on mac/linux use `./gradlew`

Quick start (local)

1. Start infrastructure (Postgres + RabbitMQ):

```powershell
docker-compose up -d
docker-compose ps
```

2. Run the monolith (from repo root):

```powershell
.
\gradlew.bat bootRun
```

Or (POSIX):

```bash
./gradlew bootRun
```

3. Run the worker (in a separate terminal):

```powershell
cd worker
.
\gradlew.bat bootRun
```

4. RabbitMQ management UI: http://localhost:15672 (user: `easybpm` / pass: `easybpm`)

Notes
- The monolith no longer executes `ServiceTask` HTTP calls synchronously; it publishes a request message and waits for an external worker to send a completion message.
- The worker implemented here is intentionally minimal (see `worker/src/main/kotlin/com/easy/bpm/worker/WorkerListener.kt`) — extend security, retries, timeouts and idempotency as needed.
- DB migrations remain managed by Flyway (`src/main/resources/db/migration`). Initially the monolith and worker share the same database; a later step is splitting DBs per service.

Example message (ServiceTask request)

```json
{
  "processInstanceId": 123,
  "nodeId": "send-email",
  "properties": {
    "url": "https://example.com/webhook",
    "method": "POST",
    "headers": { "Authorization": "Bearer ..." },
    "body": { "foo": "bar" }
  }
}
```

Next steps
- Harden worker (retries, DLQ, idempotency keys).
- Add health checks and metrics for both services.
- Optionally split services and DB ownership when ready.
