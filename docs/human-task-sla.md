# Human-task SLA (epic 54)

Source: https://github.com/Easy-BPM/easyBPM/issues/54. Approved scope and acceptance criteria: [epic-54-spec.md](epic-54-spec.md).

## Authoring and elapsed time

Configure SLA only on a User Task in the Modeler. The persisted BPMN contract remains `easy:node` JSON in the `https://easybpm.local/bpmn/extensions` namespace. For example:

```xml
<bpmn:userTask id="review" name="Review">
  <bpmn:extensionElements>
    <easy:node><![CDATA[{
      "id":"review","name":"Review","type":"HumanTask",
      "config":{"sla":{
        "duration":"PT4H","timeZone":"America/Sao_Paulo",
        "alerts":[{"id":"warn-30m","beforeDue":"PT30M"}],
        "escalations":[
          {"id":"manager","afterDue":"PT0S","action":"REASSIGN","target":"${supervisor}"},
          {"id":"operations","afterDue":"PT1H","action":"ADD_CANDIDATE_GROUP","target":"operations"}
        ]
      }}
    }]]></easy:node>
  </bpmn:extensionElements>
</bpmn:userTask>
```

Declare the usual BPMN and `easy` namespaces on `bpmn:definitions`. Replace `duration` with `${slaDuration}` to resolve a process variable once at task creation. The variable must contain a nonblank duration string such as `PT4H`; numbers, objects, missing and null values fail through the process-engine incident path. The invalid task is removed, rather than left pending without a deadline. Dynamic escalation targets resolve at execution/retry, so correcting `supervisor` can recover a failure.

Durations are continuous elapsed time. `P1D` means 24 hours, including daylight-saving changes. Months/years, negative/zero deadlines, zero alert offsets, alerts at/after the deadline, malformed expressions and unsupported action types are rejected. Escalation `PT0S` is valid. Durations support days/hours/minutes/seconds, up to nine fractional-second digits and 9,223,372,036 seconds. Instants must fit year 9999. Each list has at most 100 actions and the policy is bounded to 32768 serialized JSON characters. IDs are unique across both lists, nonblank, at most 100 characters, and cannot start with the reserved `__sla_` prefix.

All new deadline, schedule, event and completion instants persist as PostgreSQL `timestamptz` and serialize with offsets (UTC `Z`). Existing `createdAt`/`completedAt` remain the legacy local timestamps and are not reinterpreted during migration. SLA configuration captures its creation instant explicitly; it never converts legacy rows into SLA tasks. The snapshotted IANA timezone controls display, while arithmetic always uses instants.

## Operation and recovery

`easybpm.sla.time-zone` defaults to `UTC`. A task may override it with an IANA timezone. `easybpm.sla.poll-ms` defaults to `10000`; configure an interval no greater than 30000 milliseconds to meet the SLA refresh contract. Scheduler batches are bounded to 100 task IDs, ordered by the oldest eligible schedule. FAILED instances defer processing without moving deadlines. PENDING tasks in ACTIVE/WAITING instances are eligible; completed/cancelled/inactive work is skipped.

Status is authoritative at the captured query instant: ON_TRACK, then AT_RISK at the earliest alert offset, then OVERDUE at the deadline. Without alerts it moves directly to OVERDUE. SLA-enabled completed tasks report COMPLETED and preserve `completedWithinSla`; completion at the deadline is within SLA. No-SLA tasks return null SLA fields. Status facts are durably represented by configured/risk/deadline events and schedule outcomes; query status is derived from persisted instants and task lifecycle so scheduler lag cannot make an overdue task appear on track.

Claim, unclaim, draft, reassignment and candidate changes preserve deadline and policy. Reassignment preserves candidates; candidate additions are set union. User targets are existing enabled local/OIDC usernames; group targets are existing group codes (groups have no separate enabled flag in the current identity model). Existing visibility remains authoritative, including the intersection of populated candidate-user and candidate-group eligibility.

