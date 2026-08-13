# Fly.io Deployment

This Fly setup builds one image that contains:

- Spring Boot API
- BPM worker jar
- Admin frontend at `/ui/admin/`
- Task portal frontend at `/ui/portal/`
- Modeler frontend at `/ui/modeler/`

Backend API routes stay at the root, for example `/auth`, `/processes`, `/tasks`, `/forms`, `/api/documents`, `/admin`, and `/actuator`.

## Deploy

Edit `fly.toml` and change `app = "easybpm"` to your Fly app name, or run:

```bash
fly launch --copy-config --name your-easybpm-app
```

Set production secrets before deploying:

```bash
fly secrets set \
  EASY_BPM_SERVER_DATASOURCE_URL="jdbc:postgresql://your-postgres-host:5432/easybpm" \
  EASY_BPM_SERVER_DATASOURCE_USERNAME="your-db-user" \
  EASY_BPM_SERVER_DATASOURCE_PASSWORD="your-db-password" \
  EASY_BPM_SERVER_RABBITMQ_HOST="your-rabbit-host" \
  EASY_BPM_SERVER_RABBITMQ_PORT="5672" \
  EASY_BPM_SERVER_RABBITMQ_USERNAME="your-rabbit-user" \
  EASY_BPM_SERVER_RABBITMQ_PASSWORD="your-rabbit-password" \
  EASY_BPM_SERVER_SECURITY_JWT_SECRET="replace-with-a-strong-base64-secret" \
  EASY_BPM_SERVER_SECURITY_BOOTSTRAP_ADMIN_PASSWORD="replace-me"
```

Then deploy:

```bash
fly deploy
```

The `web` process serves nginx plus the API. The `worker` process uses the same image and runs `/app/worker.jar`; scale it when you are ready to process queued work:

```bash
fly scale count web=1 worker=1
```
