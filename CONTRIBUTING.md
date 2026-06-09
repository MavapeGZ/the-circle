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

## Database migrations

- The project currently uses Hibernate `ddl-auto=update`. That means
  column additions and table creations happen automatically on the
  next boot, but CHECK constraints and enum value lists do **not**
  update. Two issues from the existing repo history (the
  `email_log_status_check` mismatch, the article index `status`
  mapping drift) trace back to this. Whenever you add a new enum
  value or tighten a CHECK constraint, write the corresponding
  `ALTER` statement and ship it alongside the code change.
