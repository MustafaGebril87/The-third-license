# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"The Third License" (Kooshk) is a full-stack platform for contribution management, equity sharing, and an in-app currency/token system. It integrates Git repository management, PayPal/Stripe payments, and a coin marketplace.

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

There are three distinct test layers, each with a different scope and speed:

| Layer | Package | Annotation | What it tests |
|---|---|---|---|
| **Unit** | `controllers/`, `services/` | `@ExtendWith(MockitoExtension.class)` | Single class in isolation; all dependencies mocked with Mockito. No Spring context, no DB. |
| **Integration** | `integration/` | `@SpringBootTest(RANDOM_PORT)` | Single endpoint or service against the real DB. External services (Stripe, etc.) are `@MockBean`-ed. |
| **Scenario** | `scenarios/` | `@SpringBootTest(RANDOM_PORT)` | End-to-end multi-step flows with a named "cast of characters". Data is seeded directly in `@BeforeEach` (bypassing Git/filesystem side-effects) and torn down in `@AfterEach`. |

**Test isolation convention:** every test class seeds data with a `UUID.randomUUID().toString().substring(0, 8)` suffix appended to usernames and company names. This keeps concurrent test runs independent and avoids unique-constraint collisions. Teardown always deletes in FK-safe order (child records first, then parent rows).

## Architecture

### Monorepo Structure

- `the-third-license-frontend/` — React 18 + Vite
- `the-third-license-backend/` — Spring Boot 3.2 (Java 17), base package `com.thethirdlicense`

### Frontend

- **Routing:** `src/App.jsx` — all routes defined here. Most routes are wrapped in `<PrivateRoute>` which redirects to `/login` if no auth token.
- **Auth state:** `src/context/AuthContext.jsx` — stores decoded `user` (`{ id, username, email, roles }`) in React state only (no localStorage). Exposes `login(userData)` and `logout()`. Tokens live in HttpOnly cookies managed by the browser.
- **HTTP:** `src/api/axios.js` — Axios instance with `baseURL=http://localhost:8080/api` and `withCredentials: true`. Cookies are sent automatically; there is no manual `Authorization` header attachment. A duplicate instance also exists at `src/utils/axiosInstance.js` with the same config.
- **Layout:** `src/layout/Layout.jsx` — shared shell wrapping authenticated pages.
- **Page groups:** `Auth/`, `Dashboard/` (companies + file push + company search), `Shares/`, `Currency/` (tokens + marketplace), `Admin/`, `paypal/`, `stripe/`
- **Company search:** `Dashboard.jsx` contains a live search box (300 ms debounce) that calls `GET /api/companies/search?q=...`. Results include `repositoryId` when a repo exists so the frontend can request access in a single flow without a second round-trip.
- **Merge conflict UI:** `src/Merge/MergeConflictResolver.jsx` — three-pane editor (Base / Branch / Merged) for resolving Git conflicts

### Backend

Layered architecture: `Controller → Service → Repository (JPA)`

- **Base package:** `com.thethirdlicense`
- **Security:** Stateless JWT via `security/AuthFilter` (extends `OncePerRequestFilter`). `JWTUtil` signs tokens with HS256. `AuthFilter` reads the JWT first from the `access_token` HttpOnly cookie, then falls back to the `Authorization: Bearer` header. Only `/api/auth/**` is public; everything else requires a valid token.
- **Token storage:** JWT tokens are issued as HttpOnly cookies (`access_token`, `refresh_token`) by `AuthController`. The `RevokedToken` table tracks revoked tokens; `RefreshToken` records are persisted via `RefreshTokenService`.
- **Git integration:** `CompanyService` initializes bare Git repos at `C:\repos\origin\<companyName>.git`. `GitService` handles push, clone, pull, and merge via JGit against local bare repos (no external credentials required).
- **Database:** PostgreSQL at `localhost:5432/the_third_license_db`. Credentials: `postgres / root`. Schema managed by Hibernate (`ddl-auto: update`).
- **Payments:** PayPal SDK (sandbox) uses a two-step create+capture flow (`PayPalTopUpController`). Stripe uses checkout sessions (`StripeTopUpController`) with `/stripe/success` and `/stripe/cancel` redirect pages.
- **Currency:** `UserCurrency` tracks token balances. `CoinMarketService` manages `CoinOffer` marketplace listings. `TokenService` and `TokenTopUpService` back wallet operations.
- **Contributions & Shares:** When a contribution is approved via `ContributionService`, `ShareService` automatically grants an equity `Share` to the contributor. Shares can be split or listed for sale.
- **Company search:** `GET /api/companies/search?q=...` — case-insensitive partial match via `CompanyRepository.findByNameContainingIgnoreCase`. Returns `[{id, name, owner, repositoryId?}]`; `repositoryId` is omitted when the company has no repository.
- **External API:** `ExternalAPIController` + `ExternalClient` model support third-party API access to the platform.

### Key Backend Packages

| Package | Purpose |
|---|---|
| `controllers/` | REST endpoints. **Most DTOs live here** (e.g. `AccessRequestDto`, `ContributionDto`, `ShareDTO`, `MergeResolveRequest`). |
| `services/` | Business logic |
| `models/` | JPA entities. Git repository entity is `Repository_` to avoid clash with Spring's `Repository`. |
| `repositories/` | Spring Data JPA interfaces |
| `security/` | `JWTUtil`, `AuthFilter`, `UserPrincipal` |
| `config/` | `SecurityConfig` (CORS allows `localhost:5173`), `WebConfig`, `PayPalConfig`, `StripeConfig` |
| `requests/` | `LoginRequest`, `RegisterRequest` |
| `responses/` | `AuthResponse` |
| `Util/` | Utility classes (`AuthResponse`, `BalanceResponse`, `TransferRequest`, etc.) |
| `exceptions/` | Custom exceptions (`UsernameAlreadyExistsException`, `UserNotFoundException`, etc.) |

### Key Data Model Relationships

- `User` owns many `Company` entities; a `Company` has many `Repository_` entities
- `Contribution` links a `User` to a `Repository_` with a `ContributionStatus` (PENDING / APPROVED / DECLINED)
- `Share` links a `User` to a `Company`, representing an equity percentage
- `MergeRequest` tracks branch merge state with `MergeRequestStatus`
- `UserCurrency` tracks a user's coin/token balance separately from the fiat `balance` (BigDecimal) on `User`

### Auth Flow Detail

1. `POST /api/auth/login` → sets `access_token` and `refresh_token` as HttpOnly cookies; returns `{ id, username, email, roles }`
2. Frontend stores only user info in React state (`AuthContext`); cookies are sent automatically on every request via `withCredentials: true`
3. `AuthFilter` extracts the JWT from the `access_token` cookie (or `Authorization` header) and sets `UserPrincipal` in `SecurityContextHolder`
4. Controllers extract the current user via `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
5. `POST /api/auth/logout` → clears both cookies server-side

### Merge Conflict Flow

1. A push (`POST /contributions/push-files`) or pull that hits a conflict returns HTTP 409
2. Frontend redirects to `/merge-conflict?repositoryId=…&branch=…`
3. `MergeConflictResolver` renders the three-pane editor; user resolves each file
4. `POST /contributions/merge-branch` with `mergeType: MERGE_REQUEST` (owner) or `PULL_CONFLICT` (contributor)

## Prerequisites

- Node.js, Java 17, Maven
- PostgreSQL on `localhost:5432`, database `the_third_license_db`, user `postgres`, password `root`
- PayPal sandbox credentials and Stripe test keys (configured in `application.properties`)
- Git bare repositories are created on the server filesystem at `C:\repos\origin\`
