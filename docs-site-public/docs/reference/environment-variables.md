---
title: Environment Variables
---

# Environment Variables

Use the `EASY_BPM_<APP>_<VARIABLE>` naming standard for customer-facing configuration. Third-party container variables, such as `POSTGRES_DB`, still use the names required by those images.

## Backend API

| Variable | Default |
| --- | --- |
| `EASY_BPM_SERVER_PORT` | `8080` |
| `EASY_BPM_SERVER_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/easybpm` |
| `EASY_BPM_SERVER_DATASOURCE_USERNAME` | `meu_usuario` |
| `EASY_BPM_SERVER_DATASOURCE_PASSWORD` | `minha_senha` |
| `EASY_BPM_SERVER_RABBITMQ_HOST` | `localhost` |
| `EASY_BPM_SERVER_RABBITMQ_PORT` | `5672` |
| `EASY_BPM_SERVER_RABBITMQ_USERNAME` | `easybpm` |
| `EASY_BPM_SERVER_RABBITMQ_PASSWORD` | `easybpm` |
| `EASY_BPM_SERVER_MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,metrics,prometheus` |
| `EASY_BPM_SERVER_MANAGEMENT_HEALTH_SHOW_DETAILS` | `always` |
| `EASY_BPM_SERVER_LOGGING_LEVEL_ROOT` | `INFO` |
| `EASY_BPM_SERVER_LOGGING_LEVEL_APP` | `DEBUG` |
| `EASY_BPM_SERVER_LOGGING_LEVEL_HIBERNATE` | `WARN` |
| `EASY_BPM_SERVER_AI_ENCRYPTION_KEY` | `default-dev-key-change-in-prod-1234` |
| `EASY_BPM_SERVER_TEST_DATA_ENABLED` | `false` |

`EASY_BPM_SERVER_TEST_DATA_ENABLED=true` seeds demo/test process definitions, instances, tasks, and variables at backend startup. Keep it disabled in production.

## Agentic orchestration and AI providers

Agent Process execution can use a credential stored in the Easy BPM credential vault or an environment variable reference. Environment references must include the leading `$` in the deployed model, for example:

```json
{
  "provider": {
    "providerId": "openai",
    "modelName": "gpt-4o-mini",
    "credentialRef": "$OPENAI_API_KEY"
  }
}
```

In that example, the backend process must have `OPENAI_API_KEY` configured in its runtime environment. When `credentialRef` does not start with `$`, the backend treats it as a stored credential ID.

| Variable | Purpose |
| --- | --- |
| `OPENAI_API_KEY` | OpenAI API key used when an Agent Process or AI task references `"$OPENAI_API_KEY"`. |
| `EASY_BPM_SERVER_AI_ENCRYPTION_KEY` | Key used to encrypt credentials stored in the Easy BPM credential vault. Use a unique production value. |

PowerShell local example:

```powershell
$env:OPENAI_API_KEY="sk-..."
$env:EASY_BPM_SERVER_AI_ENCRYPTION_KEY="replace-with-a-production-secret"
```

## Worker

| Variable | Default |
| --- | --- |
| `EASY_BPM_WORKER_PORT` | `0` |
| `EASY_BPM_WORKER_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/easybpm` |
| `EASY_BPM_WORKER_DATASOURCE_USERNAME` | `meu_usuario` |
| `EASY_BPM_WORKER_DATASOURCE_PASSWORD` | `minha_senha` |
| `EASY_BPM_WORKER_RABBITMQ_HOST` | `localhost` |
| `EASY_BPM_WORKER_RABBITMQ_PORT` | `5672` |
| `EASY_BPM_WORKER_RABBITMQ_USERNAME` | `easybpm` |
| `EASY_BPM_WORKER_RABBITMQ_PASSWORD` | `easybpm` |
| `EASY_BPM_WORKER_LOGGING_LEVEL_APP` | `INFO` |

## Security bootstrap

| Variable | Default |
| --- | --- |
| `EASY_BPM_SERVER_SECURITY_ENABLED` | `true` |
| `EASY_BPM_SERVER_SECURITY_JWT_SECRET` | development-only default |
| `EASY_BPM_SERVER_SECURITY_JWT_EXPIRATION_MS` | `3600000` |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_USERNAME` | `admin` |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | `admin` |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_GROUP_CODE` | `ADMIN` |
| `EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_GROUP_NAME` | `Administrators` |

## Web apps

| Variable | Purpose |
| --- | --- |
| `EASY_BPM_ADMIN_API_BASE_URL` | Backend API URL used by the Admin Console. |
| `EASY_BPM_MODELER_API_BASE_URL` | Backend API URL used by the Modeler. |
| `EASY_BPM_MODELER_AGENTIC_ORCHESTRATION` | Enables the feature-flagged Agent Process / Agent Board resource in the Modeler when set to `true`, `1`, `yes`, `on`, or `enabled`. Defaults to disabled. |
| `EASY_BPM_TASK_PORTAL_API_BASE_URL` | Backend API URL used by the Task Portal. |

Run the feature-flagged modeler locally with:

```powershell
$env:EASY_BPM_MODELER_AGENTIC_ORCHESTRATION="true"
npm run dev
```
