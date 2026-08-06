---
title: Docker Deployment
---

# Docker Deployment

Docker Compose is the fastest way to run a complete Easy BPM stack for demos, pilots, and small beta environments.

## Run with published GHCR images

Easy BPM publishes one image per runtime to GitHub Container Registry:

| Runtime | GHCR image pattern |
| --- | --- |
| Backend API | `ghcr.io/<org-or-user>/easybpm-backend:<tag>` |
| Worker | `ghcr.io/<org-or-user>/easybpm-worker:<tag>` |
| Admin Console | `ghcr.io/<org-or-user>/easybpm-admin:<tag>` |
| Modeler | `ghcr.io/<org-or-user>/easybpm-modeler:<tag>` |
| Task Portal | `ghcr.io/<org-or-user>/easybpm-task-portal:<tag>` |

Create an environment file from the beta example:

```bash
cp deploy/docker/.env.beta.example .env.easybpm
```

Set the image registry and tag:

```dotenv
IMAGE_REGISTRY=ghcr.io/Easy-BPM
IMAGE_TAG=v0.1.0-beta.1
```

Set customer-specific secrets:

```dotenv
POSTGRES_DB=easybpm
POSTGRES_USER=easybpm
POSTGRES_PASSWORD=change-me-postgres

RABBITMQ_DEFAULT_USER=easybpm
RABBITMQ_DEFAULT_PASS=change-me-rabbitmq

EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_USERNAME=admin
EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_PASSWORD=change-me-admin
EASY_BPM_SERVER_SECURITY_JWT_SECRET=replace-with-32-byte-minimum-base64-secret
EASY_BPM_SERVER_AI_ENCRYPTION_KEY=replace-with-strong-ai-credential-key
```

Start the stack:

```bash
docker compose --env-file .env.easybpm \
  -f deploy/docker/docker-compose.beta.yml \
  up -d
```

Open the apps:

| App | URL |
| --- | --- |
| Backend API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| RabbitMQ management | `http://localhost:15672` |
| Modeler | `http://localhost:3000` |
| Admin Console | `http://localhost:3001` |
| Task Portal | `http://localhost:3002` |

:::tip
If the GHCR package is private, authenticate first:

```bash
echo "$GITHUB_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin
```
:::

## Local source compose

```bash
docker-compose up -d
```

This starts:

| Service | Purpose |
| --- | --- |
| `postgres` | PostgreSQL database. |
| `rabbitmq` | RabbitMQ broker and management UI. |
| `backend` | Easy BPM API runtime. |
| `worker` | Async worker. |
| `easy-bpm-admin` | Admin Console. |
| `easy-bpm-task-portal` | Task Portal. |
| `easybpmn-modeler` | Modeler. |

## Beta compose from this repository

For a public beta-style deployment:

```bash
docker compose --env-file .env.beta -f deploy/docker/docker-compose.beta.yml up -d
```

Create `.env.beta` from the example file in `deploy/docker` and change every secret before exposing the environment.

## Production checklist

| Area | Recommendation |
| --- | --- |
| TLS | Put HTTPS in front of all web apps and the API. |
| Secrets | Use environment-specific secrets; do not keep defaults. |
| Database | Use persistent volumes or managed PostgreSQL. |
| RabbitMQ | Use durable storage or managed RabbitMQ. |
| Logs | Centralize backend, worker, and proxy logs. |
| Backups | Back up PostgreSQL and verify restore procedures. |
| Health | Monitor `/actuator/health` and RabbitMQ readiness. |
