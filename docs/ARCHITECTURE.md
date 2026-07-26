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

Access token in `Authorization: Bearer …`. Refresh token is an HttpOnly cookie named `refreshToken`.

Under `/api/v1`:

- public: `/auth/**`, `POST /hotels/search`, hotel info/reviews, Stripe webhook, swagger
- `ADMIN` for `/admin/users/**`
- manager or admin for the rest of `/admin/**`
- authenticated for bookings, users, guests, wishlists
- anything else is denied

Ownership is checked in services by comparing **ids** (`Objects.equals(user.getId(), owner.getId())`), not `user.equals(...)`. Hotel owners are lazy-loaded; Hibernate proxies break equals that check `getClass()`, and we hit that the hard way once.

## Jobs

Scheduling is on. Roughly:

- every minute — expire stale unpaid bookings (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING` older than ~10 min) and free `reservedCount`
- every minute (offset) — expiry warning for bookings about to die
- hourly — refresh `HotelMinPrice` for search

## Pricing

Per night, `PricingService` stacks strategies on the room base price: surge from inventory, +20% when occupancy > 80%, +15% if the date is within a week, +25% on configured holidays. Booking amount is sum of nights × roomsCount.

## Payments

Checkout session for `booking.amount` → store session id, set `PAYMENT_PENDING` → Stripe hits `/webhook/payment` → verify signature → `CONFIRMED` and move reserved inventory to booked. Cancels can issue a Stripe refund (full or partial).

## Other bits

Notifications mostly log. Turn on `app.mail.enabled` + spring.mail if you want email for confirm / cancel / expiry warning.

Search leans on inventory for availability and `HotelMinPrice` for price aggregation. Amenity filtering / sorting can finish in memory before paging — not ideal at huge scale, fine for this project.

`POST /bookings/init` accepts optional `Idempotency-Key`. We store it as `userId:clientKey` and keep a fingerprint of hotel/room/dates/count so you can't reuse a key for a different request.
