# Getting Started with Easy BPM

Quick start guide to get the Easy BPM engine up and running.

## Prerequisites

- **Java**: OpenJDK 21+
- **Docker & Docker Compose**: For infrastructure (PostgreSQL, RabbitMQ)
- **Gradle**: Build system (included via gradlew)

## Step 1: Start Infrastructure

PostgreSQL and RabbitMQ are required. Use Docker Compose:

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** (port 5432): Database `easybpm` / User: `easybpm` / Pass: `easybpm`
- **RabbitMQ** (port 5672): Web UI at http://localhost:15672

Verify:
```bash
docker ps
```

## Step 2: Start BPM Engine

```bash
./gradlew bootRun
```

Or on Windows:
```powershell
.\gradlew.bat bootRun
```

Server URL: http://localhost:8085

API Docs: http://localhost:8085/swagger-ui.html

## Step 3: Start Worker (Optional)

For external service tasks:

```bash
cd worker
./gradlew bootRun
```

## Step 4: Verify Installation

```bash
curl http://localhost:8085/actuator/health
```

Expected: `{"status":"UP"}`

## First Process

Deploy:
```bash
curl -X POST http://localhost:8085/processes \
  -H "Content-Type: application/json" \
  -d '{
    "nodes": [
      {"id": "start", "type": "StartEvent"},
      {"id": "task1", "type": "HumanTask", "name": "Review"},
      {"id": "end", "type": "EndEvent"}
    ],
    "edges": [
      {"source": "start", "target": "task1"},
      {"source": "task1", "target": "end"}
    ]
  }'
```

Start (save `id` from response):
```bash
curl -X POST http://localhost:8085/processes/{id}/start
```

Get tasks:
```bash
curl http://localhost:8085/tasks
```

Complete:
```bash
curl -X POST http://localhost:8085/tasks/1/complete \
  -H "Content-Type: application/json" \
  -d '{"assignee": "john", "variables": {}}'
```

## Troubleshooting

**Port already in use**:
```bash
./gradlew bootRun --args='--server.port=8081'
```

**Database error**:
```bash
docker-compose down
docker-compose up -d
```

## Next Steps

- [Features & Architecture](./features-architecture.md)
- [API Documentation](./api-controllers.md)
- [Metrics & Observability](./metrics-observability.md)
- [Developer Reference](./developer-quick-reference.md)
