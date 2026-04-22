---
sidebar_position: 35
title: Code Task Test JAR Guide
description: Using the test JAR file for Easy BPM Code Task execution
---

# Code Task Test JAR Guide

This guide demonstrates how to use the **TestService** sample JAR file to test the Easy BPM Code Task feature. The test JAR contains several dummy methods that showcase different execution patterns.

## Overview

The test JAR (`test-service.jar`) is a pre-built, ready-to-use JAR file containing the `TestService` class with multiple executable methods. It's designed for:

- **Testing** the Code Task execution pipeline
- **Learning** how to integrate custom code execution
- **Demonstrating** variable input/output mapping
- **Validating** the Code Task Admin UI

## Download Test JAR

The test JAR file is available here: [test-service.jar](/test-service.jar)

## TestService Methods

The TestService class provides the following methods:

### 1. `greet(String name)`

**Purpose:** Simple greeting method

**Parameters:**
- `name` (String): The name to greet

**Returns:** String - A greeting message

**Example Output:**
```
"Hello, John! Welcome to Easy BPM Code Task execution."
```

**Use Case:** Test basic string parameter passing and return value handling

---

### 2. `add(int a, int b)`

**Purpose:** Calculate sum of two numbers

**Parameters:**
- `a` (int): First number
- `b` (int): Second number

**Returns:** int - Sum of a and b

**Example Output:**
```
15  // if a=5, b=10
```

**Use Case:** Test numeric parameter passing and integer return values

---

### 3. `multiply(int a, int b)`

**Purpose:** Calculate product of two numbers

**Parameters:**
- `a` (int): First number
- `b` (int): Second number

**Returns:** int - Product of a and b

**Example Output:**
```
50  // if a=5, b=10
```

**Use Case:** Test multiple integer parameters and arithmetic operations

---

### 4. `processOrder(String orderId, double amount)`

**Purpose:** Process order information (simulates real business logic)

**Parameters:**
- `orderId` (String): The order ID (e.g., "ORD-12345")
- `amount` (double): The order amount (e.g., 99.99)

**Returns:** String - A formatted order confirmation message

**Example Output:**
```
"Order ORD-12345 processed successfully. Amount: $99.99"
```

**Use Case:** Test mixed parameter types (String and double) and formatted string output

---

### 5. `validateEmail(String email)`

**Purpose:** Validate email format

**Parameters:**
- `email` (String): The email address to validate

**Returns:** boolean - true if valid, false otherwise

**Use Case:** Test string validation logic and boolean return values

---

## Step-by-Step: Using Test JAR in Easy BPM

### Step 1: Upload the JAR

