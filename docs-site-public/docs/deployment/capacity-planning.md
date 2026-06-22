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

## Benchmark example

Easy BPM provides a worker throughput benchmark that runs a synthetic process:

```text
Start -> API Task -> End
```

The API task calls a mock external service with configurable latency. The benchmark starts process instances, waits for completion, and reports throughput and latency.

Example execution:

```bash
node scripts/benchmark-worker-throughput.mjs \
  --backend-url http://localhost:8080 \
  --instances 100 \
  --concurrency 20 \
  --mock-delay-ms 250 \
  --external-url http://host.docker.internal:19090/mock-api
```

Example local Docker results:

| Setup | Mock API latency | Instances | Completed | Throughput |
| --- | ---: | ---: | ---: | ---: |
| 1 worker | 250 ms | 50 | 50 | ~3.57 API tasks/sec |
| 1 worker | 1000 ms | 30 | 30 | ~0.97 API tasks/sec |
| 3 workers | 1000 ms | 60 | 60 | ~2.86 API tasks/sec |
| 3 workers | 250 ms | 100 | 100 | ~10.72 API tasks/sec |

![Example worker throughput benchmark results](/img/benchmarks/worker-throughput-example.svg)

These values are sample results from a local Docker environment. They are useful for understanding the relationship between worker count, external API latency, and throughput, but they are not product limits or production sizing guarantees.

## Scaling notes

A single process instance is advanced with state locking, so horizontal scaling improves throughput across many process instances rather than parallelizing one individual process instance.

If the benchmark shows growing latency or unfinished instances, check:

- external API latency
- worker replica count and listener concurrency
- RabbitMQ queue depth
- PostgreSQL write latency
- retry rate
- process variable and audit volume
