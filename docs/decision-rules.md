# Decision tables and Business Rule Tasks

EasyBPM supports a deliberately limited DMN 1.3 decision-table language. Business authors can edit tables in the Modeler, test sample inputs, deploy immutable versions, and invoke them from BPMN Business Rule Tasks. Admin provides execution history, matched-rule explanations, and version differences. The delivery requirements are in [epic-55-spec.md](epic-55-spec.md).

## Create and test a table

New decisions always start with an empty key, name, table, and sample inputs. Choose **Examples** (or **Browse examples**) to explicitly load Eligibility, Expense approval, or Discount collection with matching sample data. Loading an example replaces the current draft only after confirming unsaved changes; **New decision** clears it again.

To open a published decision, use **Deployed resources → Business Rules** on the Modeler home screen. Search by name or key and open the desired resource. The editor loads that decision's XML and version; subsequent publication creates a new immutable version. The editor has no separate **Published decision** resource selector.

Open the Modeler's decision editor. Give the decision a stable key and name, select a hit policy, add input/output columns, and add ordered rule rows. Input expressions name a field in the test input object; dotted expressions such as `customer.age` read nested objects. Output column names become the result object's property names. Save/export DMN XML to keep local work. Desktop authoring/import/export remains available without a server; publishing, backend validation, and saved server test cases require a configured backend.

Input and output types are `string`, `number`, `boolean`, `date`, and `json`. Numbers and booleans must be JSON numbers and booleans, not strings. Dates are valid calendar dates in `yyyy-MM-dd` format, without a time or timezone. JSON values preserve objects, arrays, primitives, and explicit null. Missing inputs fail; explicit null is accepted only by a JSON column. No implicit type conversion occurs.

Cells use a constrained expression language, not general FEEL or JavaScript:

| Cell | Meaning |
| --- | --- |
| `-` | Match any value accepted by the input's type |
| `"gold"`, `42`, `true`, `{"tier":1}` | Typed literal equality |
| `= 42`, `!= 42` | Equality or inequality |
| `< 1000`, `>= 1000` | Numeric comparison |
| `date("2026-10-20")` | Date literal |
| `>= date("2026-01-01")` | Date comparison |
| `[10..20]`, `(10..20]`, `[10..20)`, `(10..20)` | Inclusive/exclusive numeric intervals |
| `"gold", "silver"` | Match either alternative |
| `not("blocked")` | Negate a supported test |

Ordering and intervals are limited to numbers and dates. JSON literals containing commas remain a single literal. String literals use JSON double quotes and escapes. Output cells contain a typed JSON/date literal or an input path such as `customer.name`; they do not contain a unary test or wildcard. Bare `true`, `false`, and `null` are literals, not variable names. An output path must exist and match the output column type at execution.

The only function-like constructor is `date("yyyy-MM-dd")`. Scripts, arithmetic, external function calls, class/method access, network/filesystem access, and clock/random functions are unavailable. Unsupported syntax is rejected. Empty cells are incomplete rules; use `-` explicitly for an unrestricted input condition.

| Hit policy | Matching result | No matching rule |
| --- | --- | --- |
| UNIQUE | Exactly one output object; multiple matches fail | `null` |
| FIRST | First matching row's output object, in table order | `null` |
| COLLECT | Array of all matching output objects, in table order | `[]` |

COLLECT does not aggregate or deduplicate. FIRST and COLLECT may intentionally overlap. Validation reports overlaps; UNIQUE rejects proven overlaps and also checks at execution so ambiguous data cannot silently choose a row. Overlap analysis uses bounded witnesses for tables of at most 100 rows; it is not a complete solver for complex negation or disjunction. The `OVERLAP_ANALYSIS_LIMITED` warning describes this limitation. Validation and playground results include rule IDs and condition outcomes to explain the match.

The XML size limit is 1,000,000 UTF-8 bytes, with 1–32 inputs, 1–32 outputs, 1–1,000 rules, 4,096 characters per cell, and nesting depth 16. Supplied inputs and saved expected outputs are each limited to 1,000,000 serialized UTF-8 bytes. Decision keys start with a letter or underscore and contain letters, digits, underscores, dots, or hyphens, up to 128 characters; names are limited to 255 characters. DTDs, entities, external XML resources, unsupported semantic elements, additional decisions, and COLLECT aggregation are rejected. The XML namespace is exactly `https://www.omg.org/spec/DMN/20191111/MODEL/`. JSON columns use `typeRef="Any"` (the `json` alias is also accepted). Import/export preserves supported semantics; this release does not provide full DMN or a graphical DRD editor.

## Publish and choose versions

Publishing creates a new immutable `PUBLISHED` version of a decision key, including when the XML is unchanged. Earlier XML and version history remain available. Editing occurs in the local Modeler document; there is no editable server-side published version.

A BPMN task can specify an exact positive `decisionVersion`. If omitted, the engine selects the latest published version when that instance/task first executes and durably pins it. A retry or later visit to the same task in that instance uses the pinned version. A different instance, or a task that has never executed, can select a later deployment. Publishing a new version never changes an existing pin.

