# airBnbApp — complete project guide

**One file that explains the whole project.** Read this top to bottom if you want the full picture. Deeper topic docs still exist under `docs/` if you need a narrower reference; [INTERVIEW.md](INTERVIEW.md) is the same system told for SDE2 interview prep.

| | |
|--|--|
| **What** | Full-stack hotel search & booking (Airbnb-style learning project) |
| **API** | Java 21 · Spring Boot 4.1 · Postgres · JWT · Stripe · `/api/v1` |
| **UI** | React + Vite + TypeScript (**Stayline**) · `http://localhost:5173` |
| **Repo layout** | Backend at repo root · SPA in `frontend/` · docs in `docs/` · Postman in `postman/` |

---

## 1. What this system does

Three kinds of users:

| Role | Can do |
|------|--------|
| **GUEST** (default signup) | Search hotels, soft-reserve inventory, pay via Stripe, cancel, wishlist, review after stay |
| **HOTEL_MANAGER** | Own hotels/rooms, activate listings, manage inventory (close/surge), see bookings & simple reports |
| **ADMIN** | Everything a manager can, plus promote users to manager |

End-to-end story: a manager creates a hotel and rooms, activates the hotel (that generates ~a year of per-day inventory). Guests search by city and dates, start a booking (inventory is soft-held for ~10 minutes), optionally attach guests, open Stripe Checkout (~30 minute hold), and a webhook marks the booking confirmed. Unpaid holds expire and free inventory. Confirmed stays can cancel under a refund policy. After check-out, the guest can leave a review.

The hard parts are inventory concurrency, time-bounded holds, Stripe webhooks that may retry or arrive late, and keeping the price shown in search equal to the amount charged at checkout.

---

## 2. How to run it locally

**Need:** JDK 21, Postgres, Node (for the UI). Maven wrapper is in the repo.

```bash
# 1. Database
CREATE DATABASE "airBnb";

# 2. Local secrets (gitignored)
cp src/main/resources/application-dev.properties.example \
   src/main/resources/application-dev.properties
# Fill: DB password, jwt.secretKey (32+ chars), Stripe test keys

# 3. API
mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run            # Unix
# → http://localhost:8080/api/v1

# 4. UI
cd frontend && npm install && npm run dev
# → http://localhost:5173

# 5. Stripe webhooks (needed to confirm payment)
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
# Paste the CLI whsec_… into stripe.webhook.secret in application-dev.properties
```

**Useful URLs:** Swagger `…/swagger-ui.html` · Health `…/actuator/health` · OpenAPI `…/v3/api-docs`

**Seed users** (if `app.seed.enabled=true`):

| Role | Email | Password |
|------|-------|----------|
| Manager | manager@example.com | Manager@123 |
| Admin | admin@example.com | Admin@123 |

Signup creates a `GUEST`. Promote with `POST /admin/users/{id}/promote-manager` as admin.

**Config split:**

| File | Committed? | Contents |
|------|------------|----------|
| `application.properties` | yes | Shared non-secrets; `spring.profiles.active=dev` |
| `application-dev.properties` | **no** | Local DB, JWT, Stripe |
| `application-dev.properties.example` | yes | Template |
| `application-prod.properties` | yes | Env-var placeholders only |

Prod needs: `DB_HOST_URL`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `FRONTEND_URL`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, and `SPRING_PROFILES_ACTIVE=prod`.

---

## 3. Stack & layout

```
Browser (Stayline)
    │  Authorization: Bearer <accessJWT>
    │  Cookie: refreshToken (HttpOnly, Secure, path /api/v1/auth)
    ▼
Controllers  →  Services (@Transactional, ownership, booking rules)
    ▼
Repositories (JPQL + pessimistic locks)  →  Postgres
    │
    ├── Stripe Checkout + /webhook/payment
    ├── Pricing strategy chain (scheduled)
    └── Jobs: expiry · expiry warning · pricing · inventory horizon
```

**Backend packages (under `…/airBnbApp/`):**

| Package | Role |
|---------|------|
| `controller` | REST endpoints |
| `service` | Business logic |
| `repository` | Spring Data JPA |
| `entity` | Tables + enums |
| `dto` | Request/response + validation |
| `security` | JWT filter, AuthService, WebSecurityConfig |
| `strategy` | Nightly pricing decorators |
| `config` | ModelMapper, OpenAPI, seed, holidays, Stripe |
| `advice` | `ApiResponse` + `GlobalExceptionHandler` |
| `exception` | Domain exceptions |
| `util` | `AppUtils.getCurrentUser()` |

