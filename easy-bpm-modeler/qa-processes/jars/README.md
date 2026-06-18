# QA Code Task JAR

Upload `test-service.jar` from this folder in the Easy BPM Modeler Code Task tools.

Use these values when attaching it to `qa_code_task_component`:

```text
Class: TestService
Method: processOrder
Input 0: orderId
Input 1: amount
Output: processedOrderMessage
```

The JAR also exposes `greet`, `add`, `multiply`, and `validateEmail` for quick component checks.
