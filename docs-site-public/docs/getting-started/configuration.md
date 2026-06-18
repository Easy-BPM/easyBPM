---
title: Configuration
---

# Configuration

Easy BPM is configured with Spring Boot environment variables for the backend and worker, and Vite environment variables for the web applications.

## Required backend settings

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend HTTP port. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/easybpm` | PostgreSQL JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | `meu_usuario` | PostgreSQL username. |
| `SPRING_DATASOURCE_PASSWORD` | `minha_senha` | PostgreSQL password. |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host. |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port. |
| `SPRING_RABBITMQ_USERNAME` | `easybpm` | RabbitMQ username. |
| `SPRING_RABBITMQ_PASSWORD` | `easybpm` | RabbitMQ password. |

## Security settings

| Variable | Default | Description |
| --- | --- | --- |
| `easybpm.security.enabled` | `true` | Enables JWT/RBAC enforcement. |
| `easybpm.security.bootstrap.admin-username` | `admin` | First administrator username. |
| `easybpm.security.bootstrap.admin-password` | `admin` | First administrator password. |
| `easybpm.security.bootstrap.admin-group-code` | `ADMIN` | Bootstrap administrator group code. |
| `easybpm.security.bootstrap.admin-group-name` | `Administrators` | Bootstrap administrator group display name. |

The default administrator credentials are for local startup only. Override them for any shared environment.

## Web app API URL

Each web application reads the backend URL from:

```bash
VITE_API_BASE_URL=http://localhost:8080
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

Control exposure with `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`.
