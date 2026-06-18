---
title: Observability
---

# Observability

Easy BPM exposes Spring Boot Actuator endpoints and Prometheus metrics from the backend runtime.

## Health

```bash
curl http://localhost:8080/actuator/health
```

Use health checks for load balancers, Kubernetes readiness, and external monitors.

## Prometheus

```bash
curl http://localhost:8080/actuator/prometheus
```

The backend includes Micrometer and Prometheus registry support.

## Useful runtime signals

| Signal | Why it matters |
| --- | --- |
| Process start/completion counts | Shows workflow volume and throughput. |
| Node execution duration | Helps identify slow process steps. |
| Task completion duration | Tracks human workflow latency. |
| API/service task duration | Detects slow or failing integrations. |
| RabbitMQ queue depth | Indicates worker backpressure. |
| Database connection health | Confirms persistence availability. |

## Logs

The backend writes structured console logs and `logs/bpm.log` by default. Include correlation IDs where possible when troubleshooting customer workflows across backend, worker, and external APIs.
