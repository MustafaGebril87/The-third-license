# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"The Third License" (Kooshk) is a full-stack platform for contribution management and equity sharing. It integrates Git repository management (via JGit), PayPal/Stripe payments, and a share marketplace where users buy/sell equity shares directly with Stripe.

## Commands

### Frontend (`the-third-license-frontend/`)

```bash
npm install          # Install dependencies
npm run dev          # Dev server at localhost:5173
npm run build        # Production build
npm run test         # Run tests once (Vitest)
npm run test:watch   # Run tests in watch mode
```

### Backend (`the-third-license-backend/`)

```bash
mvn clean install              # Build
mvn spring-boot:run            # Run on localhost:8080
mvn test                       # Run all tests
mvn test -Dtest=ClassName      # Run a single test class
mvn test -Dtest=ClassName#methodName  # Run a single test method
```

> **Note:** All backend tests (unit, integration, scenario) run against the real PostgreSQL instance — there is no in-memory substitute. The DB must be running before `mvn test`.

### Backend test layers

| Layer | Package | Annotation | What it tests |
|---|---|---|---|
| **Unit** | `controllers/`, `services/` | `@ExtendWith(MockitoExtension.class)` | Single class in isolation; all dependencies mocked with Mockito. No Spring context, no DB. |
| **Integration** | `integration/` | `@SpringBootTest(RANDOM_PORT)` | Single endpoint or service against the real DB. External services (Stripe, etc.) are `@MockBean`-ed. |
| **Scenario** | `scenarios/` | `@SpringBootTest(RANDOM_PORT)` | End-to-end multi-step flows with a named "cast of characters". Data is seeded directly in `@BeforeEach` and torn down in `@AfterEach`. |

**Test isolation:** every test class seeds data with a `UUID.randomUUID().toString().substring(0, 8)` suffix on usernames and company names to avoid unique-constraint collisions. Teardown deletes in FK-safe order (child records first). Integration/scenario tests use `TestRestTemplate` configured with Apache HttpClient 5 to suppress 401-triggered retry exceptions. Auth tokens are generated directly via `JWTUtil.generateToken()` — no login round-trip.

## Architecture

### Monorepo Structure

- `the-third-license-frontend/` — React 18 + Vite
- `the-third-license-backend/` — Spring Boot 3.2 (Java 17), base package `com.thethirdlicense`

### Frontend

- **Routing:** `src/App.jsx` — all routes defined here. Most routes are wrapped in `<PrivateRoute>` which redirects to `/login` if no auth token.
- **Auth state:** `src/context/AuthContext.jsx` — stores `user` (`{ id, username, email, roles }`) in React state only (no localStorage). On mount it calls `GET /api/users/me` to restore the session from cookies (exposes `loading` until done; `PrivateRoute` waits on it). Exposes `login(userData)` and `logout()`. Tokens live in HttpOnly cookies managed by the browser.
- **HTTP:** `src/api/axios.js` — Axios instance with `baseURL=http://localhost:8080/api` and `withCredentials: true`. A response interceptor handles 401s by calling `POST /auth/refresh` once and retrying; if refresh fails it notifies `AuthContext` to log out. A duplicate instance exists at `src/utils/axiosInstance.js` with the same config (legacy tech debt — prefer `src/api/axios.js`).
- **Dev proxy:** `vite.config.js` proxies `/api` → `http://localhost:8080` for local development.
- **Layout:** `src/layout/Layout.jsx` — shared shell wrapping authenticated pages.
- **Page groups:** `Auth/`, `Dashboard/` (companies + file push + company search), `Shares/`, `Admin/`, `paypal/`, `stripe/`
- **Frontend tests:** colocated alongside source files (e.g., `Login.test.jsx` next to `Login.jsx`), using Vitest + `@testing-library/react`.
- **Company search:** `Dashboard.jsx` contains a live search box (300 ms debounce) that calls `GET /api/companies/search?q=...`. Results include `repositoryId` when a repo exists so the frontend can request access in a single flow.
- **Merge conflict UI:** `src/Merge/MergeConflictResolver.jsx` — three-pane editor (Base / Branch / Merged) for resolving Git conflicts.

### Backend

Layered architecture: `Controller → Service → Repository (JPA)`

