---
title: Environment Variables
---

# Environment Variables

## Backend

| Variable | Default |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/easybpm` |
| `SPRING_DATASOURCE_USERNAME` | `meu_usuario` |
| `SPRING_DATASOURCE_PASSWORD` | `minha_senha` |
| `SPRING_RABBITMQ_HOST` | `localhost` |
| `SPRING_RABBITMQ_PORT` | `5672` |
| `SPRING_RABBITMQ_USERNAME` | `easybpm` |
| `SPRING_RABBITMQ_PASSWORD` | `easybpm` |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,metrics,prometheus` |

## Security bootstrap

| Variable | Default |
| --- | --- |
| `easybpm.security.enabled` | `true` |
| `easybpm.security.bootstrap.admin-username` | `admin` |
| `easybpm.security.bootstrap.admin-password` | `admin` |
| `easybpm.security.bootstrap.admin-group-code` | `ADMIN` |
| `easybpm.security.bootstrap.admin-group-name` | `Administrators` |

## Web apps

| Variable | Purpose |
| --- | --- |
| `VITE_API_BASE_URL` | Backend API URL used by Modeler, Admin Console, and Task Portal. |
