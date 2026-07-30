# Architecture

Pretty standard Spring layering:

```
Controller → Service → Repository → Postgres
                ↓
         pricing strategies / Stripe / notifications
```

Controllers validate and map HTTP. Services own transactions, ownership checks, and the messy booking/inventory logic. Repositories have the JPQL and locking. `advice` wraps responses and maps exceptions; `security` handles JWT.

## Packages

| package | what's in there |
|---------|-----------------|
| controller | REST |
| service | booking, inventory, hotels, reviews, etc. |
| repository | Spring Data |
| entity | JPA + enums |
| dto | payloads + validation |
| security | WebSecurityConfig, JWT filter/service, auth |
| strategy | pricing chain |
| config | ModelMapper, OpenAPI, DataLoader seed, holidays, Stripe |
| advice | ApiResponse, GlobalExceptionHandler |
| exception | ResourceNotFoundException and friends |
| util | `AppUtils.getCurrentUser()` |

## Security

Access token in `Authorization: Bearer …`. Refresh token is an HttpOnly, Secure cookie named `refreshToken`, path-scoped to `/api/v1/auth`. Tokens carry a `typ` claim (`access` vs `refresh`) so one cannot be used in place of the other. Access TTL is short (~10 min); refresh is 7 days.

Under `/api/v1`:

- public: `/auth/**` (signup, login, refresh, logout), `POST /hotels/search`, hotel info/reviews, Stripe webhook, swagger, actuator health
- `ADMIN` for `/admin/users/**`
- manager or admin for the rest of `/admin/**`
- authenticated for bookings, users, guests, wishlists
- anything else is denied

`POST /auth/logout` clears the refresh cookie. Unauthenticated API calls get **401** (not a blank 403) via a dedicated auth entry point.

Ownership is checked in services by comparing **ids** (`Objects.equals(user.getId(), owner.getId())`), not `user.equals(...)`. Hotel owners are lazy-loaded; Hibernate proxies break equals that check `getClass()`, and we hit that the hard way once.

DTOs avoid mass-assignment pitfalls: signup cannot set an id; hotel updates skip server-owned fields via ModelMapper; booking responses expose `UserDto` (password is `@JsonIgnore` on the entity).

## Jobs

Scheduling is on. Every cron is a property, so local noise can be quieted without changing production timing — set one to `-` to disable that job.

| job | property | default |
|-----|----------|---------|
| expire unpaid bookings (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING`), free `reservedCount` | `app.scheduling.booking-expiry.cron` | `0 * * * * *` |
| warn when a hold is within ~2 minutes of expiring | `app.scheduling.expiry-warning.cron` | `30 * * * * *` |
| reprice inventory and refresh `HotelMinPrice` for search | `app.scheduling.pricing-update.cron` | `0 0 * * * *` |
| roll the inventory horizon forward for active hotels | `app.scheduling.inventory-horizon.cron` | `0 30 3 * * *` |

The expiry job has to run at least as often as the shortest hold, or inventory stays unsellable past its deadline and dates look falsely sold out. The warning job only looks two minutes ahead, so it is pointless unless it runs at least that often — it is offset 30 seconds behind the expiry job so the two do not contend on the same rows.

The dev profile disables the warning and pricing jobs; the expiry job stays on because the booking flow depends on holds actually being released.

Init sets hold to `app.booking.reservation-hold-minutes` (default 10). Starting payment bumps it to `app.booking.payment-hold-minutes` (default 30). Keep the payment hold at 30 or above: the Stripe Checkout Session is pinned to that deadline, and Stripe refuses a session lifetime under 30 minutes.

The pricing sweep and the horizon job both commit one transaction per hotel and log-and-continue on failure, so one bad hotel does not roll back the rest. Both page with an explicit sort, because unsorted pagination over rows being written can skip or repeat.

Inventory generation is idempotent — it creates only the dates a room is missing — which is why the same code path serves first-time activation, adding a room to a live hotel, and the daily roll-forward. Without that job the horizon decayed by a day per day, and searches beyond it returned nothing with no error to explain why.

None of the jobs take a distributed lock, so this still assumes a single instance.

## Dates

Stay ranges are check-out exclusive: `date >= checkInDate AND date < checkOutDate`. A stay from the 1st to the 2nd is one night, billed as one night, and the 2nd remains sellable.

Administrative ranges — a manager closing dates, a pricing sweep window, an analytics window — are inclusive on both ends, because "the 1st to the 5th" means five days. `InventoryRepository` is split into two clearly labelled sections for this reason, and parameter naming carries the distinction: `checkInDate` / `checkOutDate` for stays, `startDate` / `endDate` for administrative windows.

## Health

`spring-boot-starter-actuator` exposes health only, at `/api/v1/actuator/health`, with liveness and readiness probes enabled. Readiness includes Boot's `db` indicator, so it fails when Postgres is unreachable rather than reporting healthy regardless. The mail indicator is disabled, since mail is intentionally unconfigured by default.

## Pricing

Hourly job (`PricingService` + decorator strategies) writes nightly sell prices into `inventory.price`:

1. room `basePrice` × surge
2. +20% if occupancy > 80%
3. +15% if date within next 7 days
4. +25% if `MM-dd` is in `app.holidays.dates`

Rounded to 2dp once at the end of the chain. **Booking quotes and search both use the stored column** — bookings sum it; `HotelMinPrice` averages it. The chain always starts from `room.basePrice` when *writing*, so multipliers do not compound every hour.

Room updates recompute future inventory prices rather than stomping them with the raw base.

## Payments

Checkout session for `booking.amount` → store session id + `client_reference_id` → set `PAYMENT_PENDING` → Stripe hits `/webhook/payment` → verify signature → route by event type:

- `checkout.session.completed` → confirm (or late-payment retry / auto-refund → `REFUNDED`)
- `checkout.session.expired` → let the hold expire path clean up

Webhook handlers dedupe on Stripe event id via `ProcessedStripeEvent`, check paid amount against booking amount, and fall back to `client_reference_id` if the session id was never persisted. Cancels can issue a Stripe refund (full or partial) with a booking-scoped idempotency key.

## Other bits

Notifications mostly log. Turn on `app.mail.enabled` + spring.mail if you want email for confirm / cancel / expiry warning.

Search leans on inventory for availability and `HotelMinPrice` for price aggregation. Amenity filtering / sorting can finish in memory before paging — not ideal at huge scale, fine for this project.

`POST /bookings/init` accepts optional `Idempotency-Key`. We store it as `userId:clientKey` and keep a fingerprint of hotel/room/dates/count so you can't reuse a key for a different request. Replays against terminal bookings (`EXPIRED` / `CANCELLED` / `REFUNDED`) return 409.
