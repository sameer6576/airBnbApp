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

Hotels only show up in search when `active` is true. Activating a hotel generates about a year of inventory for its rooms. Room `totalCount` is how many physical units of that type you have; that number is copied onto each inventory day. Updating a room can sync future inventory.

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
RESERVED → (add guests) → GUEST_ADDED → (payments) → PAYMENT_PENDING
                                                         ↓ webhook
                                                     CONFIRMED
                                                         ↓ cancel
                                                     CANCELLED

Unpaid (RESERVED / GUEST_ADDED / PAYMENT_PENDING) past ~10 min → EXPIRED
```

Confirmed bookings don't auto-expire.

Optional `Idempotency-Key` on init: same user + key + same request fingerprint returns the same booking. Same key, different body → conflict.

Guests live under `/guests` and belong to the current user. You can attach existing ids or create them when adding to a booking.

Reviews: after check-out on a confirmed booking. Updates the hotel's `averageRating` / `reviewCount`.

Wishlist is just unique (user, hotel) rows.

## Cancellation

From config (defaults in parentheses):

- `app.cancellation.free-cancel-days` (7) — full refund if check-in is that far out
- `app.cancellation.partial-refund-percent` (50) — otherwise that % of the booking amount

Hit `GET /bookings/{id}/cancellation-quote` before cancel if you want the number first.

## Pricing (nightly)

1. room base × surge
2. +20% if occupancy > 80%
3. +15% if date within next 7 days
4. +25% if `MM-dd` is in `app.holidays.dates`

Amount = sum(nights) × roomsCount.

## Search

`POST /hotels/search` needs city, start/end, roomsCount. Optional filters: min/max price, minRating, minCapacity, amenities (all must match), sort (`PRICE_ASC` / `PRICE_DESC` / `RATING_DESC`), page/size.
