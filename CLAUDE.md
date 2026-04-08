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
```

### Backend (`the-third-license-backend/`)

```bash
mvn clean install              # Build
mvn spring-boot:run            # Run on localhost:8080
mvn test                       # Run all tests
mvn test -Dtest=ClassName      # Run a single test class
mvn test -Dtest=ClassName#methodName  # Run a single test method
```

## Architecture

### Monorepo Structure

- `the-third-license-frontend/` — React 18 + Vite
- `the-third-license-backend/` — Spring Boot 3.2 (Java 17), base package `com.thethirdlicense`

### Frontend

- **Routing:** `src/App.jsx` — all routes defined here. Most routes are wrapped in `<PrivateRoute>` which redirects to `/login` if no auth token.
- **Auth state:** `src/context/AuthContext.jsx` — stores `authToken`, `refreshToken`, and decoded `user` (from JWT) in React state + `localStorage`. Exposes `login(token, refresh)` and `logout()`.
- **HTTP:** `src/api/axios.js` — single Axios instance that auto-attaches `Authorization: Bearer <token>` from `localStorage` on every request. All API calls go through this instance.
- **Page groups:** `Auth/`, `Dashboard/` (companies + file push), `Shares/`, `Currency/` (tokens + marketplace), `Admin/`, `paypal/`, `stripe/`, `Profile/`
- **Merge conflict UI:** `src/Merge/MergeConflictResolver` — three-pane editor (Base / Branch / Merged) for resolving Git conflicts

### Backend

Layered architecture: `Controller → Service → Repository (JPA)`

- **Base package:** `com.thethirdlicense`
- **Security:** Stateless JWT via `security/AuthFilter` (extends `OncePerRequestFilter`). `JWTUtil` signs tokens using HS256. Access tokens embed `userId` (UUID) as `sub`, plus `username` and `email` claims. Only `/api/auth/**` is public; all other endpoints require a valid token.
- **Token storage:** JWT tokens are stored client-side in `localStorage`. `RefreshToken` records are persisted in the DB via `RefreshTokenService`. Revoked tokens tracked in `RevokedToken`.
- **Git integration:** `CompanyService` initializes bare Git repos at `C:\repos\origin\<companyName>.git` on the server. `GitService` handles push, clone, pull, and merge operations via JGit against local bare repos (no external credentials required).
- **Database:** PostgreSQL at `localhost:5432/the_third_license_db`. Credentials: `postgres / root`. Schema managed by Hibernate (`ddl-auto: update`).
- **Payments:** Both PayPal SDK (sandbox) and Stripe are integrated. PayPal top-up is a two-step create+capture flow. Stripe uses a checkout session flow with `/stripe/success` and `/stripe/cancel` redirect pages.
- **Currency:** `UserCurrency` tracks token balances. `CoinMarketService` manages marketplace coin offers (`CoinOffer`). `TokenService` and `TokenTopUpService` back wallet operations.
- **Contributions & Shares:** When a contribution is approved via `ContributionService`, `ShareService` automatically grants an equity `Share` to the contributor. Shares can be split or listed for sale on the marketplace.

### Key Backend Packages

| Package | Purpose |
|---|---|
| `controllers/` | REST endpoints. Note: some DTOs (e.g. `AccessRequestDto`, `ContributionDto`, `ShareDTO`) live directly in this package rather than in `requests/`/`responses/`. |
| `services/` | Business logic |
| `models/` | JPA entities. Git repository entity is named `Repository_` to avoid clash with Spring's `Repository`. |
| `repositories/` | Spring Data JPA interfaces |
| `security/` | `JWTUtil`, `AuthFilter`, `UserPrincipal` |
| `config/` | `SecurityConfig` (CORS allows `localhost:5173`), `WebConfig`, `PayPalConfig`, `StripeConfig` |
| `requests/` / `responses/` | Some DTOs — not exhaustive, check `controllers/` too |
| `Util/` | Utility classes |

### Key Data Model Relationships

- `User` owns many `Company` entities; a `Company` has many `Repository_` entities
- `Contribution` links a `User` to a `Repository_` with a `ContributionStatus` (PENDING / APPROVED / DECLINED)
- `Share` links a `User` to a `Company`, representing an equity percentage
- `UserCurrency` tracks a user's coin/token balance separately from the fiat `balance` (BigDecimal) on `User`

### Auth Flow Detail

1. `POST /api/auth/login` → returns `{ accessToken, refreshToken }`
2. Frontend stores both in `localStorage`, decodes the JWT for user state
3. `AuthFilter` validates every subsequent request and sets `UserPrincipal` in the `SecurityContextHolder`
4. Controllers extract the current user via `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`

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
