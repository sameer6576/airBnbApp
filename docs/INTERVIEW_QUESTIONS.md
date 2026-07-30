# Interview questions for airBnbApp

Questions an interviewer (or you practicing aloud) can ask around **this** project. Answers are written for an SDE2 bar: correct for how the codebase works, plus when to mention tradeoffs or “what I’d change.”

Companion: [INTERVIEW.md](INTERVIEW.md) (walkthrough) · [COMPLETE_GUIDE.md](COMPLETE_GUIDE.md) (full system).

---

## How to use this

1. Cover each section once out loud without peeking at answers.  
2. Prefer drawing inventory rows / status transitions on a whiteboard.  
3. Always end hard answers with: *what breaks at scale* or *what I’d ship next*.

---

## A. Elevator & product

### A1. What does this project do in 60 seconds?

**Answer:** Full-stack hotel booking: managers list hotels/rooms and activate inventory; guests search, soft-reserve room-nights, pay with Stripe Checkout, cancel under a policy, wishlist, and review after stay. Backend is Spring Boot + Postgres + JWT; UI is React (Stayline). The non-CRUD work is concurrency-safe inventory, time-bounded holds, webhook idempotency, and search/checkout price alignment.

### A2. Who are the actors and what can each do?

**Answer:** `GUEST` — search, book, pay, cancel unpaid/confirmed, wishlist, review. `HOTEL_MANAGER` — own hotels/rooms, activate, inventory close/surge, bookings/reports. `ADMIN` — manager APIs plus promote users to manager. Hotels only appear in public search/info when `active`.

### A3. Why soft-reserve instead of only decrementing on payment?

**Answer:** Guests need time to complete Stripe Checkout without another user taking the last room. Soft hold bumps `reservedCount`; expiry job (or cancel) releases it. Tradeoff: inventory looks “taken” until the hold dies; expiry job must run at least as often as the shortest hold.

---

## B. Domain & dates

### B1. Is check-out a billed night? How many nights is 10th → 12th?

**Answer:** Check-out is exclusive. Stay occupies the 10th and 11th — **two nights**. Query is `date >= checkIn AND date < checkOut`. Equal dates = zero nights → rejected.

### B2. How do admin “close dates 1st–5th” differ from a stay?

**Answer:** Admin windows are inclusive on both ends (five days). Stay ranges are half-open. Mixing them causes off-by-one oversell or wrong closes. Repository params are named `checkInDate`/`checkOutDate` vs `startDate`/`endDate` to keep that clear.

### B3. Walk the booking status machine.

**Answer:**
```
RESERVED → (optional) GUEST_ADDED → PAYMENT_PENDING → CONFIRMED → CANCELLED
                ↘ EXPIRED / CANCELLED (unpaid)
Late payment after rooms gone → REFUNDED
```
Guests optional. Date changes only while unpaid. Confirmed does not auto-expire.

### B4. What is `REFUNDED` vs `CANCELLED`?

**Answer:** `CANCELLED` is guest-initiated (or cancel API) — unpaid releases hold with $0 refund; confirmed uses policy. `REFUNDED` is the late-payment path: money arrived after the hold was lost; system tries to re-acquire rooms, else issues a full Stripe refund and marks `REFUNDED` so it is not confused with a voluntary cancel.

### B5. What fields matter on an inventory day?

**Answer:** `totalCount`, `bookedCount`, `reservedCount`, `closed`, `surgeFactor`, `price`. Available ≈ total − booked − reserved if not closed. Unique `(hotel, room, date)`.

---

## C. Concurrency & inventory (expect deep dive)

### C1. How do you prevent two guests from booking the last room?

**Answer:** On init (and date change), load stay-range inventory with **pessimistic write locks** (`findAndLockAvailableInventory`), only rows that still have availability. Bump `reservedCount` for `roomsCount`. Assert the number of locked nights equals nights covered; if not, fail the transaction. Concurrent second guest blocks or sees no availability and fails.

### C2. Why return/assert row counts on bulk inventory updates?

**Answer:** A bulk UPDATE can silently affect fewer rows than expected (race, closed filter, missing dates). Asserting nights covered forces a rollback instead of a partial hold — partial holds are worse than a clean 409/400.