Use version comparison before rollout to inspect changed policy, inputs, outputs, and ordered rule cells. Run saved test cases against the candidate version. Saved cases contain a name, explicit JSON inputs, and expected outputs; a test run reports expected/actual differences. Test-case content is deliberate author data and is not protected by execution audit redaction.

## Map process variables

Add a Business Rule Task from the BPMN palette. Set its decision key and optional version, map process variables or static values to inputs, and map named outputs back to process variables. The native BPMN element is `bpmn:businessRuleTask`; its EasyBPM extension contains:

```json
{
  "id": "decideExpense",
  "name": "Decide expense approval",
  "type": "BusinessRuleTask",
  "config": {
    "decisionKey": "expenseApproval",
    "inputs": [
      {"targetName":"amount","source":"variable","value":"amount","type":"number"}
    ],
    "outputs": [
      {"sourceName":"approval","target":"variable","value":"approval","type":"string"}
    ]
  }
}
```

Static mapping values are actual JSON values on the wire: `250`, `true`, `{"tier":1}`, or the plain string `"gold"`. Do not send the string `"250"` for a numeric mapping. The Modeler converts its editing fields into these typed values.

A named COLLECT output maps to an ordered array of that column's values. Its mapping type checks every array element. To store the complete decision result, use the reserved selector `sourceName: "$result"` and `type: "json"`. An ordinary output named `result` remains a named column. A named mapping cannot read from a no-match UNIQUE/FIRST null result; this creates an incident rather than leaving stale variables. All mappings are checked before any output variables are changed.

Missing decisions or versions, invalid/missing input values, type errors, ambiguous UNIQUE evaluation, and invalid output mappings create a `DECISION_TASK` incident and a failed decision execution. In Admin, inspect decision history and the incident, correct input data with the existing process variable editor, then select the incident's Retry action. It invokes the admin-only `POST /incidents/{id}/retry` endpoint and reruns the failed task with its existing decision-version pin.

Retry locks the process instance and rechecks that it is still FAILED at the incident's task and that the incident remains OPEN or ACKNOWLEDGED. If evaluation fails again, the incident remains OPEN, its occurrence count increases, and another failed execution is recorded. If the task succeeds, the incident is resolved with action `RETRIED_SUCCESSFULLY` and the process advances normally. A stale or conflicting concurrent retry returns 409; it cannot advance the process a second time.

Publishing a newer rule version does not repair an invalid pinned definition. Use a corrected decision and a new process instance/deployment when the rule or task configuration itself must change; retry is useful when input data or a previously missing dependency can be corrected. Decision execution is owned by the backend within process transaction/locking behavior; no worker deployment or new RabbitMQ protocol is required.

## APIs and permissions

Use the normal EasyBPM bearer authentication and configured backend URL. `ACCESS_BPM_MODELER` or `ACCESS_BPM_ADMIN` permits authoring, validation, playground evaluation, saved tests, definition queries, and version comparison. Execution history requires `ACCESS_BPM_ADMIN`. Portal access alone and unrelated API-client scopes do not grant decision access. Engine invocation follows the process runtime and does not require the person starting a process to have Modeler access.

| Method and route | Request / result |
| --- | --- |
| POST `/decisions/validate` | `{dmnXml}` → validation issues and parsed table |
| POST `/decisions/test` | `{dmnXml, inputs}` → evaluate an undeployed table |
| POST `/decisions` | `{dmnXml}` → new immutable definition/version |
| GET `/decisions` | Latest definitions |
| GET `/decisions/{key}/versions` | Version history |
| GET `/decisions/{key}/versions/{version}` | Exact definition and XML |
| POST `/decisions/{key}/evaluate` | `{version?, inputs}` → evaluate a deployed decision |
| GET/POST `/decisions/{key}/tests` | List/save `{name, inputs, expectedOutputs}` |
| POST `/decisions/{key}/tests/run` | `{version?}` → case results and differences |
| GET `/decisions/{key}/diff?fromVersion=1&toVersion=2` | Structured changes between versions |
| GET `/decision-executions` | Admin paginated/filterable audit |
| GET `/decision-executions/{id}` | Admin execution details |

Evaluation returns `decisionKey`, resolved `version` when published, `hitPolicy`, `matchedRuleIds`, `outputs`, `explanations`, and `durationMs`. Validation issues contain severity, code, message, and rule/column context where available. The validate endpoint returns `valid:false` and issues for invalid XML; failing evaluation/deployment requests return 400 for invalid XML, unsupported syntax, type errors, or exceeded limits. Unauthenticated/forbidden callers get 401/403; missing definitions/versions/executions get 404. Backend errors appear in the UI and preserve editable work. Audit filters are `key`, `version`, `processInstanceId`, `nodeId`, and `status` (`COMPLETED` or `FAILED`), with zero-based `page` and `size` from 1 to 100. Results include `content`, `totalElements`, `totalPages`, `number`, and `size`.

