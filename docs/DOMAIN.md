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

Hotels only show up in search (and on public hotel info) when `active` is true. Activating a hotel generates inventory for its rooms out to the horizon (`app.inventory.horizon-days`, default 365). Adding a room to an **already active** hotel initializes that room too. Room `totalCount` is how many physical units of that type you have; that number is copied onto each inventory day.

A daily job (`app.scheduling.inventory-horizon.cron`) rolls the horizon forward, so the bookable window stays a constant length instead of shrinking a day at a time. Generation only creates dates that are missing, which makes it idempotent — activating an already-active hotel is a no-op, and a run missed during downtime is made up on the next pass.

Changing a hotel's city also updates the denormalized `Inventory.city` column, because search filters on that field.

## Dates

Check-out is not a night. A stay from the 10th to the 12th occupies the 10th and the 11th — two nights, billed as two, with the 12th left on sale. Queries express this as `date >= checkInDate AND date < checkOutDate`, and a booking where the two dates are equal is rejected as zero nights.

Manager and reporting windows are the other way round: inclusive on both ends, so closing "the 1st to the 5th" closes five days.

## Inventory

Unique on `(hotel_id, room_id, date)`, and indexed on `(city, date)` — the search predicate — plus `(room_id, date)` for the booking path.

- `totalCount` — sellable that day
- `bookedCount` — paid
- `reservedCount` — soft hold from unpaid bookings
- `closed` — manager closed the day
- `surgeFactor` — manager / strategy input
- `price` — **stored nightly sell price**; search averages this, bookings sum it

Available ≈ `totalCount - bookedCount - reservedCount` if not closed.

Init booking bumps `reservedCount` under pessimistic locks and asserts every night was updated. Confirm moves reserved → booked (not gated on `closed`). Cancel or expiry releases the hold. Bulk inventory updates return row counts; a partial update rolls back.

Lowering a room's `totalCount` is rejected if any future night already has `bookedCount + reservedCount` above the new total.

## Booking statuses

```
RESERVED → (optional guests) → GUEST_ADDED → (payments) → PAYMENT_PENDING
                                                         ↓ webhook
                                                     CONFIRMED
                                                         ↓ cancel
                                                     CANCELLED

Unpaid holds past holdExpiresAt → EXPIRED (job frees reserved inventory)
User can also cancel unpaid holds anytime → CANCELLED (no refund)

Paid but the rooms were gone → REFUNDED (money returned automatically)
```

Confirmed bookings don't auto-expire.

`REFUNDED` covers the case where payment lands after the hold lapsed: the booking tries to re-acquire the rooms, and refunds in full if it can't. It's kept distinct from `CANCELLED`, which is always guest-initiated.

Dates can only be changed while a booking is unpaid (`RESERVED` or `GUEST_ADDED`). Changing them once a Checkout Session exists would move the amount without settling the difference.

`holdExpiresAt` is set on init (`app.booking.reservation-hold-minutes`, default 10) and exposed on `BookingDto` so the UI can show a countdown. Starting payment bumps it (`app.booking.payment-hold-minutes`, default 30) so Stripe Checkout has time. Rows with a null `holdExpiresAt` still expire via a `createdAt` fallback.

Guests are optional — you can go `RESERVED` → payments directly.

Optional `Idempotency-Key` on init: same user + key + same request fingerprint returns the same booking. Same key, different body → conflict. Replaying a key whose booking is already `EXPIRED` / `CANCELLED` / `REFUNDED` returns 409 and asks for a new key. Concurrent replays hit the unique constraint and also surface as 409.

Guests live under `/guests` and belong to the current user. You can attach existing ids or create them when adding to a booking (also ok while `PAYMENT_PENDING`).

Reviews: after check-out on a confirmed booking. Updates the hotel's `averageRating` / `reviewCount`.

Wishlist is just unique (user, hotel) rows.

Deleting a hotel is refused while it has live bookings (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING` / `CONFIRMED`). Otherwise related reviews, wishlist rows, historical bookings, min-prices, inventory and rooms are removed in FK-safe order.

## Cancellation

- **Unpaid** (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING`): cancel releases the hold, refund $0
- **Confirmed**: policy from config

Defaults:
- `app.cancellation.free-cancel-days` (7) — full refund if check-in is that far out
- `app.cancellation.partial-refund-percent` (50) — otherwise that % of the booking amount

`GET /bookings/{id}/cancellation-quote` — unpaid quotes as $0; confirmed uses the policy. Refunds use a Stripe idempotency key scoped to the booking.

## Pricing (nightly)

The hourly pricing job writes into `inventory.price`:

1. room `basePrice` × surge
2. +20% if occupancy > 80%
3. +15% if date within next 7 days
4. +25% if `MM-dd` is in `app.holidays.dates`

Result is rounded to 2 decimal places once at the end of the chain (matches `numeric(10,2)`).

**Booking quotes sum stored `inventory.price`**, not a live recompute. Search averages the same column via `HotelMinPrice`, so the price a guest sees and the amount they pay stay aligned. The strategy chain still starts from `room.basePrice` when *writing* prices — starting from the stored value would compound multipliers every hour.

Room updates recompute future inventory prices rather than overwriting them with the raw base price.

Amount = sum(nights) × roomsCount.

## Search

`POST /hotels/search` needs city, start/end, roomsCount. Optional filters: min/max price, minRating, minCapacity, amenities (all must match), sort (`PRICE_ASC` / `PRICE_DESC` / `RATING_DESC`), page/size (size capped at 100).