The database action ledger and a task-row lock ensure one successful action per task/action ID across replicas and restarts. Task and process locks serialize escalation with completion. Missed escalations execute in scheduled order, with configured order breaking equal-time ties. A missed alert is skipped after the deadline, so restart never emits a late advance warning. Effects, outcome, logical publication, incident/audit and timeline evidence commit together; logs and metric increments occur after commit.

An ALERT row in `task_sla_event` is the durable internal publication. It includes a stable UUID, action/task/process IDs, deadline and scheduled/executed timestamps. This release does not deliver email/SMS or publish a new worker protocol. A future transport consumer can poll the ledger or task history API and must deduplicate by event UUID, since transport delivery can repeat. No variable payload is copied into events or logs.

Unknown/inactive/unresolvable targets fail without partial assignment mutation and create an SLA incident linked to the original action. An admin can correct the process variable and use the existing incident retry. A retry resolves the incident only after success; another failed attempt preserves the incident and records new evidence. Completed/inactive work, an already-successful action, or a reassignment whose task assignment has changed since failure returns 409. Failed independent candidate additions remain retryable. Repeated successful retry cannot duplicate the effect or overwrite a later assignment.

## APIs and dashboards

Task DTOs add nullable `dueDate`, `slaStatus`, `slaTimeZone`, and `completedWithinSla`; existing task `status` stays PENDING/COMPLETED. POST `/tasks/search` accepts `dueDate` comparisons with offset-qualified timestamps and `slaStatus` EQUALS/NOT_EQUALS/IN/NOT_IN. Filters combine with AND; inclusive ranges reject reversed bounds. Deadline sorting places null deadlines last in either direction and breaks ties by task ID.

- POST `/tasks/sla/summary`: existing `{filters:[...]}` request; returns onTrack/atRisk/overdue/completed/withoutSla/asOf/timeZone under task visibility. Counts share a repeatable database snapshot and captured clock instant.
- GET `/tasks/{id}/sla/events?page=0&size=20`: paginated durable history under the same visibility; no target details or variables are returned.
- GET `/admin/sla/metrics?groupBy=PROCESS|TASK|USER|GROUP`: admin-only paginated dimensions, optional inclusive `dueDateFrom`/`dueDateTo`. Pages are bounded to 100 rows.
- POST `/incidents/{id}/retry`: admin-only existing shape; authenticated actor takes precedence over supplied `requestedBy`.

PROCESS groups by definition key; TASK by definition key plus node ID; USER uses the current assignee and an UNASSIGNED bucket; GROUP uses current candidate membership plus NO_GROUP. A task in multiple groups contributes to multiple rows, so GROUP rows are not additive. The within-SLA rate is a nullable fraction from 0 to 1, excluding no-SLA tasks. Alert-to-completion seconds use the first successful alert and completed SLA instant; the average is null with no samples. Escalation success counts represent completed logical actions; failure counts include failed retry attempts.

Portal shows accessible text/icon status, countdown, filters/counters and refreshes authoritative metadata every 15 seconds while preserving selected task, edited form, filters and drafts. Admin exposes dimensions, ranges, status filtering, paginated history and the existing incident/variable recovery journey. A failed refresh retains user input and permits retry.

Prometheus exposes `task_sla_overdue`, `task_sla_alerts_total{outcome="success"}`, and `task_sla_escalations_total{outcome="success|failure"}`. Labels contain only bounded outcomes, never task IDs/usernames. Correlated logs carry task/instance/action/incident IDs, event type, scheduled/executed times and outcome.

## Deployment and compatibility

Apply append-only Flyway V47 before running the updated backend. It adds nullable SLA task columns, an assignment revision defaulting to zero, action/event ledgers, uniqueness and deadline/history/scheduler indexes. Existing tasks remain no-SLA and existing local timestamps/XML are preserved. Deleted task rows cascade-delete their SLA schedules/history. Existing retention policy remains responsible for history lifetime. Existing RabbitMQ worker contracts are unchanged.

Deploy backend and frontends together to expose the new endpoints/fields. Keep clocks synchronized across backend replicas. Business-day calendars, manual clock reset, provider notifications and SLA-driven process advancement remain out of scope.
