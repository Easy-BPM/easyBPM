# Bug #67: sensitive authentication console output

Source: https://github.com/Easy-BPM/easyBPM/issues/67 (BUG-3).

The local-login service no longer logs its serialized request body. Adjacent LoginView error diagnostics use fixed operation messages instead of raw exceptions: sign-in initialization, local sign-in and OIDC start. This prevents response content from being copied into console capture when an error includes sensitive data.

Authentication requests, response parsing, session persistence and existing error handling are otherwise unchanged. No credentials or tokens are added to diagnostics and no backend/provider configuration is changed.

## Acceptance evidence

- AC-1: credential-bearing console.log removed from bpmService.login.
- AC-2: full App browser regressions exercise successful login, HTTP 401, HTTP 500, network failure, auth-configuration failure and OIDC-start failure. Error fixtures deliberately contain synthetic secret/token markers to detect raw-exception logging.
- AC-3: the browser probe wraps every callable own console method and tracks only method names and a boolean sensitivity flag. It detects synthetic user/password/token markers, serialized password fields and authorization-header patterns. No captured argument, session or request body is printed or persisted. The test fails on unpatched master with only a boolean leak indication.
- AC-4: the same regression verifies the POST body/content type, valid session on success, no session on failure, retry-ready controls and no uncaught page errors. Task Portal production build passes with Vite 8.0.16.
- AC-5: all Task Portal console calls and adjacent authentication branches were inspected; the three raw exception logs are replaced with fixed messages.

Run npm run test:auth-logging:ui in the Task Portal with Playwright available locally or through NODE_PATH, and installed Chrome (AUTH_BROWSER_CHANNEL can select another channel). API calls are intercepted fixtures; no real credential or provider is used. This validates frontend behavior, not live backend authentication or OIDC exchange. No redesign or credential rotation is included.
