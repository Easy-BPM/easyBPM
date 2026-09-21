---
name: easybpm-task-portal
description: Build, change, or review the end-user EasyBPM Task Portal in easy-bpm-task-portal, including task inboxes, claiming, drafts, dynamic forms, process starts, completion, documents, and user-facing validation. Do not use for Admin-only operations.
---

# EasyBPM Task Portal

Work in `easy-bpm-task-portal/`, a React 19, TypeScript, Vite application for process participants.

## Preserve these contracts

- Keep `services/bpmService.ts`, `types.ts`, UI state, and backend task/form/document DTOs aligned.
- Respect the task lifecycle: visibility, open, automatic or explicit claim, draft save, complete, completed history, and unclaim. Handle another user claiming or completing the task between load and submit.
- Dynamic forms must preserve field types, required and validation rules, nested values where supported, falsy values, and the fallback variable editor when no form is attached.
- Draft saves must not complete work. Completion must send the intended output variables once, prevent accidental duplicate submission, and surface recoverable conflict or validation errors without losing entered data.
- Document fields must enforce backend permissions and constraints, associate files with the correct task, clean up transient UI state, and provide accessible download or PDF preview fallbacks.
- Never trust UI-only ownership or candidate checks; backend authorization remains authoritative.

## Workflow

Trace the user journey through `App.tsx`, components, service calls, and backend endpoints before editing. Preserve filters and selection when refreshing unless the completed action makes that state invalid. Cover loading, empty, unauthorized, stale, offline, and partial-form states.

Prefer clear user-facing language and accessible labels. Disable only the action in flight and make retry behavior explicit.

## Validation

From `easy-bpm-task-portal/`, run `npm run build`. Run targeted tests when a configured runner is available. Exercise the affected lifecycle end to end, including invalid input, draft recovery, claim conflict, duplicate submit protection, and backend-unavailable behavior as applicable.
