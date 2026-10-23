# Epic 55 — Visual Business Rules with DMN

Source: https://github.com/Easy-BPM/easyBPM/issues/55. Status: READY FOR IMPLEMENTATION. Parent delivery owner approved the contracts and explicit defaults below on 2026-10-20. This is the complete initial scope, including saved tests, version differences, audit explanation, metrics, and documentation.

## Context and journey

Business authors create a typed decision table visually, validate it, execute sample inputs, save regression cases, and deploy an immutable version. They add a Business Rule Task to a BPMN process and map variables. Operators inspect decision executions, matched rules, errors, and version differences from Admin. Desktop authoring and XML import/export remain usable without a backend; deployment and persisted tests require a backend.

Existing contracts: BPMN XML is authoritative; PostgreSQL owns runtime state; RabbitMQ workers do not advance decisions; existing process locking, transactions, incidents, timeline, permission authorities, authenticated services, and typed variables remain authoritative. No existing DMN implementation was found. Frontend files reside at application root, not a src directory.

## Scope and non-goals

Backend/API, Modeler, Admin, append-only database migration, automated unit/integration/XML/UI coverage, and an example decision integrated with BPMN are in scope. Task Portal and worker behavior need no change. Full DMN coverage, DRD editing, aggregates or additional hit policies, AI rule discovery, general FEEL, arbitrary scripts, and external function calls are out of scope. Unsupported DMN semantics must fail explicitly rather than silently disappear.

## Functional requirements and approved rules

FR-1. A table has a nonblank stable key/name, typed input and output columns, ordered rows with stable identifiers, cell expressions, and UNIQUE, FIRST, or COLLECT hit policy. Types are string, number, boolean, date, and JSON. Identifiers and output names are unique; rows contain exactly one cell per applicable column.

FR-2. UNIQUE returns one output object when exactly one row matches, and fails when multiple rows match. FIRST returns the first matching row by document order. COLLECT returns every matching output object in document order without aggregation. No-match results are null for UNIQUE/FIRST and an empty array for COLLECT. A named runtime output mapping from null fails rather than silently retaining stale variables.

FR-3. Missing input differs from explicit null: required missing input fails. Null is supported only for JSON, and wildcard can match JSON null. There is no string-to-number/boolean/date coercion. Compare numbers numerically without floating precision loss; dates are validated ISO calendar dates yyyy-MM-dd, independent of timezone. JSON objects/arrays retain structure and use structural equality.

FR-4. The initial secure expression language is deliberately constrained: input expressions are identifier/dotted object paths. Unary tests support wildcard '-', typed literal equality, numeric/date comparisons (<, <=, >, >=), comma disjunction, intervals using bracket/parenthesis endpoints, and not(...). String, boolean, and JSON support equality, not ordering. Outputs support typed literals and explicit input path references; an allowlisted date("yyyy-MM-dd") constructor may represent date literals. No arbitrary method calls, class access, scripts, general arithmetic, implicit evaluation, environment access, network/filesystem access, or clock/random functions. Empty function allowlists are acceptable where literals suffice. Unsupported expressions fail validation. Implementation may conservatively narrow syntax before all applications use it, but must document the exact grammar and test every supported form.

FR-5. Enforce explicit resource limits before evaluation: XML <=1,000,000 UTF-8 bytes, <=32 input/output columns each, <=1000 rows, <=4096 characters/cell, nesting <=16, and bounded total evaluation work. Reject DTD/entities and external XML resources. Validate incomplete rows, duplicate identifiers, invalid types/expressions, unsupported policies/elements, and UNIQUE overlaps. Proven FIRST/COLLECT overlap produces a warning; proven UNIQUE overlap is an error. If an accepted language construct prevents overlap analysis, report that limitation explicitly and always retain runtime UNIQUE checking.

FR-6. Deployment stores key, name, monotonically increasing version, PUBLISHED state, XML, and timestamps. Published versions are immutable: deploying an edited or identical table always creates the next version; concurrent deployments cannot share a key/version. Local editor content is the draft, so a server draft lifecycle is not required. History and exact version XML remain retrievable.

