---
sidebar_position: 14
---

# Easy BPMN Modeler: AI BPM Agent

## Overview

AI BPM Agent is a new modeling component for non-deterministic workflow steps driven by LLM prompts.

Use it when a process step needs reasoning, content generation, summarization, classification, or decision support that cannot be expressed as fixed deterministic rules.

## What the component provides

- Dedicated **AI Agent** node in the modeler palette
- AI-specific configuration in the properties panel:
  - Provider
  - Endpoint URL
  - Model
  - System prompt
  - Prompt
  - Temperature / max tokens
  - Auth reference (env-based)
- Input/output variable mapping, so process data can be passed into prompts and AI responses can be stored back into process variables

## How to use

1. Open **Process Modeler** in `easybpmn-modeler`.
2. Drag **AI Agent** from the **Activities** palette onto the canvas.
3. Select the node and configure:
   - **Provider** (OpenAI / Anthropic / Google / Custom)
   - **Endpoint URL**
   - **Model**
   - **Prompt** (required)
   - Optional **System Prompt**, **Temperature**, **Max Tokens**
4. Configure **Auth Type/Auth Ref** when endpoint requires credentials.
5. Add **Prompt Input Mapping** to pass process variables into the prompt body.
6. Add **AI Output Mapping** to capture response fields into process variables.
7. Connect the node in the process flow and deploy.

## Export and runtime contract

The modeler exports AI Agent nodes as API-task-compatible runtime payload (`APITask`) with an `aiAgent: true` marker and AI metadata in `properties.body`.

This keeps backend compatibility with the existing async worker execution path while allowing the modeler to treat AI steps as a first-class design component.

## Modeling recommendations

- Keep prompts focused and explicit.
- Use process variables for dynamic context instead of hardcoded business values.
- Reserve non-zero temperature for creative tasks; prefer low temperature for predictable business flows.
- Map only the output fields needed by downstream nodes.

## Validation expectations

AI Agent node validation requires:

- Endpoint URL
- Model
- Prompt

Deploy/export is blocked until these required fields are set.

## Related docs

- [Easy BPMN Modeler: Overview](./easy-modeler-overview.md)
- [Easy BPMN Modeler: Getting Started](./easy-modeler-getting-started.md)
- [Easy BPMN Modeler: Deploy & API Integration](./easy-modeler-deploy-integration.md)
