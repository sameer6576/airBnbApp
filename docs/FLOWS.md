# Walkthroughs

Paths skip `/api/v1`. Use a Bearer token unless the route is public.

Seed accounts (if enabled):

- manager@example.com / Manager@123  
- admin@example.com / Admin@123  

---

## Manager lists a hotel

1. `POST /auth/login` as manager  
2. `POST /admin/hotels` — hotel starts inactive  
3. `POST /admin/hotels/{hotelId}/rooms` — set basePrice, totalCount, capacity, etc.  
4. `PATCH /admin/hotels/{hotelId}/activate` — builds ~1 year of inventory  
5. Optional: `PATCH /admin/inventory/rooms/{roomId}` to set surge / closed for a date range  
6. `POST /hotels/search` with that city + some future dates to sanity-check  

Later you might use:

- `GET /admin/hotels/{hotelId}/bookings`
- `GET /admin/hotels/{hotelId}/reports?startDate=&endDate=`
- `GET /admin/hotels/{hotelId}/analytics`
- `PATCH /admin/inventory/hotels/{hotelId}` for bulk inventory tweaks

---

## Guest books and pays

1. Signup or login  
2. Search:

```json
{
  "city": "New York",
  "startDate": "2026-09-01",
  "endDate": "2026-09-03",
  "roomsCount": 1,
  "minPrice": 50,
  "maxPrice": 400,
  "sortBy": "PRICE_ASC",
  "page": 0,
  "size": 10
}
```

3. `GET /hotels/{hotelId}/info` if you want details  
4. `POST /bookings/init` → `RESERVED`, inventory reserved. You can send `Idempotency-Key` if the client might retry.  
5. `POST /bookings/{id}/addGuests` → `GUEST_ADDED`  
6. `POST /bookings/{id}/payments` → open `sessionUrl` → `PAYMENT_PENDING`  
7. Pay with Stripe (CLI forwarding to `/webhook/payment`) → `CONFIRMED`  
8. Check `GET /bookings/{id}/status` or `/users/myBookings`

Skip payment and after ~10 minutes the expiry job marks it `EXPIRED` and frees the hold. There's a warning around the 8–10 minute window.

Before paying you can still change dates (`PATCH .../dates`) or replace guests (`PUT .../guests`) while status allows it — inventory gets re-held and the amount recalculates.

---

## Cancel

`GET /bookings/{id}/cancellation-quote` then `POST /bookings/{id}/cancel`.

Default policy: full refund if check-in is 7+ days out, otherwise 50%. Stripe refund runs when there's a payment session.

---

## Review after the stay

Booking must be confirmed and check-out already passed, then `POST /bookings/{bookingId}/reviews`. List with `GET /hotels/{hotelId}/reviews` or `/users/myReviews`.

---

## Wishlist

```
POST   /wishlists/hotels/{hotelId}
GET    /wishlists
DELETE /wishlists/hotels/{hotelId}
```

---

## Promote a manager

Login as admin → `POST /admin/users/{userId}/promote-manager` with some guest's id. They can use `/admin/hotels/**` after that.

---

## Idempotency on init

```
POST /bookings/init
Idempotency-Key: my-client-retry-1
```

Same key + same body → same booking. Same key + different hotel/room/dates/count → 409.

---

## Postman order that usually works

Import `postman/`, then: Login Manager → hotel → room → activate → inventory. Switch to Login Guest → search → init → addGuests → payments. Finish payment via Stripe CLI. Then try cancel quote / wishlist / review when dates allow.

More setup detail in [SETUP.md](SETUP.md).
