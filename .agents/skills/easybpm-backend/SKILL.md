---
name: easybpm-backend
description: Build, change, or review the EasyBPM Kotlin/Spring Boot backend or RabbitMQ worker, including BPM runtime, REST APIs, persistence, Flyway migrations, security, incidents, variables, messages, timers, and asynchronous execution. Do not use for frontend-only changes.
---

# EasyBPM Backend

Work primarily in `src/main/kotlin/com/easy/bpm`, `src/main/resources`, `src/test`, and `worker/`. The stack is Kotlin, Java 21, Spring Boot, PostgreSQL, Flyway, RabbitMQ, JPA, and Gradle.

## Preserve these contracts

- PostgreSQL is the runtime source of truth. Keep state transitions transactional, lock process instances before competing resume paths advance them, and make externally retried operations idempotent where the existing contract requires it.
- RabbitMQ transports asynchronous requests and results; workers execute external work, while the backend owns process state and graph advancement. Account for redelivery, duplicate completion, retry, timeout, and DLQ paths.
- Preserve BPMN XML compatibility and keep parser/codec, runtime graph, handlers, DTOs, and modeler expectations aligned.
- Treat process and task variables as typed JSON-compatible values. Preserve null, nested objects, arrays, numeric/boolean types, mapping direction, and completed-instance history behavior.
- Add a new monotonically versioned Flyway migration for schema changes. Never edit an applied migration; include indexes and constraints needed by runtime access patterns and consider existing rows.
- Enforce authentication, authorization, and ownership in controllers/services. Do not rely on frontend checks or leak secrets and internal exception details.

## Design and tests

Prefer focused services with explicit transaction boundaries over growing orchestration classes. Match error semantics to HTTP status and existing API conventions. For concurrency-sensitive work, test observable state and idempotency rather than only method calls.

Add tests at the lowest reliable layer and include controller or integration coverage when contracts, persistence, security, transactions, or messaging change. Reuse repository fixtures and Testcontainers support when PostgreSQL behavior matters.

## Validation

On Windows run `./gradlew.bat test`; elsewhere run `./gradlew test`. For worker-only changes, also run the relevant `:worker` compile or test task. Inspect Flyway ordering and run PostgreSQL-backed tests for SQL, JSONB, locking, or migration behavior when available.
