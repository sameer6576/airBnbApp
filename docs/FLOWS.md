# Walkthroughs

Paths skip `/api/v1`. Use a Bearer token unless the route is public.

Seed accounts (if enabled):

- manager@example.com / Manager@123  
- admin@example.com / Admin@123  

---

## Manager lists a hotel

1. `POST /auth/login` as manager  
2. `POST /admin/hotels` — hotel starts inactive  
3. `POST /admin/hotels/{hotelId}/rooms`  
4. `PATCH /admin/hotels/{hotelId}/activate` — builds ~1 year of inventory for existing rooms  
5. Optional: add another room with `POST .../rooms` — if the hotel is already active, inventory is created automatically  
6. Optional: `PATCH /admin/inventory/rooms/{roomId}` for surge / closed dates  
7. `POST /hotels/search` to sanity-check  

Later: bookings list, reports, analytics, bulk inventory.

---

## Guest books and pays

1. Signup or login  
2. `POST /hotels/search`  
3. Optional `GET /hotels/{hotelId}/info`  
4. `POST /bookings/init` → `RESERVED`, inventory held, `holdExpiresAt` ~10 min out (optional `Idempotency-Key`)  
5. Optional: `POST /bookings/{id}/addGuests` — skip this if you want  
6. `POST /bookings/{id}/payments` → `{ id, url }`, status `PAYMENT_PENDING`, hold extended (~30 min)  
7. Pay via Stripe (CLI → `/webhook/payment`) → `CONFIRMED` (or `REFUNDED` if rooms were already gone)  
8. `GET /bookings/{id}/status` or `/users/myBookings` — status includes `holdExpiresAt` for the UI countdown  

If you never pay and the hold expires, the expiry job marks it `EXPIRED` and frees inventory. It runs every minute, so release happens within about a minute of the deadline. The warning fires in the last ~2 minutes of the hold, from a job offset 30 seconds behind the expiry one.

Both schedules are properties (`app.scheduling.booking-expiry.cron`, `app.scheduling.expiry-warning.cron`), and the dev profile disables the warning job to keep the console quiet. Set a cron to `-` to disable a job.

The Checkout Session is also pinned to the hold deadline via Stripe's `expires_at`, so the payment link dies with the hold instead of outliving it by Stripe's default 24 hours. If a payment still lands late — a delayed webhook, say — the booking tries to re-acquire the rooms and refunds automatically if they are gone, landing in `REFUNDED`.

Dates follow the usual hotel convention: check-out is not a night. A stay from the 10th to the 12th is two nights, and the 12th stays available for the next guest.

---

## Cancel

`GET /bookings/{id}/cancellation-quote` then `POST /bookings/{id}/cancel`.

- Unpaid (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING`): releases hold, no refund  
- Confirmed: free cancel if check-in ≥ 7 days out, else 50% (configurable)

---

## Review / wishlist / promote manager / logout

Review after checkout on confirmed stays; wishlist CRUD; admin promote via `POST /admin/users/{id}/promote-manager`. `POST /auth/logout` clears the refresh cookie (UI should call it on sign-out).

---

## Postman order

Import `postman/`: Login Manager → hotel → room → activate → Create Room After Activate (optional) → Login Guest → search → init → payments (skip guests if you want) → Stripe CLI → cancel unpaid or confirmed as needed.
