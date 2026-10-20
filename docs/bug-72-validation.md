# Bug #72: persist controlled agent provider failures

Source: https://github.com/Easy-BPM/easyBPM/issues/72.

## Failure and transaction contract

Provider/network/timeout failures are converted inside the agent call handler to AgentProcessRuntimeException, which contains only an allowlisted error code and fixed message, without the original exception as cause. The handler records a FAILED agent execution with completion timestamp and a safe trace, without output mappings. Only this expected exception is excluded from rollback on the handler's participating transaction. Database errors and unexpected failures retain default rollback behavior.

The execution engine then persists its existing FAILED process and incident in the enclosing start transaction, or routes through an attached error boundary. No REQUIRES_NEW diagnostic transaction is introduced and no process is committed before its runtime state is ready. This fixes the original rollback-only condition rather than catching an UnexpectedRollbackException after evidence has already been lost.

For an unhandled controlled agent failure, POST /processes/{key}/start returns HTTP 422 with status=error, processId, instanceId, instanceStatus=FAILED, nodeId, errorCode and a safe message. Successful/planning/boundary-handled starts retain their normal responses. Provider failure details are not included in the response; no completed-instance history or successful output mapping is created for a failed invocation.

## Diagnostics and validation

Provider error logs for Ollama, OpenAI, Azure OpenAI and Gemini now emit only an error classification, not response text or raw exception chains. Ollama success logging no longer includes the configured endpoint. These providers are shared by runtime tasks, so the safe logging adjustment applies to their other callers too; returned provider DTOs are unchanged.

Seven PostgreSQL integration cases exercise the actual Spring transaction proxies, real agent handler/provider factory and Ollama HTTP implementation: refused connection; local HTTP timeout; local provider 500 with a synthetic sensitive marker; successful provider response and mapped result; provider-free planning; error-boundary recovery; and MockMvc HTTP 422 serialization. Tests reread persisted process/execution/incident state, assert no End/completion events or partial output, and inspect application logs and incident diagnostics for the synthetic marker. The original six-case regression failed four failure paths on unpatched master; success and planning passed.

Two unit cases cover untrusted error codes and safe runtime-message recognition. Full backend suite results are recorded in the PR. Rabbit publication is mocked; PostgreSQL transactions and local provider HTTP are real. No external model, credentials or live identity provider is used. The HTTP harness does not exercise the security filter chain.

No automatic retries or new idempotency semantics are introduced. The existing caller locking and retry configuration are retained; each tested unhandled failed invocation creates one execution and incident. Fatal database errors remain rollback failures and are not converted into successful diagnostic commits. This change does not redesign all provider/task failures or remove normal persisted agent input/output audit data, which remains governed by existing access controls. No migration or frontend change is required.
