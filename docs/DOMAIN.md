# Domain

## Main pieces

```
User owns Hotel → Room → Inventory (one row per date)
User has Booking (hotel + room + dates + amount + status)
       Booking ↔ Guest (many-to-many)
User can wishlist hotels and leave reviews after a stay

HotelMinPrice = denormalized daily min price for search
```

Roles: `GUEST` (default signup), `HOTEL_MANAGER` (owns hotels), `ADMIN` (can promote managers, also has manager APIs).

Hotels only show up in search when `active` is true. Activating a hotel generates about a year of inventory for its rooms. Adding a room to an **already active** hotel also initializes that room's inventory. Room `totalCount` is how many physical units of that type you have; that number is copied onto each inventory day. Updating a room can sync future inventory.

## Inventory

Unique on `(hotel_id, room_id, date)`.

- `totalCount` — sellable that day
- `bookedCount` — paid
- `reservedCount` — soft hold from unpaid bookings
- `closed` — manager closed the day
- `surgeFactor` / `price` — pricing inputs

Available ≈ `totalCount - bookedCount - reservedCount` if not closed.

Init booking bumps `reservedCount` under pessimistic locks. Confirm moves reserved → booked. Cancel or expiry releases the hold.

## Booking statuses

```
RESERVED → (optional guests) → GUEST_ADDED → (payments) → PAYMENT_PENDING
                                                         ↓ webhook
                                                     CONFIRMED
                                                         ↓ cancel
                                                     CANCELLED

Unpaid holds past holdExpiresAt → EXPIRED (job frees reserved inventory)
User can also cancel unpaid holds anytime → CANCELLED (no refund)
```

Confirmed bookings don't auto-expire.

`holdExpiresAt` is set on init (`app.booking.reservation-hold-minutes`, default 10). Starting payment bumps it (`app.booking.payment-hold-minutes`, default 30) so Stripe Checkout has time.

Guests are optional — you can go `RESERVED` → payments directly.

Optional `Idempotency-Key` on init: same user + key + same request fingerprint returns the same booking. Same key, different body → conflict.

Guests live under `/guests` and belong to the current user. You can attach existing ids or create them when adding to a booking (also ok while `PAYMENT_PENDING`).

Reviews: after check-out on a confirmed booking. Updates the hotel's `averageRating` / `reviewCount`.

Wishlist is just unique (user, hotel) rows.

## Cancellation

- **Unpaid** (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING`): cancel releases the hold, refund $0
- **Confirmed**: policy from config

Defaults:
- `app.cancellation.free-cancel-days` (7) — full refund if check-in is that far out
- `app.cancellation.partial-refund-percent` (50) — otherwise that % of the booking amount

`GET /bookings/{id}/cancellation-quote` — unpaid quotes as $0; confirmed uses the policy.

## Pricing (nightly)

1. room base × surge
2. +20% if occupancy > 80%
3. +15% if date within next 7 days
4. +25% if `MM-dd` is in `app.holidays.dates`

Amount = sum(nights) × roomsCount.

## Search

`POST /hotels/search` needs city, start/end, roomsCount. Optional filters: min/max price, minRating, minCapacity, amenities (all must match), sort (`PRICE_ASC` / `PRICE_DESC` / `RATING_DESC`), page/size.
