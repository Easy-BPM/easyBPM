# Bug #70: catch message payload mappings

Source: https://github.com/Easy-BPM/easyBPM/issues/70.

Catch events now resolve message.payload before persisting variables or advancing. sourceValue (sourceName alias) identifies an incoming top-level field; targetVariable (value alias) must identify a declared process variable. Static mappings use sourceValue as their literal. Supported types are string, number, boolean and json; values are not coerced, and explicit null is preserved. JSON objects, arrays, numeric zero and false retain their types. Configured mappings write only their targets; absent or empty mappings retain legacy incoming-name behavior.

The complete mapping is validated before writes. Missing sources, undeclared/blank/duplicate targets, malformed configuration or type mismatch throw a safe mapping exception. The existing outer transaction rolls back subscription receipt, variable writes and process changes. The external inbox records FAILED; the subscription remains AWAITING for a corrected message with a new message ID. Reusing any recorded message ID retains the existing deduplication policy. No payload values are included in mapping diagnostics.

Acceptance evidence: MessagePayloadMappingIntegrationTest runs real BPMN XML through process start, message subscription, inbox acceptance, completion and PostgreSQL archival. Ten cases cover object/array/zero/false/null, duplicate IDs, legacy behavior, correlation isolation, atomic failures, aliases and static typed mappings. Nine cases fail on unpatched master; legacy behavior passes. Existing subscription/process pessimistic locks are preserved.

Modeler: tests/message-mapping-ui.mjs runs two XML round-trips in Chrome and checks source/target/type and correlation keys. No serialization change was necessary. Modeler lint/build and the browser check pass; the existing chunk-size warning remains. Backend suite results are recorded in the PR.

Scope: subscribed catch events. Message-start mapping, throw payload behavior and broker delivery guarantees are unchanged. Integration publisher calls are mocked; PostgreSQL and transactions are real. No schema migration or frontend behavior change.
