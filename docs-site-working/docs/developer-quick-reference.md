# Developer Quick Reference

Quick guide for developers working with the Easy BPM Engine.

---

## Running the Application

### Prerequisites
- Java 21
- Docker & Docker Compose

### Start Infrastructure
```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on localhost:5432 (user: `easybpm`, pass: `easybpm`)
- **RabbitMQ** on localhost:5672 (user: `easybpm`, pass: `easybpm`)
- **RabbitMQ UI** on http://localhost:15672

### Start BPM Engine
```bash
./gradlew bootRun
```

**Server**: http://localhost:8085
**OpenAPI**: http://localhost:8085/swagger-ui.html

### Start Worker (Optional)
In a separate terminal:
```bash
cd worker
./gradlew bootRun
```

---

## Common Tasks

### Deploy a Process

```bash
curl -X POST http://localhost:8085/processes \
  -H "Content-Type: application/json" \
  -d '{
    "nodes": [
      {"id": "start", "type": "StartEvent"},
      {"id": "task1", "type": "HumanTask", "name": "Approve"},
      {"id": "end", "type": "EndEvent"}
    ],
    "edges": [
      {"source": "start", "target": "task1"},
      {"source": "task1", "target": "end"}
    ]
  }'
```

### Start Process Instance

```bash
curl -X POST http://localhost:8085/processes/{processId}/start
```

### Deploy Form

```bash
curl -X POST http://localhost:8085/forms \
  -H "Content-Type: application/json" \
  -d '{
    "key": "approvalForm",
    "name": "ApprovalForm",
    "schema": {
      "type": "object",
      "properties": {
        "approved": {"type": "boolean"},
        "comment": {"type": "string"}
      }
    }
  }'
```

Use the form `key` as the stable identifier for versioning and for user-task attachment in process definitions.

### Get Tasks

```bash
curl http://localhost:8085/tasks?page=0&size=20
```

### Complete Task

```bash
curl -X POST http://localhost:8085/tasks/{taskId}/complete \
  -H "Content-Type: application/json" \
  -d '{
    "assignee": "john",
    "variables": {
      "approved": true,
      "comment": "Looks good"
    }
  }'
```

### Send Message to Process

```bash
curl -X POST http://localhost:8085/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "approvalReceived",
    "correlationKey": "order_123",
    "variables": {
      "approved": true
    }
  }'
```

### Check Metrics

```bash
# Health check
curl http://localhost:8085/actuator/health

# Available metrics
curl http://localhost:8085/actuator/metrics

# Prometheus format
curl http://localhost:8085/actuator/prometheus
```

---

## Database Access

### Connect to PostgreSQL
```bash
psql -h localhost -U easybpm -d easybpm
```

### Useful Queries

**View all processes**:
```sql
SELECT * FROM process_definition;
```

**View running instances**:
```sql
SELECT * FROM process_instance WHERE status = 'ACTIVE';
```

**View pending tasks**:
```sql
SELECT * FROM task WHERE status = 'PENDING';
```

**View process variables**:
```sql
SELECT * FROM process_variable WHERE process_instance_id = {instanceId};
```

**View worker requests**:
```sql
SELECT * FROM worker_request ORDER BY created_at DESC;
```

---

## RabbitMQ Administration

### RabbitMQ Management UI
Navigate to: http://localhost:15672
- Username: `easybpm`
- Password: `easybpm`

### Check Message Queues
1. Go to "Queues and Streams" tab
2. View enabled queues:
   - `service-task-requests` - Service task work items
   - `service-task-completions` - Service task results
   - `service-task-dlq` - Failed service tasks
   - `task-created` - Task creation events
   - `task-completed` - Task completion events

### Purge Queue
Click "Purge messages" on any queue (use with caution in production).

---

## Testing

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests ProcessIntegrationTest
```

### Run with Coverage
```bash
./gradlew test --info
```

### View Test Report
After running tests:
```
build/reports/tests/test/index.html
```

---

## Code Organization

```
src/main/kotlin/com/easy/bpm/
├── controller/           # REST endpoints
├── service/             # Business logic
├── model/               # JPA entities
├── repository/          # Data access
├── enum/                # Enumerations
├── messaging/           # RabbitMQ integration
├── config/              # Spring configuration
├── util/                # Utility functions
└── actuator/            # Health indicators

src/test/kotlin/com/easy/bpm/
└── integration/         # Integration tests
```

---

## Key Services

### ProcessService
Main orchestration engine. Start here for understanding process flow.