- **Security:** Stateless JWT via `security/AuthFilter` (extends `OncePerRequestFilter`). `JWTUtil` signs tokens with HS256 and tags them with a `typ` claim (`access` / `refresh`). `AuthFilter` reads the JWT first from the `access_token` HttpOnly cookie, then falls back to the `Authorization: Bearer` header, and accepts only non-revoked **access** tokens. Only `/api/auth/**` is public. Admin-only endpoints use `@PreAuthorize("hasRole('ADMIN')")`. Controllers must check resource ownership themselves (e.g. `ShareService` checks the share owner; `GitService.hasAccess` / `isCompanyOwner` for repositories).
- **Token storage:** JWT tokens are issued as HttpOnly, `SameSite=Lax`, `Secure` (`app.cookie.secure`, env `COOKIE_SECURE`) cookies (`access_token`, `refresh_token`) by `AuthController`. `POST /api/auth/refresh` rotates the refresh token. Logout and rotation add a SHA-256 hash of the token to the `revoked_token` table via `RevokedTokenService`. `LoginAttemptService` blocks a username+IP after 5 failed logins for 15 minutes (in-memory, per instance). Because cookies are `SameSite=Lax`, the frontend and API must be on the same site.
- **Git integration:** All on-disk paths come from `services/GitWorkspace` — never build Git paths by hand. Layout under `${git.repos.base-path}` (env: `GIT_REPOS_BASE_PATH`, default `/repos`): `origin/<company>.git` (bare repos), `cloned/<repo>-<username>` (contributor working copies), `owner-clones/<repo>` (owner's merge copy). Any user-supplied file path must go through `GitWorkspace.resolveInside(root, path)` (rejects `..`, absolute paths, `.git`). Company names and usernames are restricted to `[A-Za-z0-9._-]` because they become directory names. Contributors push only to feature branches (`GitWorkspace.requirePushableBranch` rejects `main`/`master`); `main` changes only through the owner's merge. On AWS the base path should be an EFS mount. Locally set `git.repos.base-path=C:/repos` in `application-local.properties`.
- **Database:** PostgreSQL at `localhost:5432/the_third_license_db`. Credentials: `postgres / root`. Schema is managed by **Flyway** in production (`ddl-auto=none`); Flyway is disabled locally and in tests so Hibernate's `ddl-auto=update` keeps managing the schema. The Flyway baseline is `db/migration/V1__initial_schema.sql`.
- **Payments (Stripe Connect):** Sellers onboard to a Stripe **Express** connected account (`PayoutService`, `/api/payouts/{status,onboard,dashboard}`, frontend `PayoutsPanel` + `/payouts/return`, `/payouts/refresh`). A share can be listed only once `User.payoutsEnabled` is true, i.e. the account has `payouts_enabled` and an active `transfers` capability. Share purchases are **destination charges**: the buyer pays price + `app.marketplace.fee-percent`, the price is transferred to the seller's account, and the fee stays with the platform. Note that Stripe's processing fees are deducted from the platform's balance. Success/cancel URLs are built server-side from `app.cors.allowed-origin`. Purchases complete from either the buyer's `POST /shares/buy/stripe/confirm` or the signed webhook `POST /api/stripe/webhook` (`checkout.session.completed`; `account.updated` keeps payout status in sync). Completion is idempotent: it row-locks the purchase and the share, and re-checks that the share is still for sale by the same seller at the same price; otherwise it refunds (reversing the transfer and the application fee) and marks the purchase `CANCELLED` (HTTP 409). All Stripe SDK calls go through `StripeService` so they can be mocked.
- **Contributions & Shares (unit ledger):** Equity is tracked in **units**, and `Share.units` is the source of truth. `Company.totalUnits` is the total issued, and `Share.percentage` is a cached `units / totalUnits × 100` kept in sync by `ShareService.syncPercentages`. When a company is opened, the founder is issued `app.equity.initial-owner-units` (default 1,000,000 = 100%). Approving a contribution (`ContributionService.approveContribution`, which sets status `ACCEPTED`; approval can happen only once) issues `modifiedLines × app.equity.units-per-line` (default 100) new units to the contributor, which dilutes everyone proportionally. Units are never overwritten: trades move whole holdings, and splits move units into a new holding. `EquityBackfill` converts companies created under the old percentage model (`totalUnits == 0`) at startup.
- **Company search:** `GET /api/companies/search?q=...` — case-insensitive partial match via `CompanyRepository.findByNameContainingIgnoreCase`. Returns `[{id, name, owner, repositoryId?}]`.
- **External API:** `ExternalAPIController` + `ExternalClient` model support third-party API access to the platform.

### Key Backend Packages

| Package | Purpose |
|---|---|
| `controllers/` | REST endpoints. **Most DTOs live here** (e.g. `AccessRequestDto`, `ContributionDto`, `ShareDTO`, `MergeResolveRequest`). |
| `services/` | Business logic |
| `models/` | JPA entities. Git repository entity is `Repository_` to avoid clash with Spring's `Repository`. |
| `repositories/` | Spring Data JPA interfaces |
| `security/` | `JWTUtil`, `AuthFilter`, `UserPrincipal` |
| `config/` | `SecurityConfig` + `WebConfig` (CORS origin from `app.cors.allowed-origin` / `ALLOWED_ORIGIN` env var), `PayPalConfig`, `StripeConfig` |
| `requests/` | `LoginRequest`, `RegisterRequest` |
| `responses/` | `AuthResponse` |
| `Util/` | Utility classes |
| `exceptions/` | Custom exceptions (`UsernameAlreadyExistsException`, `UserNotFoundException`, etc.) |

### Key Data Model Relationships

- `User` owns many `Company` entities; a `Company` has many `Repository_` entities
- `Contribution` links a `User` to a `Repository_` with a `ContributionStatus` (PENDING / ACCEPTED / REJECTED)
- `Share` links a `User` to a `Company`, representing an equity percentage; shares can be listed for sale and purchased via Stripe
- `MergeRequest` tracks branch merge state with `MergeRequestStatus`
- `User.balance` (BigDecimal) holds fiat balance topped up via PayPal or Stripe

### Auth Flow Detail

1. `POST /api/auth/login` → sets `access_token` and `refresh_token` as HttpOnly cookies; returns `{ id, username, email, roles }`
2. Frontend stores only user info in React state (`AuthContext`) and restores it on reload via `GET /api/users/me`; cookies are sent automatically on every request via `withCredentials: true`. On a 401, the axios interceptor calls `POST /api/auth/refresh` and retries
3. `AuthFilter` extracts the JWT from the `access_token` cookie (or `Authorization` header) and sets `UserPrincipal` in `SecurityContextHolder`
4. Controllers extract the current user via `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
5. `POST /api/auth/logout` → revokes both tokens and clears both cookies

### Merge Conflict Flow

1. A push (`POST /contributions/push-files`) or pull that hits a conflict returns HTTP 409
2. Frontend redirects to `/merge-conflict?repositoryId=…&branch=…`
3. `MergeConflictResolver` renders the three-pane editor; user resolves each file
4. `POST /contributions/merge-branch` with `mergeType: MERGE_REQUEST` (owner) or `PULL_CONFLICT` (contributor)

## Prerequisites

- Node.js, Java 17, Maven
- PostgreSQL on `localhost:5432`, database `the_third_license_db`, user `postgres`, password `root`
- PayPal sandbox credentials and Stripe test keys (configured in `application.properties`)
- Git bare repos are written to `${GIT_REPOS_BASE_PATH}/origin/`. Locally this defaults to `/repos`; override in `application-local.properties` with `git.repos.base-path=C:/repos`.

## Environment Variables (Backend)

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | — | Required; HS256 signing key |
| `JWT_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Refresh token TTL (7 days) |
| `STRIPE_SECRET_KEY` | — | Required for Stripe features |
| `DB_URL` | `localhost:5432/the_third_license_db` | |
| `DB_USERNAME` | `postgres` | |
| `DB_PASSWORD` | — | Required |
| `ALLOWED_ORIGIN` | `http://localhost:5173` | Production frontend URL for CORS |
| `GIT_REPOS_BASE_PATH` | `/repos` | Base dir for bare + cloned repos; use EFS mount on AWS |
| `STRIPE_WEBHOOK_SECRET` | — | Required for the webhook (`whsec_…` from the Dashboard or `stripe listen`) |
| `STRIPE_CONNECT_COUNTRY` | `US` | Country for new sellers' Express accounts |
| `MARKETPLACE_FEE_PERCENT` | `1.0` | Platform fee added on top of the share price |
| `COOKIE_SECURE` | `true` | Set `false` only for non-HTTPS, non-localhost environments |
| `EQUITY_INITIAL_OWNER_UNITS` | `1000000` | Founder units when a company is opened |
| `EQUITY_UNITS_PER_LINE` | `100` | Units issued per changed line in an approved contribution |

Use `application-local.properties` (git-ignored) with `--spring.profiles.active=local` for local overrides without modifying the committed config.

## Schema Management

Production uses **Flyway** (`spring.flyway.enabled=true`, `ddl-auto=none`). A fresh AWS RDS instance is fully bootstrapped by `db/migration/V1__initial_schema.sql`. Future schema changes must be added as versioned migrations (`V2__...sql`, etc.).

Locally and in tests, Flyway is disabled and Hibernate's `ddl-auto=update` manages the schema — no migration files needed for local development.

**Never** re-enable `ddl-auto=update` or `create-drop` in `application.properties`; those settings only belong in `application-local.properties` and `src/test/resources/application.properties`.
