# Human task followed by unavailable agent — 2026-10-21

Related: #72, #80; PR #79; branch `codex/fix-72-agent-failure`.

## Contract

Start → human review → synchronous agent call → End. The Ollama endpoint `http://127.0.0.1:1` intentionally refuses connections. Human completion must commit; process execution must stop at the agent with an OPEN incident. Existing process status is FAILED (there is no new process-status enum). Never replay the completed human task or apply successful agent outputs.

The completion endpoint retains HTTP 409, with a structured committed-task result: `code=TASK_COMPLETED_WITH_INCIDENT`, `taskId`, `taskStatus=COMPLETED`, and a fixed safe message. Other validation/authorization/conflict responses retain their behavior. Portal also supports the prior plain-text response, only after an authenticated task lookup confirms COMPLETED.

## Live browser evidence

Original Docker Portal `localhost:5174`: instance 4, task 5, incident 2. Started, automatically claimed, saved comment draft, and completed using the interface. Backend saved task/comment and created the incident; Portal incorrectly retained editable fields and action buttons. A duplicate completion was rejected. Registered as #80.

Corrected Portal was built inside the running Docker container with Vite 8.0.16, in isolated `/tmp/qa-72`; its build was served on `127.0.0.1:5175` against the real Docker backend. Existing application sources/containers were not replaced or restarted. No network interception or mocked API was used for this browser scenario.

Instance 5, task 6: start, claim, draft and completion through the corrected UI. Message clearly confirms the completed task and open incident; submitted comment stays visible, fields become disabled, Complete/Save/Unclaim disappear. Returning to All shows no pending tasks; Completed contains task 6. Original Docker backend response exercised the legacy compatibility path. The new JSON response is covered by actual Spring MockMvc/PostgreSQL integration and service contract tests.

Admin for instance 4 confirmed FAILED at agent, OPEN incident, preserved human comment and original decision, one TASK_COMPLETED then AGENT_PROCESS_STARTED / INCIDENT_CREATED / PROCESS_FAILED, no End or successful agent completion.

## Automated validation

- `./gradlew.bat test`: 424 tests, 418 passed, 6 skipped, zero failures/errors; worker has no test sources.
- 10 AgentRuntimeFailureIntegrationTest cases include downstream unavailable agent, timeout, and successful completion after human work; assertions cover preserved string/false/zero values, OPEN incident, exactly one completion/execution, duplicate rejection and absent success outputs.
- `node qa/agent-after-human/portal-contract.cjs`: 9 service contract probes (success, structured incident, wrong task, conflict, validation, forbidden, verified legacy result, pending task and forbidden lookup).
- `npm run build` in isolated Docker source copy using existing container dependencies: passed.

Screenshots remain in the QA workspace under `qa/agent-after-human/`: `portal-completed-task-stale.jpg`, `portal-fixed-incident.jpg`, `admin-failed-instance.jpg`.

## Repeat manually

1. Deploy an agent key `qa_20261021_human_agent_unavailable` with Ollama endpoint above, no tools, and a synthetic goal.
2. Import/deploy `scenario.bpmn` and start through Portal.
3. Open the human task from All, acknowledge claim, add a comment and save a draft.
4. Complete. Expect COMPLETED task, saved data and incident warning; no editing or repeat completion actions.
5. Return to inbox: task is in Completed only. In Admin inspect FAILED instance and OPEN incident at agent; End must not have executed.