All HTTP responses wrap as:

```json
{ "timeStamp": "...", "data": {}, "error": null }
```

Schema is managed with Hibernate `ddl-auto=update` (fine for learning; not a real migration story). Postgres `TEXT[]` is used for photos/amenities — that is why the full Spring context test is `@Disabled` (H2 does not love those types).

---

## 4. Domain model

```
User ──owns──► Hotel ──has──► Room ──has──► Inventory (one row per calendar date)
User ──has──► Booking (hotel + room + dates + amount + status + holdExpiresAt)
Booking ◄──► Guest (many-to-many)
User ──► Wishlist (hotel)
User ──► Review (after confirmed stay past check-out)
HotelMinPrice          denormalized daily min price for search
ProcessedStripeEvent   webhook event-id dedupe
```

### Inventory (unit of sale)

Unique on `(hotel_id, room_id, date)`. Indexed for search `(city, date)` and booking `(room_id, date)`.

| Field | Meaning |
|-------|---------|
| `totalCount` | How many units of this room type sell that day |
| `bookedCount` | Paid |
| `reservedCount` | Soft hold from unpaid bookings |
| `closed` | Manager closed the day |
| `surgeFactor` | Manager / strategy input |
| `price` | **Stored nightly sell price** — search averages it; bookings sum it |

**Available** ≈ `totalCount - bookedCount - reservedCount` if not closed.