### C3. Can a manager lower `totalCount` below already reserved+booked?

**Answer:** No — update is rejected if any future night has `bookedCount + reservedCount > newTotal`. Otherwise you’d create impossible inventory math and stuck bookings.

### C4. Confirm payment moves reserved → booked. Does `closed` block that?

**Answer:** Confirm should not be gated on `closed` the same way availability search is — a manager closing dates mid-checkout shouldn’t strand a guest who already paid. Discuss: product choice vs “never book closed days even if paid.”

### C5. What if init and expiry run at the same time on the same rows?

**Answer:** Both touch inventory under transactions/locks. Expiry marks unpaid past `holdExpiresAt` (or null hold via `createdAt` fallback) and decrements `reservedCount`. Init holds locks on available rows. DB row locks serialize; design still assumes **one app instance** for schedulers (no ShedLock).

### C6. How does the inventory horizon stay ~365 days?

**Answer:** Activation / new room / daily job call idempotent `ensureInventoryHorizon` — insert only **missing** dates out to `app.inventory.horizon-days`. Without the daily job, the window shrinks by one day every day relative to “today.”

### C7. Hotel city changes — anything else to update?

**Answer:** Yes — denormalized `Inventory.city` used by search. If only `Hotel.city` changes, search keeps filtering the old city.

---

## D. Pricing

### D1. How is a night’s price computed?

**Answer:** Strategy/decorator chain on `room.basePrice`: surge, +20% high occupancy, +15% within 7 days, +25% holiday `MM-dd`. Rounded to 2dp once. Hourly job writes into `inventory.price`.

### D2. Why sum stored `inventory.price` at booking instead of recomputing?

**Answer:** Search averages the same stored column via `HotelMinPrice`. Live recompute at checkout can diverge from what the guest saw (occupancy/surge changed). Stored price is the source of truth for quotes.

### D3. Why must the write path start from `basePrice`, not last stored price?

**Answer:** Feeding yesterday’s `inventory.price` back into multiplicative strategies would **compound** every hourly run (prices explode). Always start from base, apply chain once, write.

### D4. What happens to future prices when a manager updates room base price?

**Answer:** Future inventory prices are recomputed — not blindly overwritten with raw base only in a way that ignores the chain, and not left stale at the old dynamic price forever. (Talk to: recompute via strategies for future nights.)

---

## E. Payments & Stripe

### E1. End-to-end: init → pay → confirmed.

**Answer:** Init soft-holds + `holdExpiresAt` (~10m). Payments creates Checkout Session for `booking.amount`, stores session id, sets `client_reference_id`, status `PAYMENT_PENDING`, extends hold (~30m), pins Stripe `expires_at` to hold. Guest pays. Webhook `checkout.session.completed`: verify signature → dedupe event id → amount check → reserved→booked → `CONFIRMED`.

### E2. Webhook delivered twice — what happens?

**Answer:** `ProcessedStripeEvent` stores Stripe event id. Second delivery sees the id and skips (or no-ops) so inventory isn’t double-confirmed.

### E3. Payment succeeds after the hold expired — what happens?

**Answer:** Late path tries to re-lock available inventory for the stay. If enough nights free, confirm anyway. If not, full refund via Stripe (idempotency key scoped to booking) → status `REFUNDED`.

### E4. Why pin Checkout `expires_at` to the hold?

**Answer:** Stripe’s default session life is ~24h. Without pinning, a guest could pay long after inventory was released, causing more late-pay/refund chaos. Hold for payment must be ≥30 minutes because Stripe rejects shorter session lifetimes when you set `expires_at`.

### E5. Session id missing on the booking — can you still confirm?

**Answer:** Fallback to `client_reference_id` (booking id) on the session so orphaned payments can still be correlated.

### E6. Why verify webhook signatures? Why not trust a “paid” flag from the frontend?

**Answer:** Anyone can call your success URL. Only Stripe-signed events prove payment. Frontend redirect is UX only; source of truth is the webhook (or Stripe retrieve with secret key).

