# Airbnb Hotel Booking API

Spring Boot REST backend for an Airbnb-style hotel booking platform: JWT auth, hotel/room/inventory admin, dynamic pricing, multi-step bookings, and Stripe Checkout.

## Architecture

```text
Client (Postman / Swagger)
        │
        ▼
 Controllers  →  Services  →  Repositories  →  PostgreSQL
                     │
                     ├── Pricing strategies (surge, occupancy, urgency, holiday)
                     └── Stripe Checkout + webhooks
```

**Context path:** `/api/v1` (e.g. `http://localhost:8080/api/v1`)

| Layer | Responsibility |
|-------|----------------|
| Controllers | HTTP API |
| Services | Booking lifecycle, inventory, auth, pricing |
| Repositories | JPA / JPQL (including pessimistic locks for inventory) |
| Strategy | Dynamic room pricing chain |

## Domain model

- **Hotel** owned by a `HOTEL_MANAGER` user; has many **Rooms**
- **Inventory**: one row per room per date (`totalCount`, `bookedCount`, `reservedCount`, `surgeFactor`, `closed`)
- **HotelMinPrice**: denormalized daily min price for search (refreshed hourly)
- **Booking** states: `RESERVED` → `GUEST_ADDED` → `PAYMENT_PENDING` → `CONFIRMED` (or `CANCELLED` / `EXPIRED`)
- Unpaid reservations expire after **10 minutes** and release `reservedCount`

## Pricing strategy chain

1. Base room price  
2. Surge factor  
3. +20% if occupancy &gt; 80%  
4. +15% if stay date is within 7 days  
5. +25% if date matches configured holidays (`app.holidays.dates`)

## Roles

| Role | Access |
|------|--------|
| `GUEST` | Bookings, guests, profile, wishlist, reviews |
| `HOTEL_MANAGER` | `/admin/**` hotels, rooms, inventory, reports, analytics |
| `ADMIN` | `/admin/users/**` promote managers (+ manager admin APIs) |

With `app.seed.enabled=true`, startup seeds:

- Manager: `manager@example.com` / `Manager@123`
- Admin: `admin@example.com` / `Admin@123`

## Extra features

- **Search filters** — `minPrice`, `maxPrice`, `minRating`, `minCapacity`, `amenities[]`, `sortBy` (`PRICE_ASC` | `PRICE_DESC` | `RATING_DESC`) on `POST /hotels/search`
- **Idempotent booking init** — optional header `Idempotency-Key` on `POST /bookings/init` (retries return the same booking)
- **Reviews** — `POST /bookings/{id}/reviews` after check-out; `GET /hotels/{id}/reviews`; hotel `averageRating` on search
- **Wishlist** — `POST/DELETE /wishlists/hotels/{id}`, `GET /wishlists`
- **Emails** — confirm / cancel / expiry warning (logged by default; enable SMTP via `app.mail.enabled` + `spring.mail.*`)
- **Modify booking** — `PATCH /bookings/{id}/dates`, `PUT /bookings/{id}/guests`
- **Cancellation policy** — free cancel within `app.cancellation.free-cancel-days` (default 7); else partial `%` refund; quote via `GET /bookings/{id}/cancellation-quote`
- **Analytics** — `GET /admin/hotels/{id}/analytics` (occupancy, cancel rate, top rooms)
- **Bulk inventory** — `PATCH /admin/inventory/hotels/{id}`
- **Promote manager** — `POST /admin/users/{id}/promote-manager` (ADMIN)

## Prerequisites

- Java 21
- PostgreSQL database (default: `airBnb` on `localhost:5432`)
- Stripe test keys (optional for payment flow)

## Configuration

Secrets live in `src/main/resources/application.properties` (gitignored). Typical keys:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/airBnb
spring.datasource.username=postgres
spring.datasource.password=...
jwt.secretKey=...
frontend.url=http://localhost:8080
stripe.secret.key=sk_test_...
stripe.webhook.secret=whsec_...
app.seed.enabled=true
app.holidays.dates[0]=01-01
app.holidays.dates[1]=12-25
app.holidays.dates[2]=07-04
```

## Run

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

### Swagger UI

[http://localhost:8080/api/v1/swagger-ui.html](http://localhost:8080/api/v1/swagger-ui.html)

Authorize with **Bearer** access token from `POST /auth/login`.

### Postman

1. Import [`postman/AirbnbApp.postman_collection.json`](postman/AirbnbApp.postman_collection.json)
2. Import [`postman/AirbnbApp.local.postman_environment.json`](postman/AirbnbApp.local.postman_environment.json)
3. Select **AirbnbApp Local**
4. Suggested order: **Login Manager** → Admin (create hotel/room/activate) → **Login Guest** → Browse & Book

Login/refresh scripts store `accessToken` in the environment. Collection auth uses `Authorization: Bearer {{accessToken}}`.

### Stripe webhook (local)

```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
```

Copy the signing secret into `stripe.webhook.secret`, then complete Checkout from **Initiate Payment**. Do not fake webhook signatures in Postman.

## Main API surface

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/signup`, `/login`, `/refresh` |
| Browse | `POST /hotels/search`, `GET /hotels/{id}/info` |
| Bookings | `POST /bookings/init`, `.../addGuests`, `.../payments`, `.../cancel`, `GET .../status` |
| Guests | CRUD under `/guests` |
| Users | `/users/profile`, `/myBookings`, `/getMyProfile` |
| Admin | `/admin/hotels/**`, `/admin/inventory/**` |
| Webhook | `POST /webhook/payment` |

All successful responses are wrapped as:

```json
{ "timeStamp": "...", "data": { }, "error": null }
```

## Known limitations

- Production hygiene (Flyway, Docker, CI, env profiles) is not included yet
- `HolidayCalendar` uses configured recurring `MM-dd` dates (not a live holiday API)
- Booking expiry job runs every minute; access tokens are short-lived (~10 minutes)
