---
title: Capacity Planning
---

# Capacity Planning

Easy BPM scales horizontally for API traffic and asynchronous worker execution. Backend replicas can be increased for REST traffic and message consumption. Worker replicas can be increased for external API and service task throughput.

The main shared resources are PostgreSQL and RabbitMQ. PostgreSQL stores process state, variables, tasks, message subscriptions, received external messages, and audit data. RabbitMQ handles asynchronous service task requests, retries, completions, and workflow events.

## Estimate worker throughput

For API tasks, worker throughput depends mostly on external service latency and worker concurrency:

```text
worker task throughput = worker concurrency / average external API latency
```

Example:

```text
10 concurrent worker consumers / 2 second API latency = about 5 API tasks per second
```

If each process instance executes two API tasks:

```text
5 API tasks per second / 2 API tasks per process = about 2.5 process instances per second
```

These estimates should be validated with a benchmark that matches your workflow shape, external API latency, retry rate, and deployment size.

## Estimate RabbitMQ volume

RabbitMQ message volume depends on the workflow model:

```text
rabbit messages per process =
  service task requests
+ service task completions
+ retries
+ DLQ events
+ message events
+ task events
```

Then:

```text
rabbit messages per second = process instances per second * rabbit messages per process
```

## Estimate database writes

PostgreSQL write volume depends on how much state each process creates:

```text
database writes per process =
  process instance insert
+ process state updates
+ variables created or updated
+ task rows
+ task variable rows
+ message subscriptions
+ external message inbox records
+ worker request rows
+ audit rows
```

Then:

```text
database writes per second = process instances per second * database writes per process
```

## Benchmark worker throughput

The repository includes a benchmark script that deploys a synthetic process:

```text
Start -> API Task -> End
```

The API task calls a local mock HTTP endpoint with configurable latency. The script starts process instances, waits for completion, and reports throughput and latency.

Run it from the repository root:

```bash
node scripts/benchmark-worker-throughput.mjs \
  --backend-url http://localhost:8080 \
  --instances 100 \
  --concurrency 20 \
  --mock-delay-ms 250 \
  --external-url http://host.docker.internal:19090/mock-api
```

Use `host.docker.internal` when the worker runs in Docker and needs to call the mock API running on the host machine. Use `http://localhost:19090/mock-api` when the worker runs directly on the same machine as the script.

The output includes:

- completed process instances
- failed or unfinished process instances
- API task throughput
- process completion latency percentiles
- mock API request count

Use the result as an environment-specific benchmark, not as a universal product limit. Real capacity depends on workflow design, database sizing, broker sizing, worker concurrency, external API latency, and retry behavior.

## Scaling notes

A single process instance is advanced with state locking, so horizontal scaling improves throughput across many process instances rather than parallelizing one individual process instance.

If the benchmark shows growing latency or unfinished instances, check:

- external API latency
- worker replica count and listener concurrency
- RabbitMQ queue depth
- PostgreSQL write latency
- retry rate
- process variable and audit volume