Activating a hotel (or adding a room to an already-active hotel) fills inventory out to `app.inventory.horizon-days` (default 365). A daily job rolls that window forward. Generation only inserts **missing** dates, so it is idempotent. Changing a hotel’s city also updates denormalized `Inventory.city` (search filters on it). Lowering `totalCount` is rejected if any future night already has `booked + reserved` above the new total. Deleting a hotel is refused while live bookings exist (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING` / `CONFIRMED`).

### Dates (critical)

- **Stay / booking:** check-out is **exclusive**. Stay 10th→12th = nights of the 10th and 11th. Query: `date >= checkIn AND date < checkOut`. Equal check-in/check-out = zero nights → rejected.
- **Admin windows** (close dates, reports): **inclusive** on both ends.

Mixing these is a classic off-by-one. The repository labels stay params `checkInDate`/`checkOutDate` and admin params `startDate`/`endDate`.

### Booking statuses

```
RESERVED ──(optional guests)──► GUEST_ADDED ──(start pay)──► PAYMENT_PENDING
                                                              │ webhook
                                                          CONFIRMED
                                                              │ cancel
                                                          CANCELLED

Unpaid past holdExpiresAt ──► EXPIRED   (job frees reservedCount)
Guest cancels unpaid     ──► CANCELLED  (no refund)
Paid after rooms gone    ──► REFUNDED   (auto Stripe refund)
```

- Guests are optional; you can go `RESERVED` → payments directly.
- Date changes only while unpaid (`RESERVED` / `GUEST_ADDED`).
- `holdExpiresAt` set on init (~10 min), extended on payment start (~30 min), exposed on `BookingDto` for the UI countdown. Null holds still expire via a `createdAt` fallback.
- Optional `Idempotency-Key` on init: same user + key + same fingerprint → same booking; mismatch or terminal booking (`EXPIRED`/`CANCELLED`/`REFUNDED`) → 409.

### Cancellation policy (defaults)

| Situation | Refund |
|-----------|--------|
| Unpaid hold | $0; release inventory |
| Confirmed, check-in ≥ 7 days out | 100% (`app.cancellation.free-cancel-days`) |
| Confirmed, inside that window | 50% (`app.cancellation.partial-refund-percent`) |

Quote first: `GET /bookings/{id}/cancellation-quote`.

### Pricing

Hourly job writes `inventory.price` from room `basePrice` through a strategy chain:

1. × surge  
2. +20% if occupancy > 80%  
3. +15% if date within next 7 days  
4. +25% if `MM-dd` is in `app.holidays.dates`  

Rounded to 2 decimal places once at the end. **Quotes and search use the stored column** — they do not recompute live at checkout (that would diverge from what search showed). The write path always starts from `basePrice` so multipliers do not compound every hour. Booking amount = sum(nightly prices) × `roomsCount`.

### Search

`POST /hotels/search`: city, start/end, roomsCount; optional min/max price, minRating, minCapacity, amenities (all must match), sort (`PRICE_ASC` / `PRICE_DESC` / `RATING_DESC`), page/size (size capped at 100). Only **active** hotels. Some amenity filter/sort happens in memory before paging — fine for this size, not for huge catalogs. Public hotel info also hides inactive hotels.

---

## 5. Main flows

### Manager lists a hotel

1. Login as manager  
2. `POST /admin/hotels` (starts inactive)  
3. `POST /admin/hotels/{id}/rooms`  
4. `PATCH …/activate` → ~1 year of inventory  
5. Optional: more rooms (auto-inventoried if already active), inventory surge/closed patches  
6. Search to verify  

### Guest books and pays

1. Signup/login  
2. Search → optional hotel info  
3. `POST /bookings/init` → `RESERVED` + hold  
4. Optional add guests  
5. `POST …/payments` → `{ id, url }`, `PAYMENT_PENDING`, hold extended; Stripe Session `expires_at` pinned to hold  
6. Pay in Stripe; CLI forwards webhook → `CONFIRMED`  
7. If money arrives after inventory was released → try re-acquire, else full refund → `REFUNDED`  

### Cancel / review / wishlist

- Cancel: quote then `POST …/cancel`  
- Review: after check-out on a confirmed booking; updates hotel `averageRating` / `reviewCount`  
- Wishlist: unique `(user, hotel)`  

---

## 6. API map (all under `/api/v1`)

Auth: `Authorization: Bearer <accessToken>` unless public.

| Area | Endpoints |
|------|-----------|
| **Auth** | `POST /auth/signup`, `/login`, `/refresh`, `/logout` |
| **Browse** | `POST /hotels/search`, `GET /hotels/{id}/info`, `GET /hotels/{id}/reviews` |
| **Bookings** | init, addGuests, guests, dates, payments, status, cancellation-quote, cancel; reviews on booking |
| **Guests / me** | `/guests`, `/users/getMyProfile`, profile patch, myBookings, myReviews |
| **Wishlist** | POST/DELETE `/wishlists/hotels/{id}`, GET `/wishlists` |
| **Admin hotels/rooms** | CRUD + activate + bookings/reports/analytics |
| **Admin inventory** | GET/PATCH room or bulk hotel |
| **Admin users** | `POST /admin/users/{id}/promote-manager` |
| **Webhook** | `POST /webhook/payment` (signature verified) |
| **Ops** | `GET /actuator/health` (+ liveness/readiness; readiness includes db) |

**Security rules (sketch):** public auth, search, hotel info/reviews, webhook, swagger, health · `ADMIN` for `/admin/users/**` · manager/admin for `/admin/**` · authenticated for bookings/users/guests/wishlists · else deny. Unauthenticated → **401**.

Typical errors: 400 validation, 401 auth, 403 role/owner, 404 missing, 409 inventory/idempotency/bad state.

---

## 7. Security & auth (detail)

- **Access JWT** in header (~10 min TTL). **Refresh JWT** in HttpOnly Secure cookie, path-scoped to `/api/v1/auth`, ~7 days.  
- Tokens carry a `typ` claim (`access` vs `refresh`) so they are not interchangeable.  
- Logout clears the refresh cookie with matching attributes.  
- Ownership checks compare **user ids**, not `entity.equals` — Hibernate lazy proxies break class-based equals.  
- Mass-assignment hardening: signup cannot set id; hotel updates skip server-owned fields; `User.password` is `@JsonIgnore`; booking responses use `UserDto`.  
- Room/booking mutations check the caller owns the resource (IDOR fixes).  

Honest gaps: no refresh-token rotation/denylist, no rate limiting, schedulers assume one instance, JWT secrets in old history should be treated as burned.

---

## 8. Payments (Stripe)

1. Create Checkout Session for `booking.amount`; store session id; set `client_reference_id` to booking id.  
2. Webhook verifies Stripe signature.  
3. Dedupe on Stripe event id via `ProcessedStripeEvent`.  
4. Route: `checkout.session.completed` → confirm (amount check, inventory move) or late-pay path; `checkout.session.expired` → hold cleanup path.  
5. If session id missing, fall back to `client_reference_id`.  
6. Cancels/refunds use a booking-scoped Stripe idempotency key.  

Do not fake signatures from Postman — verification will reject them. Local confirm needs `stripe listen`.

---

## 9. Concurrency & correctness

| Concern | Approach |
|---------|----------|
| Oversell on init / date change | Pessimistic write lock on available stay rows; bump `reservedCount`; assert all nights updated |
| Confirm / cancel / release | Lock stay range; move or free counts |
| Partial bulk updates | Row-count asserts → rollback |
| Double webhook | Event id table |
| Double init with same key | Idempotency key + fingerprint; concurrent insert → 409 |
| Price drift search vs pay | Both use stored `inventory.price` |
| Horizon decay | Daily idempotent fill of missing dates |
| Multi-instance jobs | **Not handled** (no ShedLock) — single instance assumed |

---

## 10. Background jobs

Every cron is a property; set to `-` to disable. Dev profile disables pricing + expiry-warning noise; **expiry stays on**.

| Job | Default cron | Purpose |
|-----|--------------|---------|
| Booking expiry | every minute at :00 | Unpaid → `EXPIRED`, free `reservedCount` |
| Expiry warning | every minute at :30 | Warn ~2 minutes before hold ends |
| Pricing update | hourly | Reprice inventory + refresh `HotelMinPrice` (per-hotel transaction) |
| Inventory horizon | 03:30 daily | Keep ~365 days of inventory ahead |

Expiry must run at least as often as the shortest hold. Payment hold must stay ≥ 30 minutes (Stripe session minimum when pinning `expires_at`).

---

## 11. Frontend (Stayline)

- Vite React app talks to `VITE_API_BASE` (default `http://localhost:8080/api/v1`).  
- Stores access token; uses refresh cookie; clears auth on 401; logout calls API.  
- Shows hold countdown from `holdExpiresAt`, and copy for `EXPIRED` / `REFUNDED`.  
- Guest fields (age/gender), local date defaults, and search date mins match exclusive check-out.  
- Manager UI: create hotel → rooms → activate → bookings.  

See `frontend/README.md` for run notes.

---

## 12. Important config knobs

| Property | Default | Meaning |
|----------|---------|---------|
| `app.booking.reservation-hold-minutes` | 10 | Soft hold after init |
| `app.booking.payment-hold-minutes` | 30 | Hold after payment starts / Stripe session |
| `app.cancellation.free-cancel-days` | 7 | Full refund window |
| `app.cancellation.partial-refund-percent` | 50 | Partial refund % |
| `app.inventory.horizon-days` | 365 | How far inventory is generated |
| `app.holidays.dates` | `01-01,12-25` | Holiday price bump (`MM-dd`) |
| `app.mail.enabled` | false | Email vs log notifications |
| `app.scheduling.*.cron` | see above | Job schedules |

---

## 13. Tests & tooling

```bash
mvnw.cmd test
```

Most unit / WebMvc tests run without a live DB. Full Boot context test is disabled because of Postgres-specific types. Postman collection under `postman/` — typical order: manager login → hotel → room → activate → guest login → search → init → payments → Stripe CLI → cancel as needed.

---

## 14. Known limitations (by design for learning)

- No Flyway / Docker / CI out of the box; `ddl-auto` will not change column types or add constraints on existing columns without a recreate.  
- No distributed lock on schedulers.  
- Holidays are static `MM-dd` strings, not a calendar API.  
- Search amenity filter/sort can page in memory.  
- Notifications mostly log unless mail is configured.  
- Not production-hardened auth (rotation, rate limits, etc.).  

Natural next steps: Flyway + Testcontainers, ShedLock, refresh-token rotation, DB-side search pagination, rate limits, Docker Compose.

---

## 15. Where to look in code

| Topic | Start here |
|-------|------------|
| Booking state machine / Stripe | `service/BookingServiceImpl.java` |
| Inventory locks & horizon | `repository/InventoryRepository.java`, `service/InventoryServiceImpl.java` |
| Pricing chain | `strategy/PricingService.java` |
| Security rules | `security/WebSecurityConfig.java`, `security/JWTService.java` |
| Auth cookies / logout | `controller/AuthController.java` |
| Webhook dedupe entity | `entity/ProcessedStripeEvent.java` |
| Shared defaults | `src/main/resources/application.properties` |

---

## 16. Doc index

| Doc | Use when |
|-----|----------|
| **This file** | You want everything in one pass |
| [INTERVIEW.md](INTERVIEW.md) | Interview walkthrough, tradeoffs, sample questions |
| [SETUP.md](SETUP.md) | Getting the machine running |
| [DOMAIN.md](DOMAIN.md) | Inventory / statuses / pricing rules only |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Packages, jobs, security shape |
| [FLOWS.md](FLOWS.md) | Step-by-step HTTP walkthroughs |
| [API.md](API.md) | Endpoint cheat sheet |
| [../frontend/README.md](../frontend/README.md) | Stayline UI |
| [../README.md](../README.md) | Repo landing + quick start |
