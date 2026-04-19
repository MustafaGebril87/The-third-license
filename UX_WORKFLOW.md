# User Experience Workflow

End-to-end user journeys for The Third License (Kooshk). Each section describes what the user sees, what they do, and what the system does in response.

---

## 1. Authentication

### Register
1. User visits `/register`, fills in username / email / password.
2. `POST /api/auth/register` — server validates (username 3–50 chars, password ≥ 8 chars, valid email, no duplicates).
3. On success: redirect to `/login`.
4. On failure: generic `400` message — same wording whether the username or email is taken (no enumeration).

### Login
1. User visits `/login`, enters credentials.
2. `POST /api/auth/login` — server authenticates, sets two `HttpOnly` cookies (`access_token`, `refresh_token`), and returns `{ id, username, email, roles }`.
3. Frontend stores the user object in `AuthContext` (React state only — no `localStorage`). Cookies are managed by the browser and sent automatically on every subsequent request.
4. Redirected to `/dashboard`.

### Logout
1. User clicks Logout in the navbar.
2. `POST /api/auth/logout` — server sets `Max-Age=0` on both cookies.
3. `AuthContext` clears user state. Frontend redirects to `/login`.

---

## 2. Company Discovery (Contributor — Finding a Company)

There are two ways for a user to find companies they do not yet belong to.

### Option A — Dashboard Search (primary path)
1. User is on `/dashboard` and types into the search box.
2. After a 300 ms debounce: `GET /api/companies/search?q=<query>` — case-insensitive partial match across all companies on the platform.
3. Results appear inline: company name + owner + **Request Access** button per row.
4. Clicking **Request Access** calls `POST /api/contributions/{repositoryId}/request-access` using the `repositoryId` embedded in the search result — no second request needed.
5. A pending `AccessRequest` is created in the DB. The button is not shown again for the same company.

### Option B — All Companies page (`/companies/all`)
1. Navigated from the navbar "All Companies" link.
2. `GET /api/companies/all` — lists every company with owner name.
3. For each company the page also loads its first repository (one extra request per company).
4. **Request Access** button calls the same access-request endpoint.

---

## 3. Company Creation (Owner)

1. User goes to `/companies` (My Companies).
2. Clicks **Open New Company**, enters a name, clicks **Create**.
3. `POST /api/companies/open` — server creates the `Company` record and initialises a bare Git repo at `C:\repos\origin\<companyName>.git`.
4. New company appears immediately in the list.

---

## 4. My Companies — Navigating Owned & Joined Companies

Page: `/companies`

The list includes companies where the user is:
- **Owner** — created the company.
- **Contributor** — added to `company_users` via prior contribution approval.
- **Access holder** — has a `RepositoryAccess` record for any of the company's repos.

Per company, clicking **Show Repos** lazy-loads repositories (`GET /api/companies/{companyId}/repositories`).

Per repository, the available actions depend on access status:

| Access Status | Actions visible |
|---|---|
| `NONE` | Request Access |
| `PENDING` | "Access Pending" (disabled) |
| `APPROVED` | Clone, Pull |

- **Clone** → `POST /api/companies/{repoId}/clone` — clones the bare repo to a local working copy.
- **Pull** → `GET /api/contributions/pull-file?repositoryId=...&branch=...&mode=pull` — returns file content. On conflict (409/500) shows an alert that a merge request was sent to the owner for resolution.

---

## 5. Push Files (Contributor Contribution Flow)

Page: `/push`