FR-7. Business Rule Task supports decision key and optional positive exact version. Omitted version selects latest published at first execution of that instance/task, persists the choice, and reuses it across retries and later re-entry for that instance/task. Explicit versions remain exact. Deployment of a new decision never changes a previously pinned task. A not-yet-reached unversioned task can resolve the newer deployment when first reached.

FR-8. Inputs map explicit process variable paths or typed static values to decision inputs; outputs map named decision outputs into process variables. Existing task mapping conventions are retained. COLLECT named output mapping yields an ordered array of that output across matched rows. Validate all inputs, evaluation, and output mappings before applying any output variables; failures never leave a partially updated set. Successful execution advances the BPMN graph once under existing instance locking. Missing decision/version, mapping/type failures, and evaluation failures create DECISION_TASK incidents with actionable sanitized diagnostics. Admin corrects inputs through the existing process variable editor and invokes POST /incidents/{id}/retry. Retry locks the instance, rechecks FAILED/errorNode and OPEN/ACKNOWLEDGED incident state, and reruns the same task with its durable version pin. Failed retries retain an OPEN incident, increment occurrence count, and record a failed execution; successful task retry resolves the incident with RETRIED_SUCCESSFULLY. Stale/conflicting concurrent retries return 409 without duplicate progression. A new decision version does not repair an invalid pinned rule; a changed definition/configuration requires an explicit new process instance/deployment.

FR-9. Playground executes draft XML before deployment and published versions with supplied JSON inputs; it reports matched rule IDs, per-condition match explanations, outputs, and duration. Saved test cases persist name, typed inputs, and expected result by decision key; running cases against an optional version returns pass/fail with structured expected/actual differences. Test failures are visible and do not mutate process state. Saved sample data is explicit user content accessible only to author roles.

FR-10. Version comparison identifies changes to metadata, policy, columns, and ordered rule/cell content. Admin provides paginated/filterable execution history and details including decision identity/version, instance/node linkage when relevant, status, matched rule IDs, duration, sanitized error, and condition match explanation.

## Shared contracts

Names below are frozen for cross-application coordination; incompatible changes require coordinated backend/frontend updates and tests.

DecisionTable = {key,name,hitPolicy,inputs:[{id,label,expression,type}],outputs:[{id,name,type}],rules:[{id,description?,inputEntries:string[],outputEntries:string[]}]}. Array order is semantic. DecisionDefinition includes id,key,name,version,state,dmnXml and creation timestamp; include parsed table where useful. Types use lowercase string/number/boolean/date/json in JSON.

DMN XML uses the 20191111/MODEL namespace, definitions containing one decision and decisionTable, input/inputExpression, output, rule/inputEntry/outputEntry/text, and standard hitPolicy/typeRef attributes. Map JSON type to Any or a documented EasyBPM extension while preserving its supported semantics. Import -> edit -> export -> backend evaluation preserves all supported semantics, stable IDs, ordering, type information, and decision key. Reject unsupported semantic constructs with clear feedback.

BPMN uses native bpmn:businessRuleTask and the existing https://easybpm.local/bpmn/extensions easy:node extension. Frontend node type is business-rule-task; internal backend node type is BusinessRuleTask. Config: {decisionKey,decisionVersion?,inputs:[{targetName,source:'variable'|'static',value,type}],outputs:[{sourceName,target:'variable',value,type}]}. Variable mappings use a variable/path name in value. Static mappings carry actual typed JSON values: numbers, booleans, objects/arrays/null, or plain strings/dates; they do not carry a string containing a JSON literal. Missing optional version means latest-at-first-execution, never an implicit version 0. A named COLLECT output maps to an ordered array with each element checked against the mapping type; reserved sourceName=$result can map the entire result with type=json, while ordinary output name result remains a column name.

EvaluationResult = {decisionKey,version?,hitPolicy,matchedRuleIds,outputs:Object|null|Object[],explanations:[{ruleId,matched,conditions:[{inputId,expression,matched}]}],durationMs}. Explanation predicates describe rule conditions and their boolean result; they do not echo supplied values.

Author APIs (ACCESS_BPM_MODELER or ACCESS_BPM_ADMIN):

