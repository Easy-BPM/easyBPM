# EasyBPM Production Layout

This repository can stay as the application mono-repo for beta. Split infrastructure into a second repo only when multiple environments, teams, or customers need independent release control.

## Recommended Root Shape

| Path | Ownership |
| --- | --- |
| `src/`, `worker/` | Spring Boot backend and worker source |
| `easy-bpm-modeler/`, `easy-bpm-admin/`, `easy-bpm-task-portal/` | Web applications |
| `Dockerfile`, `Dockerfile.worker` | Backend and worker image builds |
| `deploy/docker/` | Beta compose and shared container assets |
| `deploy/helm/easybpm/` | Kubernetes/SaaS deployment chart |
| `.github/workflows/` | CI and image release automation |

## Release Automation

Use the `Release` GitHub Action to cut a version from `master` or another release branch.

The workflow accepts:

- `version`: release version, with or without `v`.
- `previous_tag`: optional tag used to generate the commit range for release notes.
- `target_ref`: branch/ref to release from, usually `master`.
- `update_version_files`: updates Gradle, frontend package, and Helm version files before tagging.
- `run_validation`: runs backend tests and frontend builds before tagging.
- `draft` and `prerelease`: control the GitHub Release status.

The action creates an annotated `v*` tag, generates release notes from the commits between the previous tag and the new tag, pushes the version commit when enabled, creates the GitHub Release, and invokes the `Release Images` workflow to publish the backend, worker, admin, modeler, and task portal images using the same tag. Manual `v*` tag pushes continue to publish images through the same image workflow.

## Release Model

Build and publish one immutable image per runtime:

- `easybpm-backend`
- `easybpm-worker`
- `easybpm-admin`
- `easybpm-modeler`
- `easybpm-task-portal`

Use the same tag for all images in one release, usually a semver tag such as `v0.1.1-beta.1` or a commit SHA for preview builds.

## Beta Path

1. Run the `Release` workflow or create a tag like `v0.1.1-beta.1`.
2. GitHub Actions publishes Docker images to GitHub Container Registry.
3. Copy `deploy/docker/.env.beta.example` to `.env.beta` and change all secrets.
4. Start the public beta stack:

```bash
docker compose --env-file .env.beta -f deploy/docker/docker-compose.beta.yml up -d
```

For a public beta, put a TLS reverse proxy in front of the web apps and API, such as Caddy, Traefik, Cloudflare Tunnel, Fly.io, Render, or a small VM with HTTPS.

## SaaS Path

When moving to Kubernetes:

1. Use managed PostgreSQL and managed RabbitMQ if possible.
2. Store app secrets in the platform secret manager.
3. Deploy with `deploy/helm/easybpm`.
4. Add per-environment values files in a separate private infra repo when needed.

The app repo should own the chart shape. The infra repo should own environment values.