### E7. Cancellation refunds — idempotency?

**Answer:** Stripe refund calls use a stable key like `refund-booking-{id}` so retries don’t double-refund.

---

## F. Idempotency & APIs

### F1. What does `Idempotency-Key` on booking init do?

**Answer:** Scoped as `userId:clientKey`. Store fingerprint of hotel/room/dates/count. Same key + same fingerprint → return existing booking. Same key + different body → conflict. Replay when booking is already `EXPIRED`/`CANCELLED`/`REFUNDED` → 409 (need a new key). Concurrent duplicate inserts hit unique constraint → 409.

### F2. Why not only unique on booking id?

**Answer:** Clients retry on timeouts before they ever got an id. Client-supplied idempotency keys are the standard pattern for “create” that must not double-reserve.

### F3. Can you change dates after payment started?

**Answer:** No — only `RESERVED` / `GUEST_ADDED`. Changing dates under `PAYMENT_PENDING` would change amount vs an already-created Checkout Session.

---

## G. Auth & security

### G1. Access vs refresh tokens — how stored?

**Answer:** Access JWT in `Authorization: Bearer`. Refresh JWT in HttpOnly Secure cookie, path `/api/v1/auth`. Access short-lived (~10m); refresh longer (~7d). Logout clears the cookie.

### G2. What is the `typ` claim for?

**Answer:** Distinguishes `access` vs `refresh` so a refresh token cannot be used as an access token on API routes (and vice versa).

### G3. Why compare user **ids** for ownership, not `user.equals(owner)`?

**Answer:** Hotel owner is often a lazy Hibernate proxy. `equals` based on `getClass()` fails (`User` vs `User$HibernateProxy…`), causing false 403s. Id comparison is stable.

### G4. Name mass-assignment / IDOR issues you fixed or would watch for.

**Answer:** Signup must not accept client `id`. Hotel updates must not map over server fields (active, owner). Booking must not expose password (use `UserDto`, `@JsonIgnore`). Room/booking APIs must verify the caller owns the hotel/booking. Public hotel info must hide inactive hotels.

### G5. Unauthenticated request — 401 or 403?

**Answer:** Should be **401** (not authenticated) via authentication entry point; **403** when authenticated but wrong role/owner. Mixing them confuses clients and refresh logic.

### G6. How are secrets kept out of git?

**Answer:** Shared non-secrets in committed `application.properties`. Local secrets in gitignored `application-dev.properties` (+ `.example` template). Prod from env vars. Never commit Stripe `sk_` / `whsec_` or real JWT secrets; rotate anything that leaked.

---

## H. Scheduling & ops

### H1. List the background jobs and why each exists.

**Answer:** (1) Booking expiry — free holds. (2) Expiry warning — notify ~2 min before hold ends. (3) Pricing update — rewrite `inventory.price` + `HotelMinPrice`. (4) Inventory horizon — roll bookable window forward. Crons are properties; `-` disables. Dev can quiet pricing/warning but must keep expiry.

### H2. You deploy three API replicas. What breaks?

**Answer:** Schedulers run on every instance → duplicate expiry warnings, duplicate pricing sweeps, possible contention. Need ShedLock / leader election / external scheduler. Booking locks in Postgres still protect oversell for request path; jobs are the main multi-instance risk.

### H3. What does readiness health check?

**Answer:** Actuator health with readiness including Boot’s `db` indicator — fails when Postgres is unreachable. Liveness separate. Don’t expose full actuator (env/metrics) publicly.

### H4. Why is Hibernate `ddl-auto=update` not enough for production?

**Answer:** It mostly adds columns; it won’t safely change types or add constraints on existing data. Need Flyway/Liquibase + reviewed migrations. Learning project trades that for speed.

---

## I. Search & frontend

### I1. How does search get price and availability?

**Answer:** Availability from inventory counts over the stay range; price aggregation from denormalized `HotelMinPrice` (fed by pricing job). Filters: city, dates, rooms, optional price/rating/capacity/amenities/sort/page.

### I2. What’s wrong with filtering amenities in memory then paging?

