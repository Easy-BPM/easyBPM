---
title: Configuration
---

# Configuration

Easy BPM is configured with environment variables that follow the `EASY_BPM_<APP>_<VARIABLE>` standard. Third-party container variables, such as `POSTGRES_DB`, keep the names required by those images.

## Required backend settings

| Variable | Default | Description |
| --- | --- | --- |
| `EASY_BPM_SERVER_PORT` | `8080` | Backend HTTP port. |
| `EASY_BPM_SERVER_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/easybpm` | PostgreSQL JDBC URL. |
| `EASY_BPM_SERVER_DATASOURCE_USERNAME` | `meu_usuario` | PostgreSQL username. |
| `EASY_BPM_SERVER_DATASOURCE_PASSWORD` | `minha_senha` | PostgreSQL password. |
| `EASY_BPM_SERVER_RABBITMQ_HOST` | `localhost` | RabbitMQ host. |
| `EASY_BPM_SERVER_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port. |
| `EASY_BPM_SERVER_RABBITMQ_USERNAME` | `easybpm` | RabbitMQ username. |
| `EASY_BPM_SERVER_RABBITMQ_PASSWORD` | `easybpm` | RabbitMQ password. |

## Security settings

| Variable | Default | Description |
| --- | --- | --- |
| `EASY_BPM_SERVER_SECURITY_ENABLED` | `true` | Enables JWT/RBAC enforcement. |
| `EASY_BPM_SERVER_SECURITY_JWT_SECRET` | development-only default | Base64 JWT signing secret. |
| `EASY_BPM_SERVER_SECURITY_JWT_EXPIRATION_MS` | `3600000` | JWT lifetime in milliseconds. |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_USERNAME` | `admin` | First administrator username. |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | `admin` | First administrator password. |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_GROUP_CODE` | `ADMIN` | Bootstrap administrator group code. |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_GROUP_NAME` | `Administrators` | Bootstrap administrator group display name. |

The default administrator credentials are for local startup only. Override them for any shared environment.

## Web app API URL

Each web application reads the backend URL from an app-specific variable:

```bash
EASY_BPM_ADMIN_API_BASE_URL=http://localhost:8080
EASY_BPM_MODELER_API_BASE_URL=http://localhost:8080
EASY_BPM_TASK_PORTAL_API_BASE_URL=http://localhost:8080
```

Set this to the public backend URL for deployed customer environments.

## CORS

By default, the backend allows local browser origins such as `http://localhost:*` and `http://127.0.0.1:*`. For production, configure your platform routing so the web apps and backend share an approved origin or update CORS in the backend security configuration.

## Actuator and metrics

The backend exposes:

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health` | Application and dependency health. |
| `/actuator/metrics` | Runtime metrics index. |
| `/actuator/prometheus` | Prometheus scrape endpoint. |

Control exposure with `EASY_BPM_SERVER_MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`.
