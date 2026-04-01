# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"The Third License" (Kooshk) is a full-stack platform for contribution management, equity sharing, and an in-app currency/token system. It integrates Git repository management, PayPal payments, and a coin marketplace.

## Commands

### Frontend (`the-third-license-frontend/`)

```bash
npm install          # Install dependencies
npm run dev          # Dev server at localhost:5173 (proxies /api → localhost:8080)
npm run build        # Production build
npm run preview      # Preview production build
```

### Backend (`the-third-license-backend/`)

```bash
mvn clean install    # Build
mvn spring-boot:run  # Run on localhost:8080
mvn test             # Run tests
mvn test -Dtest=ClassName#methodName  # Run a single test
```

## Architecture

### Monorepo Structure

- `the-third-license-frontend/` — React 18 + Vite
- `the-third-license-backend/` — Spring Boot 3.2 (Java 17)

### Frontend

- **Routing:** React Router in `src/App.jsx` — routes map to page directories under `src/pages/`
- **Auth state:** `src/context/AuthContext.jsx` manages JWT login/logout globally
- **HTTP:** `src/api/axios.js` — Axios instance with JWT bearer token interceptor; all requests go to `VITE_API_BASE_URL` (default `http://localhost:8080/api`)
- **Key page groups:** `Auth/`, `Dashboard/` (companies + file pushing), `Shares/`, `Currency/` (coin marketplace), `Admin/`, `paypal/`
- **Merge conflict UI:** `src/Merge/MergeConflictResolver` — dedicated component for resolving Git conflicts

### Backend

Layered architecture: `Controller → Service → Repository (JPA)`

- **Security:** Spring Security + JWT (JJWT). Tokens expire in 15 min; refresh tokens are stored in DB. See `security/` and `config/SecurityConfig.java`.
- **Git integration:** JGit (`GitService`, `CompanyService`) handles repository operations on behalf of users.
- **Database:** PostgreSQL at `localhost:5432/the_third_license_db`. Schema is auto-managed by Hibernate (`ddl-auto: update`).
- **Payments:** PayPal SDK in sandbox mode (`PayPalService`, `PayPalTopUpController`).
- **Currency/Tokens:** `CoinMarketService` and `TokenService` back the in-app economy.
- **Contributions & Shares:** `ContributionService` and `ShareService` track equity.
- **All REST endpoints** are prefixed with `/api`.

### Key Backend Packages

| Package | Purpose |
|---|---|
| `controllers/` | REST endpoints (~15 controllers) |
| `services/` | Business logic |
| `models/` | JPA entities (note: `Repository_` to avoid clash with Java's `Repository`) |
| `repositories/` | Spring Data JPA interfaces |
| `security/` | JWT utilities, filter chain |
| `config/` | `SecurityConfig`, `WebConfig`, `PayPalConfig` |
| `requests/` / `responses/` | DTOs for API I/O |

## Prerequisites

- Node.js, Java 17, Maven
- PostgreSQL running on `localhost:5432` with database `the_third_license_db`
- PayPal sandbox credentials (configured in `application.properties`)
