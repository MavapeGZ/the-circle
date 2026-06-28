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
npm run test:e2e:auto
```

Set `E2E_HEADLESS=false` before that command if you want Chrome to stay visible while the suite runs.

## Environment

- `E2E_BASE_URL` defaults to `http://localhost:5173`
- `E2E_LOGIN_URL` defaults to `${E2E_BASE_URL}/login`
- `E2E_TIMEOUT_SECONDS` defaults to `10`
- `AUTH_OTP_EXPOSE_DEV=true` must be enabled on ms-users for the auth flow
	scenario to read registration and login codes from the JSON response.

The initial suite is a smoke harness. Extend it with feature files under `src/test/resources/features/` and step definitions under `src/test/java/com/thecircle/e2e/steps/`.