# Project Overview

**The Circle** is a collaborative circular-economy web platform (Master's Thesis / TFM) for buying,
selling, renting, and donating goods at symbolic prices, prioritizing low-income and
socially-excluded users. It pairs a React + Vite single-page frontend with a Spring Boot
microservices backend fronted by a Spring Cloud Gateway, adding official ID verification (KYC) and
automatic digital-contract generation with OTP signatures to keep lending and donating safe.

## Repository Structure

- `frontend/` — React 18 + Vite SPA (pages, components, `AuthContext`, axios `api` service);
  deployed to Vercel.
- `backend/` — Maven multi-module parent (`the-circle-parent`) containing the gateway and all
  microservices.
  - `backend/api-gateway/` — Spring Cloud Gateway: single public entry point, CORS, path routing.
  - `backend/ms-users/` — identity, profiles, JWT auth, login OTP, KYC, password reset.
  - `backend/ms-catalog/` — articles (offers/demands) CRUD backed by OpenSearch.
  - `backend/ms-contracts/` — contract PDF generation, OTP signature workflow, payments.
  - `backend/ms-gamification/` — points, badges, and leaderboards.
  - `backend/ms-notifications/` — transactional email dispatch with HTML templates.
  - `backend/init.sql` — Postgres bootstrap (per-service databases).
- `docs/` — project documentation (`arquitectura/`, `memoria/`). > TODO: docs are placeholders.
- `deploy/` — backup/restore scripts and `MIGRATION.md` for production operations.
- `docker-compose.yml` / `docker-compose.prod.yml` — local and production orchestration.
- `Caddyfile` — reverse proxy / TLS for production.
- `.githooks/pre-commit` — warns on `@RequestBody` without `@Valid`.

## Build & Development Commands

Install frontend dependencies:

```bash
cd frontend && npm install
```

Run everything (gateway + all services + frontend) from the repo root via the orchestration script:

```bash
npm run dev:full
```

Run individual pieces (root `package.json` scripts):

```bash
npm run dev:frontend    # vite dev server (http://localhost:5173)
npm run dev:gateway     # api-gateway on :8080
npm run dev:users       # ms-users on :8081
npm run dev:catalog     # ms-catalog on :8082
npm run dev:contracts   # ms-contracts on :8083
```

> TODO: no root scripts exist yet for `ms-gamification` (:8084) or `ms-notifications` (:8085);
> start them with `cd backend/ms-gamification && mvn spring-boot:run` (and likewise for
> notifications) or via Docker Compose.

Frontend build / preview:

```bash
cd frontend
npm run build      # vite production build
npm run preview    # serve the production build locally
```

Backend build & test (Maven, Java 21):

```bash
cd backend
mvn clean install            # build + test all modules
mvn -pl ms-users test        # test a single module
mvn -pl ms-contracts spring-boot:run   # run a single service
```

Full stack via Docker (requires a root `.env`, see Security & Compliance):

```bash
docker compose up -d         # postgres, opensearch, all services, caddy
docker compose -f docker-compose.prod.yml up -d   # production profile
```

> TODO: no frontend lint or type-check scripts are configured (`eslint`/`tsc` not wired up).

## Code Style & Conventions

- **Backend:** Java 21, Spring Boot 3.2.x, Spring Cloud 2023.0.x, Lombok for boilerplate.
  Package root `com.thecircle.<service>`; layered as
  `controllers` → `service` → `repository`/`model`, with `dto`, `config`, `client`, `security`,
  `util` siblings. Internal-only HTTP endpoints live under `/internal/**` and are guarded by an
  `X-Internal-Api-Key` header.
- **Frontend:** React 18 function components with hooks, ES modules, JSX in `.jsx` files. Pages in
  `src/pages/`, reusable UI in `src/components/`, global auth state in `src/context/AuthContext.jsx`,
  HTTP access centralized in `src/services/api.js` (axios instance with token + 401 interceptors).
  Styling via Tailwind CSS.
- **Naming:** Java classes `PascalCase`, methods/fields `camelCase`; React components `PascalCase`,
  files match the component name.
- **Lint config:** pre-commit hook at `.githooks/pre-commit` (install with
  `git config core.hooksPath .githooks`). > TODO: no formatter (Prettier/Spotless) configured.
- **Commit messages:** Conventional Commits, e.g. `fix(auth): require verified user for protected
  route guard` (matches existing history: `type(scope): summary`).

## Architecture Notes

```
            ┌─────────────┐
            │  Browser    │  React + Vite SPA (Vercel)
            └──────┬──────┘
                   │ HTTPS  (axios, JWT Bearer + device cookie)
            ┌──────▼──────┐
            │   Caddy     │  TLS / reverse proxy (prod)
            └──────┬──────┘
                   │
            ┌──────▼──────────┐
            │  api-gateway    │  :8080  Spring Cloud Gateway
            │  CORS + routing │  (never routes /internal/**)
            └─┬────┬────┬────┬┴───┬───────┐
   /api/auth  │    │    │    │    │       │  /api/notifications
   /api/users │    │    │    │    │       │
        ┌─────▼─┐ ┌▼────────┐ ┌▼───────┐ ┌▼──────────────┐ ┌▼───────────────┐
        │ms-users│ │ms-catalog│ │ms-contr.│ │ms-gamification│ │ms-notifications│
        │ :8081  │ │ :8082    │ │ :8083   │ │ :8084         │ │ :8085          │
        └───┬────┘ └────┬─────┘ └────┬────┘ └──────┬────────┘ └───────┬────────┘
            │           │            │             │                  │
       ┌────▼────┐  ┌───▼──────┐  ┌──▼──────┐  ┌───▼────┐         ┌───▼────┐
       │ users_db│  │OpenSearch│  │contracts│  │ gamif. │         │ notif. │
       │(Postgres)│ │  index   │  │   _db   │  │  _db   │         │  _db   │
       └─────────┘  └──────────┘  └─────────┘  └────────┘         └────────┘
```

- The **gateway** is the only public surface, mapping `/api/<domain>/**` to each service and
  applying global CORS with credentials. It is configured never to route `/internal/**`.
- **ms-users** owns authentication: issues short-lived JWTs (no refresh flow), login OTP with a
  device-trust cookie, KYC document validation, and password reset.
- **ms-catalog** stores articles in **OpenSearch** rather than a relational table.
- **ms-contracts** generates contract PDFs and runs the OTP signature + payment workflow.
- Services talk to each other over internal HTTP clients (`client/`, `*Client.java`) using
  `/internal/**` endpoints secured by `X-Internal-Api-Key`; each service has its own Postgres
  database.
- The frontend attaches the JWT from `localStorage` to every request and redirects to login on a
  `401` (except `/auth/*` and explicitly opted-out probes).

## Testing Strategy

- **Backend unit/integration:** JUnit (Spring Boot Test) per module under `src/test/java`. Existing
  suites cover services and security (e.g. `SignatureServiceTest`, `ContractServiceTest`,
  `GamificationControllerSecurityTest`, `KycServiceTest`).

  ```bash
  cd backend && mvn test            # all modules
  mvn -pl ms-contracts test         # one module
  ```

- **Frontend:** > TODO: no automated test runner is configured. Manual verification via
  `npm run dev`; ad-hoc scratch checks live under `frontend/__verify/` (untracked).
- **CI:** > TODO: no CI workflow files found in the repo; run `mvn clean install` and `npm run build`
  locally before opening a PR.

## Security & Compliance

- **Secrets:** never commit credentials. A root `.env` supplies `DB_USER`, `DB_PASSWORD`,
  `JWT_SECRET`, the per-service `*_INTERNAL_KEY` values, mail settings, and `DUCKDNS_TOKEN`. Use
  long random values for `JWT_SECRET` and the internal keys.
- **DTO masking:** any field holding a password, reset token, card number, CVC, IBAN, or OTP must be
  excluded from `toString()` — Lombok `@ToString.Exclude` for `@Data` classes, or an overridden
  `toString()` (replace with `***`) for records.
- **Error responses:** keep each service's `ValidationErrorHandler` (`@RestControllerAdvice`); never
  delete it. `server.error.include-message` / `include-stacktrace` default to `never` — enabling
  them outside `dev`/`local` is a release blocker.
- **Logging:** never set `logging.level.org.springframework.web=DEBUG` in shared environments, and
  never log full request DTOs that touch credentials, cards, IBANs, OTPs, JWTs, or reset tokens.
- **Actuator:** exposure limited to `health,info` (gateway also exposes `gateway`). Do not add
  `httpexchanges`/`httptrace`.
- **Validation:** annotate `@RequestBody` with `@Valid` whenever the DTO carries jakarta-validation
  constraints; enforce security-relevant checks in both annotations and the service layer.
- **Database migrations:** Hibernate runs `ddl-auto=update`, which does **not** update CHECK
  constraints or enum value lists — ship the matching `ALTER` statement when you add an enum value
  or tighten a constraint.
- **Account deletion:** ms-users anonymizes the profile, revokes sessions, deletes published
  articles, and asks ms-contracts to remove open owner-side contracts (with payments/PDFs).
  Completed/cancelled and receiver-side contracts are retained.
- **License:** root `package.json` declares `ISC`. > TODO: confirm intended project license.

## Agent Guardrails

- **Never touch:** `frontend/node_modules/`, build outputs (`frontend/dist/`, `backend/**/target/`),
  the `.git/` directory, or any real `.env` / secret files.
- **Do not weaken security defaults:** removing a `ValidationErrorHandler`, flipping
  `include-message`/`include-stacktrace` on in non-dev profiles, broadening actuator exposure, or
  raising web logging to `DEBUG` are release-blocking changes — refuse or flag, don't apply silently.
- **Respect service boundaries:** when a change crosses a service (e.g. a new `/internal/**`
  endpoint), update the calling service's HTTP client in the same change and re-check `SecurityConfig`
  plus gateway routes.
- **Required reviews:** changes to auth, payments, KYC, contracts, or any secret-bearing DTO warrant
  human review before merge.
- **Branch/PR conventions:** work on feature branches (history shows
  `<issue#>-<slug>` branches merged via PR into `main`); use Conventional Commit messages.
- > TODO: no automated rate limits or CODEOWNERS configured.

## Extensibility Hooks

- **Frontend env vars:** `VITE_API_URL` (defaults to `http://localhost:8080/api`).
- **Gateway routing:** add a new service by appending a route under
  `backend/api-gateway/src/main/resources/application.yml` (`MS_<NAME>_URL` env var + `Path=` predicate).
- **Backend env vars (per service):** `SPRING_DATASOURCE_URL`, `JWT_SECRET`, `*_INTERNAL_KEY`,
  `*_BASE_URL` / `MS_*_URL` for inter-service clients, `OPENSEARCH_URIS` (catalog),
  `KYC_UPLOAD_DIR` / `DEVICE_COOKIE_SECURE` (users), `MAIL_*` (notifications),
  `CORS_ALLOWED_ORIGINS` (gateway).
- **Spring profiles:** `dev` / `local` relax error detail for development; activate via
  `SPRING_PROFILES_ACTIVE`.
- **Email templates:** add HTML under
  `backend/ms-notifications/src/main/resources/templates/email/`.
- **OTP delivery:** ms-contracts selects an `OtpDeliveryChannel` implementation
  (`EmailOtpDelivery`, `HttpOtpDelivery`, `LogOtpDelivery`) — add a new channel by implementing the
  interface.

## Further Reading

- [README.md](README.md) — project overview, objectives, setup.
- [CONTRIBUTING.md](CONTRIBUTING.md) — security/runtime conventions and the pre-commit hook.
- [deploy/MIGRATION.md](deploy/MIGRATION.md) — production migration/operations notes.
- [docs/arquitectura/README.md](docs/arquitectura/README.md) — architecture docs. > TODO: placeholder.
- [docs/memoria/README.md](docs/memoria/README.md) — thesis memoria. > TODO: placeholder.
