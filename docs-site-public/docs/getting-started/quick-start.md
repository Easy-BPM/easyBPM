---
title: Quick Start
---

# Quick Start

This guide starts Easy BPM locally with PostgreSQL, RabbitMQ, the backend, the worker, and the web applications.

## Prerequisites

| Dependency | Version |
| --- | --- |
| Java | 21 |
| Docker and Docker Compose | Current stable release |
| Node.js | 18 or newer for web apps, 20 or newer for the docs site |

## Start infrastructure

From the repository root:

```bash
docker-compose up -d postgres rabbitmq
```

RabbitMQ management is available at `http://localhost:15672`.

Default local credentials:

| Service | Username | Password |
| --- | --- | --- |
| PostgreSQL | `meu_usuario` | `minha_senha` |
| RabbitMQ | `easybpm` | `easybpm` |

## Start the backend

```bash
./gradlew bootRun
```

The backend listens on `http://localhost:8080`.

Useful backend URLs:

| URL | Purpose |
| --- | --- |
| `http://localhost:8080/swagger-ui.html` | Interactive OpenAPI UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Health check |
| `http://localhost:8080/actuator/prometheus` | Prometheus metrics |

## Start the worker

In a second terminal:

```bash
./gradlew :worker:bootRun
```

The worker consumes RabbitMQ tasks and resumes processes when async work completes.

## Start web applications

Run each app in its own terminal when needed:

```bash
cd easy-bpm-modeler
npm install
npm run dev
```

```bash
cd easy-bpm-admin
npm install
npm run dev
```

```bash
cd easy-bpm-task-portal
npm install
npm run dev
```

Default local URLs:

| App | URL |
| --- | --- |
| Modeler | `http://localhost:3000` |
| Admin Console | `http://localhost:3001` |
| Task Portal | `http://localhost:3002` |

Depending on your Vite configuration or Docker Compose profile, Admin and Task Portal may also run on `5173` and `5174`.

## Sign in

On first startup, Easy BPM bootstraps an administrator account unless you override it with environment variables.

| Username | Password |
| --- | --- |
| `admin` | `admin` |

Change this password before exposing any environment to external users.

## Verify the API

```bash
curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

The response includes a JWT token. Use it as `Authorization: Bearer <token>` for protected endpoints.
