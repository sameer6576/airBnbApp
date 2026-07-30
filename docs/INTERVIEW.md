# airBnbApp — SDE2 interview guide

One document for walking through this project in an interview: what it is, how it is built, the hard problems, tradeoffs, and what you would do next. For a single end-to-end explanation of the whole system (setup through ops), see [COMPLETE_GUIDE.md](COMPLETE_GUIDE.md). Pair with [DOMAIN](DOMAIN.md), [ARCHITECTURE](ARCHITECTURE.md), [FLOWS](FLOWS.md), and [API](API.md) when you need depth on one topic.

---

## 1. Elevator pitch (30–60s)

**airBnbApp** is a full-stack hotel booking system: managers list hotels and rooms, guests search and book with soft inventory holds, and Stripe Checkout confirms payment. The API is Java 21 / Spring Boot 4.1 / Postgres / JWT; the UI is React + Vite (**Stayline**).

The interesting engineering is not CRUD — it is **concurrency-safe inventory**, **time-bounded reservations**, **payment webhooks that can arrive late or twice**, and keeping **search price** aligned with **checkout amount**.

---

## 2. Problem statement

Build a marketplace-style booking backend (plus SPA) where:

| Actor | Needs |
|-------|--------|
| Guest | Search by city/dates, reserve briefly, pay, cancel, review after stay, wishlist |
| Hotel manager | CRUD hotels/rooms, activate listing, close/surge inventory, see bookings & simple analytics |
| Admin | Promote users to managers |

Constraints that drive design:

- Two guests must not oversell the same room-night.
- Unpaid holds cannot block inventory forever.
- Payment confirmation is async (Stripe webhook), not the HTTP response from “pay”.
- Check-out date is exclusive (nights billed ≠ calendar span).
- Secrets and env-specific config must not live in the committed shared properties.

---

## 3. High-level architecture

```
Browser (Stayline)
    │  Bearer access JWT + refresh cookie
    ▼
Spring Controllers  (/api/v1)
    ▼
Services (transactions, ownership, booking/inventory)
    ▼
JPA Repositories + Postgres
    │
    ├── Stripe Checkout + webhook
    ├── Pricing strategy chain (hourly job)
    └── Schedulers (expiry, horizon, pricing)
```

**Layers:** Controller validates/maps → Service owns business rules and `@Transactional` → Repository JPQL + `PESSIMISTIC_WRITE` locks. Cross-cutting: JWT filter, `ApiResponse` wrapper, `GlobalExceptionHandler`.

**Why this shape?** Clear ownership of the booking state machine in one service, testable without HTTP, and locks stay next to the inventory updates that need them.

---

## 4. Domain model (talking points)

```
User ──owns──► Hotel ──► Room ──► Inventory (per date)
User ──► Booking (hotel, room, dates, amount, status, holdExpiresAt)
Booking ◄──► Guest (M:N)
HotelMinPrice (denormalized daily min for search)
ProcessedStripeEvent (webhook idempotency)
```

**Inventory row** is the unit of sale:

- `totalCount` — capacity that day  
- `bookedCount` — paid  
- `reservedCount` — soft hold  
- `closed` / `surgeFactor` / `price` (stored nightly sell price)

**Available** ≈ `totalCount - bookedCount - reservedCount` (if not closed).

**Booking statuses:**

```
RESERVED → GUEST_ADDED (optional) → PAYMENT_PENDING → CONFIRMED
                ↘ EXPIRED / CANCELLED (unpaid)
CONFIRMED → CANCELLED (policy refund)
Late pay after hold lost → REFUNDED (auto Stripe refund)
```

**Dates:** stay queries use `[checkIn, checkOut)`; admin windows use inclusive `[start, end]`. Mixing them is a classic off-by-one bug — the repository is split and named to make the convention obvious.

---

## 5. Core flow: book and pay

1. Guest searches (`POST /hotels/search`) — availability on inventory + price from `HotelMinPrice`.
2. `POST /bookings/init` — lock available nights, bump `reservedCount`, create `RESERVED` with `holdExpiresAt` (~10 min). Optional `Idempotency-Key`.
3. Optional guests → `GUEST_ADDED`.
4. `POST .../payments` — Stripe Checkout Session for `booking.amount`, `expires_at` pinned to hold (~30 min), status `PAYMENT_PENDING`.
5. Stripe webhook `checkout.session.completed` — verify signature, dedupe event id, amount check, move reserved → booked, `CONFIRMED`.
6. If hold already expired when money arrives — try re-acquire; else full refund → `REFUNDED`.

**Why soft hold + expiry job?** UX needs time to pay without overselling. Expiry must run at least as often as the shortest hold, or the catalog looks sold out after holds die.

---

## 6. Concurrency & correctness (expect deep questions)

### Oversell prevention

- Init / date change: `findAndLockAvailableInventory` with pessimistic write locks on the stay range.
- Bulk inventory updates return **row counts**; partial updates roll back (assert nights covered).
- Confirm / cancel use `lockStayRange` and update reserved/booked without requiring `closed=false` on confirm (manager closing mid-checkout should not strand a paid guest incorrectly — discuss tradeoff).
- Lowering room `totalCount` is rejected if any future night already has `booked + reserved > newTotal`.

### Idempotency

| Surface | Mechanism |
|---------|-----------|
| Booking init | Optional key → `userId:key` + request fingerprint; conflict on reuse/mismatch/terminal booking |
| Stripe webhook | `ProcessedStripeEvent` by event id |
| Stripe refunds | Idempotency key scoped to booking |

### Payment edge cases

