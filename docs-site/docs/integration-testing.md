# Integration Testing

Integration tests validate message event handling and process orchestration.

- Message Catch event pauses the process as expected.
- Message Throw event sends a message with the correct correlation key.
- The engine resumes and completes the waiting process instance when the message is delivered.

See `src/test/kotlin/com/easy/bpm/integration/ProcessIntegrationTest.kt` for test examples.
