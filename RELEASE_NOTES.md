# EasyBPM v0.1.0-beta.1 Release Notes

Initial beta release of EasyBPM.

EasyBPM is a Kotlin/Spring Boot business process engine with a React modeler, admin console, task portal, and async worker runtime. This beta establishes the first end-to-end product baseline: model a process, deploy it, start instances, complete human tasks, run automated work, and inspect runtime state.

## Release Highlights

- First public beta baseline for the EasyBPM mono-repo.
- Spring Boot backend runtime for deploying and executing JSON-based process definitions.
- React/Vite modeler for building processes and forms.
- React/Vite task portal for starting work and completing assigned human tasks.
- React/Vite admin console for operational visibility and administration.
- RabbitMQ-backed worker for asynchronous API and external-service execution.
- PostgreSQL persistence with Flyway-managed schema migrations.
- Docker Compose and Helm deployment assets for beta environments.

## Included Capabilities

### Process Runtime

- Deploy, version, start, stop, and execute process definitions.
- Store process definitions as EasyBPM internal JSON graphs.
- Persist process instances, execution state, and node history.
- Move running instances manually between nodes for operational recovery.
- Use JSONB-backed process and task variables.
- Synchronize task completion variables back to global process state.

### Workflow Elements

- Human tasks with claim and completion flows.
- Dynamic task forms referenced by stable `formId` values.
- Exclusive, parallel, and inclusive gateway execution.
- Message catch and throw events with correlation keys.
- Timer and message timeout handling.
- Call activity and subprocess execution with parent/child instance tracking.
- Variable mappings between parent and child process instances.

### Automation And Integrations

- Async API/service task execution through RabbitMQ.
- Worker application for external API work.
- Code-task jar upload and execution support.
- Reflection-based code class metadata discovery.
- Code-task execution audit history.
- AI task plumbing with provider abstraction and credential storage.

### Documents And Forms

- Form definition deployment and retrieval.
- Dynamic form rendering in the task portal.
- Fallback variable editing for tasks without an attached form.
- Document upload, preview, download, and task-form integration.

### Security And Operations

- JWT authentication.
- RBAC tables and task candidate assignment support.
- Bootstrapped admin user and group.
- Admin security management endpoints and UI.
- Actuator health endpoints.
- Metrics and Prometheus registry support.
- OpenAPI/Swagger UI for backend API exploration.

### Web Applications

- `easy-bpm-modeler`: process and form modeling interface.
- `easy-bpm-admin`: administration, security, metrics, and code-task audit interface.
- `easy-bpm-task-portal`: user-focused task inbox and completion portal.
- `docs-site-working`: documentation site workspace.

## Deployment

This beta can be run locally or deployed as a beta stack.

Recommended release tag:

```text
v0.1.0-beta.1
```

The release workflow publishes images to GitHub Container Registry (`ghcr.io`) under the repository owner and to Docker Hub under `nsandi/easybpm`.

GitHub Container Registry images use one repository per runtime:

- `ghcr.io/<owner>/easybpm-backend:v0.1.0-beta.1`
- `ghcr.io/<owner>/easybpm-worker:v0.1.0-beta.1`
- `ghcr.io/<owner>/easybpm-admin:v0.1.0-beta.1`
- `ghcr.io/<owner>/easybpm-modeler:v0.1.0-beta.1`
- `ghcr.io/<owner>/easybpm-task-portal:v0.1.0-beta.1`

Docker Hub uses one repository with component-prefixed tags:

- `nsandi/easybpm:backend-v0.1.0-beta.1`
- `nsandi/easybpm:worker-v0.1.0-beta.1`
- `nsandi/easybpm:admin-v0.1.0-beta.1`
- `nsandi/easybpm:modeler-v0.1.0-beta.1`
- `nsandi/easybpm:task-portal-v0.1.0-beta.1`

Local development starts PostgreSQL and RabbitMQ with Docker Compose, then runs the backend, worker, and web apps independently. Beta deployment assets are available under `deploy/docker/`, and Kubernetes/SaaS deployment assets are available under `deploy/helm/easybpm/`.

## Beta Notes

This is the first beta release and should be treated as a validation release, not a final production contract.

- APIs, database schema, process JSON format, and deployment values may change before a stable release.
- Upgrade paths between beta builds may require manual migration or environment rebuilds.
- Security defaults and secrets must be reviewed before exposing any deployment publicly.
- Production deployments should place TLS, authentication policy, backups, and monitoring around the stack.
- The repository is still organized as a mono-repo for beta release speed.

## Compatibility

- Java 21
- Spring Boot 3.5.3
- Kotlin 1.9.25
- PostgreSQL
- RabbitMQ
- Node.js 18+ for web applications
- Node.js 20+ for the documentation site

## Testing Coverage

The beta includes backend unit, controller, repository, worker, and integration tests covering core runtime behavior, security, process execution, AI task handling, code-task execution, documents, forms, messaging, and gateway logic.

Frontend test coverage is present for code-task execution audit views and hooks in the admin console.

## Known Limitations

- The beta is focused on core BPM execution and operational validation.
- UI flows may still evolve as real beta feedback arrives.
- Process compatibility across future beta releases is not guaranteed.
- Multi-environment release management is intentionally lightweight in this version.
- Public deployments should use an external HTTPS reverse proxy.

## Upgrade Notes

There is no previous EasyBPM release to upgrade from. For this first beta:

1. Build or pull all runtime images with the same release tag.
2. Configure PostgreSQL, RabbitMQ, JWT secrets, admin bootstrap values, and frontend API URLs.
3. Apply Flyway migrations on backend startup.
4. Start backend, worker, modeler, admin, and task portal services.
5. Validate health endpoints, RabbitMQ connectivity, login, process deployment, task completion, and worker execution.

## Thanks

This first beta marks the initial usable foundation for EasyBPM. The next phase should focus on beta feedback, hardening upgrade paths, expanding documentation, and smoothing the full model-deploy-run-administer loop.
