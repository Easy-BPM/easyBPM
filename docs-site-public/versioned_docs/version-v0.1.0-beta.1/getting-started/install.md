---
title: Installation
---

# Installation

Easy BPM can run as individual local processes during development or as containers for shared environments.

## Local development layout

| Runtime | Command |
| --- | --- |
| PostgreSQL and RabbitMQ | `docker-compose up -d postgres rabbitmq` |
| Backend API | `./gradlew bootRun` |
| Worker | `./gradlew :worker:bootRun` |
| Modeler | `cd easy-bpm-modeler && npm run dev` |
| Admin Console | `cd easy-bpm-admin && npm run dev` |
| Task Portal | `cd easy-bpm-task-portal && npm run dev` |

Install web dependencies before first run:

```bash
npm install
```

## Containerized development

To run from source with local image builds:

```bash
docker-compose up -d
```

The stack includes PostgreSQL, RabbitMQ, backend, worker, Modeler, Admin Console, Task Portal, and the documentation site.

To run from published GitHub Container Registry images:

```bash
cp deploy/docker/.env.beta.example .env.easybpm
docker compose --env-file .env.easybpm \
  -f deploy/docker/docker-compose.beta.yml \
  up -d
```

Set `IMAGE_REGISTRY=ghcr.io/<org-or-user>` and `IMAGE_TAG=<release-tag>` in `.env.easybpm`.

## Backend database setup

The backend uses Flyway migrations from `src/main/resources/db/migration`. Migrations run automatically when the backend starts with `spring.flyway.enabled=true`.

Use a persistent PostgreSQL volume in all non-temporary environments. Do not use a throwaway database for customer workflows because process state, documents, users, and audit records are stored there.

## Build artifacts

Build the backend:

```bash
./gradlew clean build
```

Build web apps:

```bash
cd easy-bpm-modeler && npm run build
cd ../easy-bpm-admin && npm run build
cd ../easy-bpm-task-portal && npm run build
```

## Production guidance

For production, run each runtime as an immutable image:

| Image | Runtime |
| --- | --- |
| `ghcr.io/<org-or-user>/easybpm-backend:<tag>` | Backend API |
| `ghcr.io/<org-or-user>/easybpm-worker:<tag>` | Async worker |
| `ghcr.io/<org-or-user>/easybpm-modeler:<tag>` | Modeler UI |
| `ghcr.io/<org-or-user>/easybpm-admin:<tag>` | Admin UI |
| `ghcr.io/<org-or-user>/easybpm-task-portal:<tag>` | Task Portal UI |

Use managed PostgreSQL and RabbitMQ where possible. Put HTTPS, host-based routing, request limits, and access logs in front of the web apps and backend API.
