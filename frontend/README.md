# Stayline UI

React + Vite frontend for the airBnbApp API.

## Run

1. Start the Spring Boot API on port 8080 (with Postgres + `application.properties`)
2. In local API props set:
   - `frontend.url=http://localhost:5173`
   - `app.cors.allowed-origins=http://localhost:5173` (default)
   - `app.seed.enabled=true` if you want seed users
3. Here:

```bash
npm install
npm run dev
```

Open http://localhost:5173

## Seed logins

| Role | Email | Password |
|------|-------|----------|
| Manager | manager@example.com | Manager@123 |
| Admin | admin@example.com | Admin@123 |

Guests: use Sign up in the UI.

## What works

- Guest: search → hotel → book → optional guests → Stripe pay → my bookings / cancel
- Hold countdown from `holdExpiresAt`; `REFUNDED` / `EXPIRED` messaging; logout clears API refresh cookie
- Manager: create hotel → rooms → activate → view bookings

Confirming payment still needs Stripe CLI:

```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
```

## Env

Copy `.env.example` → `.env` if needed. `VITE_API_BASE` defaults to `http://localhost:8080/api/v1`.