1. Open **Easy BPM Admin** (http://localhost:5173)
2. Navigate to **Code Tasks** section
3. Click **Upload JAR** button
4. Select the `test-service.jar` file
5. Click **Upload**

### Step 2: Discover Methods

1. After successful upload, you'll see the JAR listed in the uploaded JARs section
2. Click **Discover Methods** for the test-service JAR
3. The system will scan and display available methods:
   - `TestService.greet(String name)`
   - `TestService.add(int a, int b)`
   - `TestService.multiply(int a, int b)`
   - `TestService.processOrder(String orderId, double amount)`
   - `TestService.validateEmail(String email)`

### Step 3: Create a Process with Code Task

1. Open **Easy BPMN Modeler** (http://localhost:3000)
2. Create a new process
3. Add a **Code Task** node:
   - Drag Code Task to canvas
   - Click to open properties
   - Select JAR: `test-service`
   - Select Class: `TestService`
   - Select Method: e.g., `add(int a, int b)`

### Step 4: Configure Variable Mapping

**Input Mapping Example (for `add` method):**

In the Code Task properties, map process variables to method parameters:

```json
{
  "inputMapping": {
    "a": "numberOne",    // process variable "numberOne" → method param "a"
    "b": "numberTwo"     // process variable "numberTwo" → method param "b"
  },
  "outputVariable": "sum"  // method return value → process variable "sum"
}
```

**Sample Process Variables:**
```json
{
  "numberOne": 5,
  "numberTwo": 10
}
```

**Expected Result After Execution:**
```json
{
  "numberOne": 5,
  "numberTwo": 10,
  "sum": 15
}
```

### Step 5: Deploy & Execute

1. Deploy the process from the Modeler
2. Start a new process instance from Admin UI or Task Portal
3. The Code Task will execute the selected method
4. Monitor execution in **Code Task Executions** section
5. View results including return value and execution time

---

## Test Scenarios

### Scenario 1: String Greeting

**Process Setup:**
- Method: `greet(String name)`
- Input Variable: `customerName` = "Alice"
- Output Variable: `greeting`

**Expected Result:**
```
greeting = "Hello, Alice! Welcome to Easy BPM Code Task execution."
```

---

### Scenario 2: Numeric Calculation

**Process Setup:**
- Method: `add(int a, int b)`
- Input Variables: `value1` = 42, `value2` = 8
- Output Variable: `total`

**Expected Result:**
```
total = 50
```

---

### Scenario 3: Business Logic - Order Processing

**Process Setup:**
- Method: `processOrder(String orderId, double amount)`
- Input Variables: 
  - `orderId` = "ORD-2026-001"
  - `orderAmount` = 149.99
- Output Variable: `orderConfirmation`

**Expected Result:**
```
orderConfirmation = "Order ORD-2026-001 processed successfully. Amount: $149.99"
```

---

### Scenario 4: Email Validation

**Process Setup:**
- Method: `validateEmail(String email)`
- Input Variable: `userEmail` = "user@example.com"
- Output Variable: `isValidEmail`

**Expected Result:**
```
isValidEmail = true
```

---

## Monitoring Executions

### Code Task Execution Details

In **Easy BPM Admin** → **Code Task Executions** tab, you can view:

| Field | Description |
|-------|-------------|
| **Status** | SUCCESS, FAILED, or TIMEOUT |
| **Executed At** | Timestamp of execution |
| **Execution Time** | Time taken in milliseconds |
| **JAR ID** | ID of the uploaded JAR |
| **Class Name** | TestService |
| **Method Name** | The executed method |
| **Input Variables** | Parameters passed to the method |
| **Output Variable** | Variable name for return value |
| **Return Value** | The actual result |
| **Error Message** | Details if execution failed |

### Viewing Execution Details

1. Click on any execution row in the table
2. Modal opens showing:
   - Full JSON view of input/output
   - Method signature
   - Execution metadata
   - Error stack trace (if failed)

---

## Troubleshooting

### Method Not Discovered

**Problem:** Method doesn't appear after clicking "Discover Methods"

**Solution:**
1. Ensure JAR file was uploaded successfully
2. Verify JAR contains compiled `.class` files
3. Check method is `public static`
4. Re-upload the JAR

### Type Mismatch Error

**Problem:** Execution fails with "Type mismatch" error

**Solution:**
1. Verify input variable types match method parameters
2. For `int` parameters, ensure variable is numeric
3. For `String` parameters, ensure variable is text
4. Check method signature in discovery results

### Execution Timeout

**Problem:** Code Task shows TIMEOUT status

**Solution:**
1. Default timeout is 30 seconds
2. For long-running operations, increase timeout in process definition
3. TestService methods should complete in < 1ms

---

## Creating Your Own Code Task JAR

To create a custom JAR similar to TestService:

### 1. Create Java Class

```java
public class MyService {
    public static String myMethod(String input) {
        return "Processed: " + input;
    }
}
```

### 2. Compile

```bash
javac MyService.java
```

### 3. Create JAR

```bash
jar cvf my-service.jar MyService.class
```

### 4. Upload to Easy BPM

1. Go to Code Tasks section
2. Upload your JAR file
3. Discover and use methods in processes

---

## API Reference

For API details on Code Task execution, see:
- [Code Task REST API](./api-controllers.md#code-task-endpoints)
- [Code Class Discovery Service](./api-controllers.md#code-class-discovery)

---

## Next Steps

- ✅ Download and upload `test-service.jar`
- ✅ Create a test process with Code Task
- ✅ Configure variable mapping
- ✅ Execute and monitor results
- ✅ Create your own custom JAR

For more information on Phase 8 Code Task feature, see:
- [Phase 8: Code Task & JAR Execution](./phase-8-documentation-index.md)
- [Phase 8.1-8.2 Delivery Summary](./phase-8-1-9-8-2-delivery-summary.md)
- [Code Task Quick Start](./code-task-quick-start.md)
