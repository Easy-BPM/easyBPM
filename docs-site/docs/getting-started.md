# Getting Started

## Prerequisites
- Java 21
- Docker & Docker Compose

## Quick Start
1. Start infrastructure (Postgres + RabbitMQ):
   ```sh
   docker-compose up -d
   ```
2. Run the monolith:
   ```sh
   ./gradlew bootRun
   ```
3. Run the worker (in a separate terminal):
   ```sh
   cd worker
   ./gradlew bootRun
   ```
4. Access RabbitMQ UI at http://localhost:15672 (user: `easybpm` / pass: `easybpm`)

See the main README for more details.
