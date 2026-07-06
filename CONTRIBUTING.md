# Contributing to The Circle

Quick conventions for changes that affect the runtime behaviour of any
microservice. Most of these exist to prevent accidental data leaks; the
review history of the project has more than one incident where a default
Spring behaviour was about to surface secrets in HTTP responses or logs,
so the rules below are the bare minimum to keep that from happening
again.

## Logging

- **Never** set `logging.level.org.springframework.web=DEBUG` in any
  shared environment (`dev`, `staging`, `prod`). That level instructs
  Spring to log the full request payload — including `password`,
  `cardNumber`, `iban`, and the OTP carried in `EmailRequestDto.variables`.
  Local debugging only.
- **Never** log a request DTO with `log.info("req={}", req)` for any
  endpoint that touches credentials, card data, IBAN, OTPs, JWTs or
  reset tokens. The auth + payment DTOs already mask those fields in
  `toString()`, but the safe path is to log a curated set of fields
  (user id, request id) instead of the whole object.
- The only `log.*request*` call audited in the codebase logs the
  template name and recipient address only (`EmailService.java`). If
  you add another, mirror that pattern.

## HTTP error responses

- Every microservice ships a `ValidationErrorHandler`
  (`@RestControllerAdvice` over `MethodArgumentNotValidException`).
  Do not delete it. The default Spring response echoes
  `getRejectedValue()` for each failing field, which would surface
  passwords / IBANs / card numbers into the HTTP body, browser dev
  tools, proxy access logs and SIEMs.
- `server.error.include-message` and `server.error.include-stacktrace`
  default to `never`. The `dev` / `local` profiles override them to
  `always` for development. Any non-dev profile that flips them on is
  a release-blocker.

## Actuator

- The exposure list in every `application.{yml,properties}` is limited
  to `health,info`. Do **not** add `httpexchanges` (Spring Boot 3.x) or
  `httptrace` (deprecated, Spring Boot 2.x) — both record full
  request/response payloads in-memory and serve them over HTTP without
  any field-level redaction. If you genuinely need request tracing,
  emit structured events through ms-notifications-style auditing
  instead.

## Validation

- Annotate `@RequestBody` parameters with `@Valid` whenever you add
  jakarta-validation constraints to the DTO. Without `@Valid`, the
  constraints are silently ignored — defence in depth means at least
  both layers (annotations + manual checks in the service) are in
  place for fields that affect security decisions (OTP length, card
  Luhn, IBAN structure).
- A pre-commit hook at `.githooks/pre-commit` warns when a staged Java
  file adds `@RequestBody` without `@Valid`. Install it once per
  clone:
  ```bash
  git config core.hooksPath .githooks
  ```

## Secrets in DTOs

- Any DTO field that holds a password, reset token, card number,
  CVC, IBAN or OTP must be excluded from the generated `toString()`:
  - Lombok `@Data` DTOs: annotate the field with
    `@ToString.Exclude`.
  - Java records: override `toString()` and replace the sensitive
    components with `***`.
- A failing review (see PR #62, PR #63) is faster than a leaked
  credential. Err on the side of masking.

## Cross-service changes

- When a change crosses a service boundary (e.g. a new internal
  endpoint), update the calling service's HTTP client at the same
  time. Internal endpoints live under `/internal/**` and are guarded by
  `X-Internal-Api-Key`; the gateway is configured to never route
  `/internal/**`, so leaking these accidentally to the public is
  harder, but still possible if a controller is added under the wrong
  path. Double-check `SecurityConfig` and the gateway routes when in
  doubt.

## Testing

Full reference (current state + how-to) lives in
[issue #20](https://github.com/MavapeGZ/the-circle/issues/20). Summary:

### Backend

- Maven multi-module, Spring Boot 3.2.12 / Java 21. Test framework is
  JUnit 5 + Mockito + AssertJ, all from `spring-boot-starter-test`. H2
  (`scope=test`) backs persistence tests.
- Tests mirror the target package under `src/test/java`; name them
  `<ClassUnderTest>Test`; name methods `method_condition_expectedResult`.
- Pick one of the three patterns already in the codebase:
  - **Pure unit:** `@ExtendWith(MockitoExtension.class)` + `@Mock` /
    `@InjectMocks` (services, controller logic).
  - **Web slice:** `@WebMvcTest(Controller.class)` + `MockMvc` +
    `@MockBean` (HTTP status, JSON, validation).
  - **JPA slice:** `@DataJpaTest` against in-memory H2 (repositories).
  - DTO constraints can be tested with a raw `jakarta.validation.Validator`,
    no Spring context.
- `api-gateway` now has `spring-boot-starter-test` and route tests
  (`GatewayApplicationTest` context smoke + `GatewayRoutesConfigTest`
  asserting each route id, `Path` predicate and target URI). Route URIs
  assert the in-code defaults, so don't set `MS_*_URL` env vars when
  running these locally.
- Run:
  ```bash
  cd backend && mvn test                         # all modules
  cd backend && mvn -pl ms-contracts test        # one module
  cd backend && mvn -pl ms-contracts test -Dtest=ContractServiceTest
  ```

### Frontend

- Stack: **Vitest + @testing-library/react + jest-dom + jsdom**,
  unit/integration only. Config lives in the `test` block of
  `frontend/vite.config.js`; global setup (jest-dom matchers, cleanup)
  in `frontend/src/test/setup.js`.
- Co-locate tests next to the code as `Component.test.jsx` /
  `module.test.js`. Existing suites cover the pure functions in
  `src/utils/*`, the `usePageTitle` hook, and `src/services/api.js`
  (with `axios` mocked) — mirror those when adding more.
- Run from `frontend/`:
  ```bash
  npm run test            # single run
  npm run test:watch      # watch mode
  npm run test:coverage   # with coverage
  ```
- Browser E2E lives in the top-level `e2e/` Maven module (Cucumber +
  Selenium 4, Java 21). Start the stack, then run `cd e2e && mvn test`,
  or `npm run test:e2e` from the repo root. `npm run test:e2e:auto`
  (PowerShell) / `:auto:linux` (bash) boot the stack and run the suite
  in one step.

### CI

- `.github/workflows/ci.yml` runs on every PR to `main` (and pushes to
  `main`): a **backend** job (`mvn -B test`, JDK 21) and a **frontend**
  job (`npm ci` → `npm run test` → `npm run build`, Node 20). Tests and
  build are separate steps on purpose, so a failing test fails the job
  independently of the production bundle. E2E is not part of CI.

All tests and test documentation are written in English.

## Database migrations

- The project currently uses Hibernate `ddl-auto=update`. That means
  column additions and table creations happen automatically on the
  next boot, but CHECK constraints and enum value lists do **not**
  update. Two issues from the existing repo history (the
  `email_log_status_check` mismatch, the article index `status`
  mapping drift) trace back to this. Whenever you add a new enum
  value or tighten a CHECK constraint, write the corresponding
  `ALTER` statement and ship it alongside the code change.