- POST /decisions/validate {dmnXml} -> {valid,issues:[{severity,code,message,ruleId?,columnId?}],table?}.
- POST /decisions/test {dmnXml,inputs} -> EvaluationResult; no deployment or persisted audit side effect required for draft tests.
- POST /decisions {dmnXml} -> DecisionDefinition.
- GET /decisions -> latest definitions.
- GET /decisions/{key}/versions -> all immutable versions.
- GET /decisions/{key}/versions/{version} -> exact definition.
- POST /decisions/{key}/evaluate {version?,inputs} -> EvaluationResult.
- GET /decisions/{key}/tests and POST /decisions/{key}/tests {name,inputs,expectedOutputs} -> saved cases.
- POST /decisions/{key}/tests/run {version?} -> {decisionKey,version,results:[{testCaseId,name,passed,expectedOutputs,actualOutputs,error?,differences}]}.
- GET /decisions/{key}/diff?fromVersion=&toVersion= -> {decisionKey,fromVersion,toVersion,changes:[{path,before,after}]}.

Admin APIs (ACCESS_BPM_ADMIN): GET /decision-executions with key,version,processInstanceId,nodeId,status,page,size filters returns paginated executions; GET /decision-executions/{id} returns one detail. Use consistent DTO date and pagination conventions with existing Admin services.

## Permissions, visibility, and errors

Explicit SecurityConfig matchers protect every decision path, including native bearer/API clients: author roles may author/test/deploy/query/diff, Admin alone reads executions. A portal-only user or unrelated authenticated API client gets 403; anonymous gets 401. Internal engine execution does not require the starting end user's authoring authority. The repository has no decision tenant partition, so retain existing application visibility rather than inventing tenants. Centralize frontend authentication/backend URL handling in existing services.

Validation returns actionable diagnostics without parser stack traces or supplied values. Invalid request/XML/type/expression/limit/mapping gives 400; inaccessible authentication gives 401/403; missing keys/versions/executions gives 404; concurrency conflict gives 409 if not resolved internally; dependency failures give sanitized 5xx. Evaluation failures carry stable error code and safe diagnostic context. Runtime failures persist an incident and audit that survive the failure handling transaction; errors must not accidentally roll back those records. Errors never expose credentials, tokens, raw input/output values, or stack traces to unauthorized callers.

## Persistence and non-functional requirements

NFR-1. Append the next migration after current V47; never edit applied migrations. Persist definitions with unique key/version, durable instance/task version bindings, saved cases, and executions; index actual key/version/instance/time/filter access paths. Preserve current data and append-only published history.

NFR-2. Determinism covers row ordering, explicit no-match behavior, typed JSON, numeric/date comparison, and pinned versions. Evaluation has bounded CPU/memory. Runtime output updates, audit success, and graph progress participate in existing process transaction/locking behavior; no asynchronous worker contract is changed.

NFR-3. Persist audit input/output structure as redacted typed summaries by default, not raw values. Persist version, rule IDs, safe predicate explanation, duration, outcome, and error code/context. Do not log supplied payloads in service logs or exception messages. Document that explicit saved test inputs and literal rule definitions are author-controlled content. Metrics publish volume, duration, failures, and matched rules through Micrometer; never use input/output values or process-instance IDs as metric tags.

NFR-4. UI includes loading, empty, invalid JSON/XML, permission denied, backend unavailable, and not-found states. Input/output/rule editing and results are keyboard usable, with text status as well as color. Desktop local file interactions use the existing bridge and never database access.

## Acceptance criteria and traceable test matrix

AC-1. Given an empty editor, when an author adds typed columns and rows and changes hit policy, then a decision can be created and edited without writing XML. Evidence: UI interaction test for table add/edit/delete, policy changes, validation feedback.

AC-2. Given supported types and expressions, when validated/evaluated, then literal/path values preserve types and invalid/missing/coerced values are rejected. Evidence: unit cases for all five types, dates/leap dates, nested JSON, null/missing, numeric precision, malformed output and expressions, every grammar form and resource limit.