## Run the expense example

[expense-approval.dmn](examples/expense-approval.dmn) contains two disjoint UNIQUE rules. Amounts below 1000 produce `{"approval":"APPROVED","requiresReview":false}`; 1000 and above produce `{"approval":"REVIEW","requiresReview":true}`. [expense-approval.bpmn](examples/expense-approval.bpmn) invokes the decision and writes both values to process variables. It records a review decision; it does not create a human review task.

In the Modeler, import the DMN file, validate it, test with `{"amount":250}` and `{"amount":1000}`, save both expected-result cases, and deploy. Import the BPMN file, inspect the Business Rule Task mappings, and deploy it. Start `expenseDecisionProcess` with numeric `amount`. Inspect completed instance variables and Admin decision history.

Equivalent PowerShell API steps (run from the repository root, with your backend and token):

```powershell
$baseUrl = 'http://localhost:8080'
$headers = @{ Authorization = "Bearer $token" }
$dmnXml = Get-Content -Raw docs/examples/expense-approval.dmn
$definitionBody = @{ dmnXml = $dmnXml } | ConvertTo-Json -Depth 20
Invoke-RestMethod -Method Post -Uri "$baseUrl/decisions/validate" -Headers $headers -ContentType 'application/json' -Body $definitionBody
$decision = Invoke-RestMethod -Method Post -Uri "$baseUrl/decisions" -Headers $headers -ContentType 'application/json' -Body $definitionBody
$sample = @{ version = $decision.version; inputs = @{ amount = 250 } } | ConvertTo-Json -Depth 20
Invoke-RestMethod -Method Post -Uri "$baseUrl/decisions/expenseApproval/evaluate" -Headers $headers -ContentType 'application/json' -Body $sample
$case = @{ name = 'Automatic approval'; inputs = @{ amount = 250 }; expectedOutputs = @{ approval = 'APPROVED'; requiresReview = $false } } | ConvertTo-Json -Depth 20
Invoke-RestMethod -Method Post -Uri "$baseUrl/decisions/expenseApproval/tests" -Headers $headers -ContentType 'application/json' -Body $case
$run = @{ version = $decision.version } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/decisions/expenseApproval/tests/run" -Headers $headers -ContentType 'application/json' -Body $run
$bpmnXml = Get-Content -Raw docs/examples/expense-approval.bpmn
Invoke-RestMethod -Method Post -Uri "$baseUrl/processes" -Headers $headers -ContentType 'application/xml' -Body $bpmnXml
Invoke-RestMethod -Method Post -Uri "$baseUrl/processes/expenseDecisionProcess/start" -Headers $headers -ContentType 'application/json' -Body '{"variables":{"amount":250}}'
```

Change amount to 1000 to exercise the second row. To demonstrate recovery and pinning, start another instance with `{"variables":{"amount":"invalid"}}`. Inspect its `DECISION_TASK` incident, publish a newer decision, correct the failed instance's amount to numeric 250 in Admin, and select Retry. The existing instance still evaluates its original pinned version and resolves the incident after success. A new instance with no explicit version selects the newer version. Use an exact `decisionVersion` in the BPMN config when every instance must use a specific release.

## Operations and privacy

Admin execution history identifies the decision/version, associated process/node, success/failure, matched rows, condition outcomes, elapsed duration, and sanitized error code. Filters and pagination keep history navigable. The audit stores typed redacted input/output summaries by default, not supplied values. Predicate explanations show rule conditions and match outcomes without echoing input values. Rule literals and saved test data remain visible to authorized authors: avoid putting credentials or secrets in either.

Micrometer publishes `easybpm.decision.evaluations` and `easybpm.decision.duration` tagged by policy/status, `easybpm.decision.failures`, and `easybpm.decision.matched.rules` tagged by policy. The matched-rule counter counts selected rules across evaluations rather than creating a label for every rule ID. Payload values, credentials, and process instance IDs never become metric labels. Draft playground and saved-case runs do not create operational execution audits. Use the execution ID and process/node references to connect operator diagnostics; do not infer the original payload from audit summaries.

Apply the new decision Flyway migration before running the updated backend. Existing process definitions and runtime data remain compatible. Published decision history and durable pins survive backend restarts. Test the database-backed deployment/version/retry paths in PostgreSQL as well as the pure expression and XML tests; a skipped integration test is not evidence of a passing runtime contract.

## Developer verification

Run `./gradlew.bat test` with Java 21 and Docker available. From the Modeler run `npm run lint`, `npm run build`, and `npm run build:desktop`; from Admin run `npm run build`.

The browser regression command is `npm run test:dmn:ui` from the Modeler. It covers decision authoring, DMN/BPMN round trips, mappings, Admin history, and incident retries. It requires Playwright resolvable by Node (locally installed or through `NODE_PATH`) and Chrome; `DMN_BROWSER_CHANNEL` selects another installed Playwright browser channel. The harness starts temporary local Vite servers and closes them after the tests.
