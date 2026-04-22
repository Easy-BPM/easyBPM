# Easy BPM Task Portal

Task-first portal for Easy BPM process instances.

## Features

- Lists and opens user tasks
- Renders task forms when `formId` exists
- Falls back to editable task variables when no form is attached
- Allows adding new task variables in no-form tasks
- Completes tasks and sends variables to backend for global process synchronization

## Local Run

1. Install dependencies:
   `npm install`
2. Optional API override (default is `http://localhost:8080`):
   set `VITE_API_BASE_URL`
3. Start dev server:
   `npm run dev`

## Build

- `npm run build`