AC-3. Given overlapping, unique, and no-match rows, when evaluated under each policy, then policy and no-match behavior follows FR-2 deterministically. Evidence: policy tests, ordered COLLECT, duplicate UNIQUE, FIRST order and overlap diagnostics.

AC-4. Given draft XML, when validated and playground-tested before deployment, then actionable diagnostics/explanations/results appear and no definition/process is mutated. Given saved cases, when run against a selected version, then expected/actual differences and pass/fail results persist or display. Evidence: API and UI tests, saved case persistence tests.

AC-5. Given existing and concurrent deployments, when a version is published, then next versions are unique/immutable, previous XML is unchanged, history and structured differences are available. Evidence: PostgreSQL migration/repository/concurrency integration tests and version comparison tests/UI.

AC-6. Given a BPMN Business Rule Task, when it executes first and after a newer deployment/retry, then exact versions or durable latest-at-first-entry bindings resolve correctly. Evidence: BPMN XML fixture and integration tests covering first execution, newer deployment, retry, re-entry, and instance isolation.

AC-7. Given variable/static input and named output mappings, when decision execution succeeds, then typed values (including COLLECT arrays) are applied and the next node executes once; when any mapping fails, no partial outputs are committed. Evidence: runtime integration tests for mapping directions/types, absent variable, invalid output name, failure atomicity and duplicate/concurrent resume.

AC-8. Given missing definition/version or evaluation/mapping failure, when a process enters the task, then a DECISION_TASK incident records safe key/version/node/error context and permits diagnosis/retry. Given corrected input variables, when Admin retries through POST /incidents/{id}/retry, then the same pinned task executes and resolves the incident RETRIED_SUCCESSFULLY after success; another failure leaves it OPEN with incremented occurrence count and failed audit. Stale/concurrent conflicting retries return 409 without duplicate graph progression. Evidence: actual incident endpoint and failure/retry/concurrency integration tests verifying incident/audit persistence, pin retention, successful resolution and stale conflict.

AC-9. Given decision execution, when an authorized operator inspects Admin history/details, then identity, matched rules and explanations, duration, errors, and redacted typed payload summaries are shown with filters/pagination. Raw input/output secrets do not appear in audit/log/error text. Evidence: API authorization, redaction canaries, Admin UI loading/empty/filter/details/error tests.

AC-10. Given supported DMN XML and BPMN decision mappings, when imported/exported/reimported, then supported semantic content and identifiers remain compatible with backend evaluation; unsupported semantic constructs are rejected. Evidence: realistic DMN/BPMN fixtures, XML round trips including namespace prefixes/escaped text, external entity rejection, and existing definition regression checks.

AC-11. Given anonymous, portal-only, native API client, modeler, and admin credentials, when each endpoint family is called, then only specified authorities succeed. Evidence: actual security-filter integration/controller tests, not only mocked service tests.

AC-12. Given success/failure/matching/no-match evaluations, when metrics are sampled, then volume, duration, failure, and rule counters reflect outcomes without payload tags. Evidence: meter-registry tests.

AC-13. Given the documented example, when an author imports the decision and BPMN process, deploys, supplies sample inputs, and runs it, then mapped outputs follow the documented policy. Documentation covers grammar/limits, no-match/COLLECT, version pinning, permissions, redaction, saved tests, and unsupported DMN scope. Evidence: runnable examples, docs check, automated example evaluation/XML test.

AC-14. Before handoff, backend/worker ./gradlew.bat test, Modeler npm run lint and npm run build (plus build:desktop for desktop changes), Admin npm run build and relevant tests all pass or unavailable checks are explicitly reported. PostgreSQL-backed tests are implemented even if Docker availability prevents running them locally. No environment-limited test counts as passed.

## Handoff

Next role: easybpm_developer, then independent easybpm_qa against AC-1 through AC-14. No unresolved product blocker remains after delivery-owner approval of the explicit defaults. Docker is available for PostgreSQL-backed validation. Non-blocking risks: the limited DMN language must be described clearly to avoid implying general FEEL conformance; prefix/namespace variations and date/JSON literal interchange require compatibility fixtures. Preserve all pre-existing user changes (including unrelated root files, local backups and legacy modeler directory).
