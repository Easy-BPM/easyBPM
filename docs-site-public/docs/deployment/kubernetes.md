---
title: Kubernetes Deployment
---

# Kubernetes Deployment

Easy BPM includes a Helm chart at `deploy/helm/easybpm` for Kubernetes deployments.

## Install with Helm

```bash
helm upgrade --install easybpm deploy/helm/easybpm \
  --namespace easybpm \
  --create-namespace \
  --set license.key=<valid-license-key> \
  -f values.customer.yaml
```

## License

Kubernetes deployments require a valid Easy BPM license. When Helm creates the application Secret, set `license.key` with the license value provided for your subscription:

```bash
helm upgrade --install easybpm deploy/helm/easybpm \
  --namespace easybpm \
  --create-namespace \
  --set license.key=<valid-license-key> \
  -f values.customer.yaml
```

If you manage Secrets outside the chart, create a Kubernetes Secret that contains `EASYBPM_LICENSE_KEY`, then set `existingSecretName`:

```bash
kubectl create secret generic easybpm-secrets \
  --from-literal=EASYBPM_LICENSE_KEY=<valid-license-key>

helm upgrade --install easybpm deploy/helm/easybpm \
  --namespace easybpm \
  --create-namespace \
  --set existingSecretName=easybpm-secrets \
  -f values.customer.yaml
```

The backend and worker pods read the license from `EASYBPM_LICENSE_KEY`.

## Recommended architecture

Use managed services where available:

| Dependency | Recommendation |
| --- | --- |
| PostgreSQL | Managed database with backups and point-in-time recovery. |
| RabbitMQ | Managed broker or a production-grade RabbitMQ operator. |
| Secrets | Cloud secret manager or sealed/encrypted Kubernetes secrets. |
| Ingress | HTTPS ingress with host-based routing for API and web apps. |
| Metrics | Prometheus scrape for backend actuator endpoints. |

## Images

Deploy one image per runtime:

| Runtime | Image |
| --- | --- |
| Backend | `easybpm-backend` |
| Worker | `easybpm-worker` |
| Modeler | `easybpm-modeler` |
| Admin | `easybpm-admin` |
| Task Portal | `easybpm-task-portal` |

Use the same immutable tag across all Easy BPM images in one release.