**Key Methods**:
- `deployProcess()` - Store process definition
- `startProcessInstance()` - Create and execute instance
- `executeNode()` - Route to node handler
- `handleMessageReceived()` - Resume on message

### TaskService
Task lifecycle management.

**Key Methods**:
- `completeTask()` - Mark done, apply mappings
- `getTasks()` - List/search tasks

### GatewayService
Decision routing logic.

**Key Methods**:
- `getNextNodes()` - Find next execution nodes
- `evaluateCondition()` - SpEL/JavaScript evaluation

### MetricsService (Phase 3)
Observability framework.

**Key Methods**:
- `recordProcessStarted()` - Track process creation
- `recordTaskCompleted()` - Track task completion
- `recordNodeExecution()` - Track performance by node type

---

## Debugging

### Logging Configuration

**Production** (default): Suppresses verbose logging, keeps only warnings and errors
```yaml
logging:
  level:
    root: INFO
    com.easy.bpm: INFO
    org.hibernate: WARN  # Suppress SQL queries
```

**Development**: Enhanced logging for troubleshooting
```yaml
logging:
  level:
    root: INFO
    com.easy.bpm: DEBUG
    org.hibernate: WARN  # Still suppress SQL by default
```

**Enable Hibernate Query Logging** (debugging only, not recommended for production):
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**Log Files**:
- Location: `logs/bpm.log`
- Rotation: Daily or when 10MB is exceeded
- History: Last 10 days retained
- Format: Timestamp, thread, level, logger, correlation ID, message

### Check Logs
```bash
# Watch real-time logs (development)
./gradlew bootRun 2>&1 | grep -E "com.easy.bpm|ERROR|WARN"

# Check production logs
tail -f logs/bpm.log

# Filter by error level only
grep "ERROR" logs/bpm.log | tail -20
```

### Debug Common Components

**Enable RabbitMQ Debug Logging**:
```yaml
logging:
  level:
    com.rabbitmq: DEBUG
    org.springframework.amqp: DEBUG
```

**Enable Spring Framework Debug Logging**:
```yaml
logging:
  level:
    org.springframework: DEBUG
    org.springframework.boot: DEBUG
```

### View Logs by Component

---

## Performance Tips

1. **Index Process Variables**
   - Add database index on `(process_instance_id, name)` for `process_variable` table
   - Improves variable lookup speed

2. **Archive Old Instances**
   - Regularly delete completed instances from 6+ months ago
   - Reduces table size and improves query speed

3. **Connection Pooling**
   - HikariCP already configured
   - Default pool size: 10 connections
   - Adjust via `spring.datasource.hikari.maximum-pool-size`

4. **Metrics Overhead**
   - Minimal impact: < 1ms per operation
   - Consider disabling low-value metrics in high-throughput scenarios

5. **Message Throughput**
   - RabbitMQ: Default prefetch count of 1 (adjust for worker efficiency)
   - Increase `spring.rabbitmq.listener.simple.prefetch` for higher throughput

---

## Common Issues

### Port 8080 Already in Use
```bash
./gradlew bootRun --args='--server.port=8081'
```

### Database Connection Error
```bash
# Verify PostgreSQL is running
docker ps | grep postgres

# Restart if needed
docker-compose down
docker-compose up -d
```

### RabbitMQ Connection Error
```bash
# Verify RabbitMQ is running
docker ps | grep rabbitmq

# Check RabbitMQ logs
docker logs <rabbitmq-container-id>
```

### Process Not Completing
1. Check `nodeHistory` in `process_instance`
2. Verify process definition has valid paths to EndEvent
3. Check `process_variable` table for unexpected variable states

### Task Not Created
1. Verify process definition has UserTask node
2. Check Form deployment (form_id must exist)
3. Review logs for input mapping errors

---

## Monitoring Checklist

- [ ] Check `/actuator/health` - all components UP
- [ ] Review `/actuator/metrics` - baseline metrics
- [ ] Monitor database table sizes (growth rate)
- [ ] Check RabbitMQ queue depths
- [ ] Review process execution times (95th percentile)
- [ ] Monitor task completion rate
- [ ] Track DLQ for failed service tasks
- [ ] Verify backup schedule for PostgreSQL

---

## Additional Resources

- **Documentation**: See `docs-site/docs/` folder
- **API Reference**: `/swagger-ui.html` (Swagger/OpenAPI)
- **Metrics Guide**: [metrics-observability.md](metrics-observability.md)
- **Architecture**: [features-architecture.md](features-architecture.md)
- **Examples**: [examples.md](examples.md)
