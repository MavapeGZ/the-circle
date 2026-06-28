# The Circle E2E

Browser-driven end-to-end tests for the full stack.

## Stack

- Java 21
- Cucumber JVM
- Selenium 4 with Selenium Manager
- JUnit Platform Suite

## Run

Start the application stack first, then execute:

```bash
cd e2e
mvn test
```

From the repo root you can also use:

```bash
npm run test:e2e
```

To start the stack automatically and run the suite in one step:

```bash
# Windows (PowerShell)
npm run test:e2e:auto

# Linux / macOS (bash)
npm run test:e2e:auto:linux
```

Set `E2E_HEADLESS=false` before that command if you want Chrome to stay visible while the suite runs.

## Environment

- `E2E_BASE_URL` defaults to `http://localhost:5173`
- `E2E_LOGIN_URL` defaults to `${E2E_BASE_URL}/login`
- `E2E_API_BASE_URL` defaults to `http://localhost:8080/api` (API gateway)
- `E2E_CONTRACTS_API_BASE_URL` defaults to `http://localhost:8083/api` (ms-contracts direct)
- `E2E_TIMEOUT_SECONDS` defaults to `60`
- `AUTH_OTP_EXPOSE_DEV=true` (ms-users) and `OTP_EXPOSE_DEV=true` (ms-contracts) must be
	enabled so the flows can read OTP codes from the JSON response. Both are honored
	**only under the `local`/`test` Spring profile** — `OtpExposureGuard` aborts startup
	if they are set on any deployed profile.

The initial suite is a smoke harness. Extend it with feature files under `src/test/resources/features/` and step definitions under `src/test/java/com/thecircle/e2e/steps/`.