- Session `expires_at` aligned with hold so Stripe’s 24h default does not outlive inventory.
- `client_reference_id` fallback if session id never stored.
- Amount mismatch → do not blindly confirm.
- Null `holdExpiresAt` still expires via `createdAt` fallback in the expiry query.

### Pricing consistency

- **Write path:** strategy chain from `room.basePrice` → `inventory.price` (rounded 2dp).
- **Read/pay path:** sum stored `inventory.price` — never recompute live at checkout (would diverge from search).
- Do **not** feed stored price back into the strategy each hour (would compound multipliers).

---

## 7. Auth & security (SDE2 checklist)

- Access JWT in `Authorization` header; refresh in HttpOnly Secure cookie, path `/api/v1/auth`.
- `typ` claim separates access vs refresh tokens.
- Logout clears refresh cookie with matching attributes.
- Role hierarchy: `GUEST` / `HOTEL_MANAGER` / `ADMIN`; ownership checks by **user id**, not entity `equals` (Hibernate proxies).
- Mass-assignment: no client-controlled ids on signup; hotel update mapping skips server fields; password `@JsonIgnore`.
- Public hotel info only for **active** hotels.
- Room/booking IDOR: always verify ownership / booking owner before mutate.
- Config: secrets in gitignored `application-dev.properties`; prod via env vars; example template committed.
- Actuator: health (+ db readiness) only, not full actuator surface.

**Honest gaps for interview:** no refresh-token rotation/denylist, no rate limiting, JWT secret historically burned in samples (rotate), no ShedLock for multi-instance schedulers.

---

## 8. Schedulers & ops

| Job | Purpose |
|-----|---------|
| Booking expiry | Free `reservedCount`, mark `EXPIRED` |
| Expiry warning | Notify ~2 min before hold ends |
| Pricing update | Reprice inventory + refresh `HotelMinPrice` (per-hotel tx) |
| Inventory horizon | Keep ~365 days of rows; idempotent fill of missing dates |

Crons are properties; set `-` to disable. Dev profile quiets pricing/warning noise but keeps expiry on.

**Single-instance assumption** — no distributed lock. Two replicas would double-run pricing/expiry (usually “eventually ok” for expiry, wasteful/dangerous for writes at scale).

Health: `GET /api/v1/actuator/health` for local and k8s-style probes.

---

## 9. Frontend (Stayline)

React + Vite + TypeScript. Calls `/api/v1`, stores access token, relies on refresh cookie, clears auth on 401, logout hits API. Surfaces hold countdown via `holdExpiresAt`, `REFUNDED` messaging, guest age/gender, and date rules aligned with exclusive check-out.

Stripe confirmation still needs CLI webhook forwarding locally.

---

## 10. Tradeoffs & known limitations

Speak to these proactively — interviewers reward self-awareness:

| Decision | Why | Cost |
|----------|-----|------|
| Soft hold + cron expiry | Simple, works on one node | Stale holds until next tick; multi-node needs ShedLock/outbox |
| Stored `inventory.price` for quotes | Search ↔ pay alignment | Price can change after init until you freeze amount at init (we set amount at init from then-current prices) |
| Hibernate `ddl-auto` | Fast for learning | No real migrations (Flyway next) |
| In-memory amenity filter/sort then page | Simple JPQL | Breaks at large result sets |
| Postgres `TEXT[]` | Natural for photos/amenities | H2-hostile; context test disabled |
| Notifications mostly log | Avoid mail setup friction | Not production notification |
| Strategy pattern for pricing | Extensible multipliers | Still synchronous DB-heavy hourly sweep |

---

## 11. “What would you build next?” (strong close)

Prioritize by risk:

1. **Flyway + Testcontainers** — real schema evolution and CI-friendly integration tests.  
2. **ShedLock / leader election** — safe multi-instance schedulers.  
3. **Refresh token rotation + reuse detection** — tighter auth.  
4. **Outbox / reliable webhook processing** — if webhooks grow complex.  
5. **DB-side search pagination** for filters/sorts.  
6. **Rate limits** on auth and booking init.  
7. **Docker Compose** for one-command local (API + Postgres + Stripe mock optional).

---

## 12. Suggested interview walkthrough (10–15 min)

1. Pitch + actors (1 min).  
2. Draw Hotel → Room → Inventory → Booking (2 min).  
3. Deep dive: init booking + locks + exclusive dates (3–4 min).  
4. Payments: Checkout → webhook → dedupe → late pay / `REFUNDED` (3 min).  
5. Pricing write vs read (1 min).  
6. Auth cookies + ownership pitfalls (1 min).  
7. Tradeoffs & next steps (2 min).

**Sample questions to prepare:** see the full set with model answers in [INTERVIEW_QUESTIONS.md](INTERVIEW_QUESTIONS.md). Highlights:

- How do you prevent double booking?  
- What happens if the webhook is delivered twice? Late?  
- Why is check-out exclusive?  
- Why not recompute price at payment time?  
- How does inventory stay a year out without decaying?  
- How would this run on three API replicas?

---

## 13. Quick facts (resume / intro)

- **Backend:** Java 21, Spring Boot 4.1, Spring Security JWT, JPA, Postgres, Stripe, springdoc OpenAPI  
- **Frontend:** React, Vite, TypeScript  
- **Patterns:** layered architecture, pricing Strategy/Decorator chain, soft reservation + expiry, webhook idempotency  
- **Tests:** WebMvc/unit suite green; full context test skipped for Postgres-specific types  
- **Docs:** SETUP, DOMAIN, ARCHITECTURE, FLOWS, API, COMPLETE_GUIDE, INTERVIEW, INTERVIEW_QUESTIONS  

Repo root README links here as the interview-oriented overview.
