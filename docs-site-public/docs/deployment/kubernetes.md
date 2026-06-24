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
  -f values.customer.yaml
```

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

## Agentic orchestration values

Enable the Agent Board and Agent Process BPM node in the Modeler with Helm values:

```yaml
web:
  modeler:
    apiBaseUrl: "https://api.easybpm.example.com"
    agenticOrchestration:
      enabled: true
```

The chart renders those values into `easybpm-config.js` and mounts it into the Modeler container, so the feature flag can be changed per deployment without rebuilding the frontend image.

To run provider-backed Agent Processes with the example OpenAI configuration, provide the backend token as a Kubernetes secret value:

```yaml
secrets:
  ai:
    encryptionKey: "replace-with-strong-ai-credential-key"
    openaiApiKey: "sk-..."
```

Agent Process definitions that use `"credentialRef": "$OPENAI_API_KEY"` resolve that value from the backend pod environment. For production, prefer a sealed secret, external secret operator, or `existingSecretName`.