**Answer:** You load a large candidate set, filter/sort in JVM, then page — wrong totals and poor performance at scale. Fix: push filters/sort into SQL (or search engine).

### I3. What does the UI need from `holdExpiresAt`?

**Answer:** Countdown so the guest knows when the soft hold / payment window ends. Also clear messaging for `EXPIRED` and `REFUNDED`.

### I4. Why must logout call the API, not only clear localStorage?

**Answer:** Refresh token lives in an HttpOnly cookie the JS cannot clear reliably without a matching Set-Cookie from the server (`/auth/logout`).

---

## J. Design / system-design follow-ups

### J1. How would you redesign inventory for higher scale?

**Talking points:** Partition by hotel/date; avoid locking many nights in one tx for long stays; consider booking requests + async reservation workers; Redis holds with DB as source of truth; CQRS read models for search (Elasticsearch).

### J2. Exactly-once webhook processing?

**Talking points:** At-least-once is what Stripe gives. Idempotency table (what we have) ≈ effectively-once side effects. Outbox + consumer for multi-step workflows; store processing state machine.

### J3. Alternative to soft holds?

**Talking points:** Pay-first then allocate; shorter holds; queue “interested” users; overbooking with compensation. Soft hold is UX-friendly for Checkout.

### J4. How would you test this properly in CI?

**Talking points:** Testcontainers Postgres (supports `TEXT[]`); contract tests for webhook signatures; concurrency tests (two threads init last room); Flyway in tests; WebMvc for auth/IDOR.

---

## K. Behavioral / “tell me about a bug”

Use real stories from this codebase:

| Prompt | Story angle |
|--------|-------------|
| Hard bug | Hibernate proxy `equals` → false ownership failures |
| Off-by-one | Inclusive vs exclusive dates / nights billed |
| Data integrity | Bulk update without row-count assert → partial holds |
| Security | Mass-assignment on signup id; password on booking JSON |
| Payments | Webhook retry; late pay after expiry → `REFUNDED` |
| Ops | Inventory horizon decay without daily job |
| Pricing | Compounding if strategies feed stored price back in |

Prepare 1–2 minutes each: situation → action → result → what you’d do differently.

---

## L. Rapid-fire (short answers)

| Question | Short answer |
|----------|--------------|
| Default reservation hold? | 10 minutes |
| Payment hold / Stripe pin? | 30 minutes (Stripe minimum when setting `expires_at`) |
| Free cancel window? | 7 days before check-in (100%); else 50% |
| Horizon length? | 365 days |
| Context path? | `/api/v1` |
| Access token where? | `Authorization: Bearer` |
| Refresh token where? | HttpOnly cookie `refreshToken` |
| Webhook path? | `POST /webhook/payment` |
| Health path? | `/api/v1/actuator/health` |
| Guests required before pay? | No |
| Idempotency required? | Optional on init |
| Can delete hotel with live bookings? | No |
| Strategy pattern used for? | Nightly pricing multipliers |
| Why context test disabled? | Postgres `TEXT[]` vs H2 |

---

## M. Questions *you* can ask the interviewer

Shows product sense:

- How would you want overbooking vs strict availability handled?  
- Should managers closing dates cancel open Checkout sessions?  
- SLA for hold release vs “looks sold out”?  
- Multi-currency / tax — in price or at Stripe?  

---

## Practice checklist (before an interview)

- [ ] Draw Hotel → Room → Inventory → Booking on paper  
- [ ] Explain init locking in under 2 minutes  
- [ ] Explain webhook twice + late payment  
- [ ] Explain stored price vs recompute  
- [ ] Explain exclusive check-out with an example  
- [ ] Name 3 production gaps (Flyway, ShedLock, rate limit / token rotation)  
- [ ] One “bug I fixed” story with equals/dates/payments  

---

## Doc links

- [INTERVIEW.md](INTERVIEW.md) — narrative walkthrough  
- [COMPLETE_GUIDE.md](COMPLETE_GUIDE.md) — entire system  
- [DOMAIN.md](DOMAIN.md) · [ARCHITECTURE.md](ARCHITECTURE.md) · [FLOWS.md](FLOWS.md) · [API.md](API.md)
