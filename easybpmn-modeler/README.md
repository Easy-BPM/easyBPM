<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://github.com/user-attachments/assets/0aa67016-6eaf-458a-adb2-6e31a0763ed6" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/60db0451-1450-4005-a98e-47ac6a8f1a24

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
3. Run the app:
   `npm run dev`

## Deploy Integration

This modeler is integrated with Easy BPM backend deploy API.

- Deploy endpoint: `POST /processes`
- Default API base URL: `http://localhost:8085`
- Optional override: set `VITE_API_BASE_URL` in your environment

Use the `Deploy Process` button in the toolbar to deploy the current model directly.