1. User selects a repository from a dropdown (populated from all their companies' repos), enters a branch name, selects files, and writes a commit message.
2. `POST /api/contributions/push-files` (multipart form).
3. **Happy path:** files are committed to the branch. Status shows success.
4. **Conflict path (409):** frontend redirects to `/merge-conflict?repositoryId=...&branch=...` and the user resolves the conflict manually (see §6).
5. On success, a `Contribution` record (status `PENDING`) is created and appears in the Admin Dashboard for the company owner to review.

---

## 6. Merge Conflict Resolution

Page: `/merge-conflict` (reached automatically on push/pull conflict, or via the Merge button in Admin)

1. `MergeConflictResolver` loads conflict data and renders a three-pane editor: **Base** (common ancestor) / **Branch** (incoming changes) / **Merged** (editable result).
2. User resolves each conflicting file by editing the Merged pane.
3. Submits: `POST /api/contributions/merge-branch` with the resolved content and a `mergeType`:
   - `MERGE_REQUEST` — used by the **owner** merging a contributor's branch.
   - `PULL_CONFLICT` — used by the **contributor** who pulled and got a conflict.

---

## 7. Admin Dashboard (Owner Approval Flows)

Page: `/admin`

The dashboard has two sections, loaded in parallel on mount.

### Pending Contributions
Each row shows filename + contributor username with three actions:
- **Approve** → `POST /api/contributions/{id}/approve` — approves the contribution and triggers `ShareService` to automatically grant an equity `Share` to the contributor.
- **Decline** → `POST /api/contributions/{id}/decline`.
- **Merge** → navigates to `/merge-conflict` for that contribution's branch.

### Pending Access Requests
Each row shows the requesting user's name + the target repository with two actions:
- **Approve** → `POST /api/contributions/requests/{id}/approve` — creates a `RepositoryAccess` record, granting the user clone/pull rights.
- **Decline** → `POST /api/contributions/requests/{id}/decline`.

---

## 8. Shares & Equity

Page: `/shares` (My Shares)

Lists the user's equity shares, showing percentage and company. Per share:

- **Split** — user enters a percentage, clicks Split → `POST /api/shares/{id}/split?percentage=...`. The original share is divided into two.
- **Mark For Sale** — user enters a price, clicks Mark → `POST /api/shares/{id}/mark-for-sale?price=...`. Share becomes visible in the marketplace.
- **Unmark** (visible only when `forSale=true`) → `POST /api/shares/{id}/unmark-for-sale`. Removes it from the marketplace.

---

## 9. Marketplace

Page: `/pages/Currency/MarketplaceCoins` (navigated from Navbar → Marketplace)

Two sections load in parallel on mount.

### Shares for Sale
- Shows shares listed by other users (the viewer's own shares are excluded server-side).
- **Buy Share with Tokens** → `POST /api/shares/buy?shareId=...&price=...`
  - Deducts tokens from the buyer's wallet via `TokenService`.
  - Transfers share ownership to the buyer in DB.
  - Creates a `RepositoryAccess` record — buyer immediately gains access to the company's repo.

### Coin Offers
- Shows coin offers listed by other users.
- **Top up wallet with Stripe** → stores `pendingTopUpOfferId` and `pendingTopUpUsd` in `localStorage`, then calls `POST /api/stripe/topup/create` and redirects the browser to the Stripe checkout URL.
  - **Success:** browser lands on `/stripe/success` → `POST /api/stripe/topup/capture` → tokens credited to wallet → user returns to the platform.
  - **Cancel:** browser lands on `/stripe/cancel` → `localStorage` keys removed, no charge.
- After the wallet is topped up, the user uses their token balance to purchase the coin offer.

---

## 10. Token Wallet

Page: `/tokens` (My Tokens)

Lists the user's currency tokens with `amount` and `tokenValue`.

Per token, the user can create a **coin offer** for the marketplace:
1. Enter the number of coins to offer and a price per coin.
2. Click **Offer** → `POST /api/coin-market/offer?tokenId=...&coinAmount=...&pricePerCoin=...`.
3. The offer appears in the Marketplace Coin Offers section for other users to purchase.

---

## 11. PayPal Top-Up

Alternative to Stripe for adding funds.

1. User initiates top-up via `PayPalTopUpController`.
2. `POST /api/paypal/topup/create` — creates a PayPal order and returns an approval URL.
3. User approves on PayPal → redirected back → `POST /api/paypal/topup/capture` captures the order and credits the wallet.
4. Redirected to `/paypal/success`.

---

## User Role Summary

| Role | Key capabilities |
|---|---|
| **Any authenticated user** | Search companies, request repo access, push files, manage own shares and tokens |
| **Company owner** | Approve/decline contributions and access requests, trigger merges, view pending items in Admin |
| **ADMIN** (platform role) | Access `POST /api/admin/currency/revoke` and other admin-only endpoints |
