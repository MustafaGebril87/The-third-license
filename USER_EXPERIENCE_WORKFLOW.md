# User Experience Workflow — The Third License

## Overview

The platform has three user roles with overlapping capabilities:

| Role | Primary Goal |
|---|---|
| **Developer / Contributor** | Push code, earn equity shares |
| **Investor** | Buy and trade shares and coins |
| **Owner / Admin** | Manage repositories, approve contributions |

---

## 1. Authentication

### Register (`/register`)
- Fill in **Username**, **Email**, **Password** → `POST /auth/register`
- On success: redirected to `/login` after 1.5 s
- Already-logged-in users are redirected to `/dashboard`

### Login (`/login`)
- Enter **Username** + **Password** → `POST /auth/login`
- Response provides `accessToken` and `refreshToken`, both stored in `localStorage`
- JWT is decoded and user state is set in `AuthContext`
- Redirects to `/dashboard`

### Logout
- Navbar button clears both tokens from `localStorage` and resets auth state → redirects to `/login`

---

## 2. Navigation

After login, the **Navbar** is always visible with links to:

- Dashboard
- All Companies / My Companies
- Push Files
- My Shares
- My Tokens
- Marketplace
- Admin Panel
- Logout

All API requests automatically carry `Authorization: Bearer <token>` via the Axios interceptor in `src/api/axios.js`.

---

## 3. Company & Repository Management

### Browse All Companies (`/companies/all`)
- Lists every company on the platform with their repositories
- Any user can **Request Access** to a repository → `POST /contributions/{repoId}/request-access`
- Request stays in a *pending* state until the owner acts on it

### My Companies (`/companies`)
- Shows companies the user owns or contributes to
- **Create a company:** click "Open New Company" → `POST /companies/open`
- For each repository:
  - **Clone** → `POST /companies/{repoId}/clone`
  - **Pull** → `GET /contributions/pull-file?repositoryId=…&branch=…&filePath=…&mode=pull`
    - If the pull triggers a merge conflict (HTTP 409/500), an alert is shown and a merge request is automatically created on the backend

---

## 4. Contribution (Push) Workflow

### Push Files (`/push`)
1. Select a repository from the dropdown (populated from owned/accessible repos)
2. Enter a **branch name**
3. Attach one or more files
4. Write a **commit message**
5. Click **Push Files** → `POST /contributions/push-files` (multipart)

**Outcomes:**
- **Success:** "Files pushed successfully!" — the contribution enters a *pending* state in the admin queue
- **Merge conflict (409):** Automatically redirected to `/merge-conflict?repositoryId=…&branch=…`
- **Other error:** Error message displayed inline

### Merge Conflict Resolver (`/merge-conflict` or `/merge/files`)
Triggered either by a failed push or by the owner clicking **Merge** in the admin panel.

1. Each conflicting file is shown with three panes:
   - **Base** (read-only)
   - **Branch** (read-only)
   - **Merged** (editable)
2. Use **"Use Base"** or **"Use Branch"** for quick resolution, or edit manually
3. Click **Submit Merge** → `POST /contributions/merge-branch`
   - `mergeType: MERGE_REQUEST` if user is the repo owner
   - `mergeType: PULL_CONFLICT` if user is a contributor

After submission, redirects to `/admin/dashboard`.

---

## 5. Admin / Owner Workflow (`/admin`)

The admin panel has two sections:

### Pending Contributions
- Lists files awaiting review (filename + contributor)
- **Approve** → `POST /contributions/{id}/approve` — grants an equity share to the contributor
- **Decline** → `POST /contributions/{id}/decline`
- **Merge** → navigates to the Merge Conflict Resolver

### Pending Access Requests
- Lists users requesting access to repositories
- **Approve** → `POST /contributions/requests/{id}/approve`
- **Decline** → `POST /contributions/requests/{id}/decline`

---

## 6. Shares & Equity (`/shares`)

Once a contribution is approved, the contributor receives a **share** (equity stake in the repository).

From the **My Shares** page a user can:

| Action | How | API |
|---|---|---|
| Split a share | Enter a percentage → click Split | `POST /shares/{shareId}/split?percentage=…` |
| List for sale | Enter a price → click Mark For Sale | `POST /shares/{shareId}/mark-for-sale?price=…` |
| Delist | Click Unmark | `POST /shares/{shareId}/unmark-for-sale` |

---

## 7. Currency & Marketplace

### My Tokens (`/tokens`)
- Displays currency tokens the user holds (token ID, available balance)
- **Create a coin offer** to sell coins on the marketplace:
  - Enter **Coins to offer** and **Price per coin** → `POST /coin-market/offer?tokenId=…&coinAmount=…&pricePerCoin=…`

### Marketplace (`/pages/Currency/MarketplaceCoins`)

Two sections:

**Shares for Sale**
- Lists shares listed by other users (company, percentage, price, seller)
- **Buy Share with Tokens** → `POST /shares/buy?shareId=…&price=…` (immediate, spends tokens)

**Coin Offers**
- Lists coin sell orders (amount, price/coin, total USD, seller)
- Buying coins is a **two-step PayPal flow** (see below)

---

## 8. PayPal Top-Up Flow

Used when a user needs coins to buy a marketplace offer.

### Step 1 — Initiate (`/buy-coins` or from Marketplace)
- Enter coin amount (1 coin = $1, plus 1% fee) → `POST /paypal/topup/create?coinAmount=…&returnUrl=…&cancelUrl=…`
- Backend returns a PayPal `approvalUrl`; the browser is redirected to PayPal

Before redirecting, the app saves to `localStorage`:
- `pendingTopUpUsd`
- `pendingTopUpOfferId`

### Step 2a — Success (`/paypal/success`)
- PayPal redirects back with an `orderId` query param
- Page immediately calls `POST /paypal/topup/capture?orderId=…`
- On success: "Wallet topped up successfully!" with links to **My Tokens** and **Marketplace**
- `localStorage` keys are cleared

### Step 2b — Cancel (`/paypal/cancel`)
- Shows "You cancelled the PayPal checkout"
- Links: **Try again** (`/buy-coins`) or **Back to Marketplace**

---

## 9. Complete User Journey by Role

### Developer (Contributor)
```
Register → Login → Browse /companies/all → Request Access
→ Wait for owner approval
→ Clone repo, Push files (/push)
→ Resolve conflicts if needed (/merge-conflict)
→ Wait for contribution approval
→ Receive equity share → View in /shares
→ Split or sell share on marketplace
```

### Investor
```
Login → Browse Marketplace
→ Buy a share with existing tokens (immediate)
  OR
→ Top up wallet via PayPal (/buy-coins → PayPal → /paypal/success)
→ Buy coin offer → receive tokens
→ Use tokens to buy shares
```

### Owner / Admin
```
Login → /companies → Create company
→ Review /admin for access requests → Approve/Decline
→ Review /admin for pending contributions → Approve / Decline / Merge
→ Resolve merge conflicts (/merge-conflict) when needed
→ Approved contributions automatically grant shares to contributors
```

---

## Route Reference

| Route | Page | Auth Required |
|---|---|---|
| `/` | Landing | No |
| `/register` | Register | No |
| `/login` | Login | No |
| `/dashboard` | Dashboard | Yes |
| `/companies/all` | All Companies | No |
| `/companies` | My Companies | Yes |
| `/push` | Push Files | Yes |
| `/merge-conflict` | Merge Conflict Resolver | Yes |
| `/merge/files` | Merge Conflict Resolver | Yes |
| `/shares` | My Shares | Yes |
| `/tokens` | My Tokens | Yes |
| `/pages/Currency/MarketplaceCoins` | Marketplace | Yes |
| `/buy-coins` | Buy Coins (PayPal) | Yes |
| `/paypal/success` | PayPal Success | No |
| `/paypal/cancel` | PayPal Cancel | No |
| `/admin` | Admin Dashboard | Yes |
