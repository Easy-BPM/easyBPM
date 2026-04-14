# Message Events (Catch & Throw)

## Overview
Easy BPM supports BPMN-style Message Catch and Message Throw events for process orchestration and external system integration.

- **Message Catch Event**: Pauses a process instance, waiting for a message with a specific name and correlation key.
- **Message Throw Event**: Sends a message with a name, correlation key, and optional payload. Any waiting process instance with a matching catch event resumes.
- **Correlation**: Matching is performed on both `messageName` and `correlationKey`.

## Example
See the `examples` section for minimal working process definitions and integration tests.
