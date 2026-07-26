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
4. `POST /bookings/init` → `RESERVED`, inventory held, `holdExpiresAt` ~10 min out  
5. Optional: `POST /bookings/{id}/addGuests` — skip this if you want  
6. `POST /bookings/{id}/payments` → Stripe URL, status `PAYMENT_PENDING`, hold extended (~30 min)  
7. Pay via Stripe (CLI → `/webhook/payment`) → `CONFIRMED`  
8. `GET /bookings/{id}/status` or `/users/myBookings`  

If you never pay and the hold expires, the job marks it `EXPIRED` and frees inventory. Warning fires in the last ~2 minutes of the hold.

---

## Cancel

`GET /bookings/{id}/cancellation-quote` then `POST /bookings/{id}/cancel`.

- Unpaid (`RESERVED` / `GUEST_ADDED` / `PAYMENT_PENDING`): releases hold, no refund  
- Confirmed: free cancel if check-in ≥ 7 days out, else 50% (configurable)

---

## Review / wishlist / promote manager

Same as before — review after checkout on confirmed stays; wishlist CRUD; admin promote via `POST /admin/users/{id}/promote-manager`.

---

## Postman order

Import `postman/`: Login Manager → hotel → room → activate → Create Room After Activate (optional) → Login Guest → search → init → payments (skip guests if you want) → Stripe CLI → cancel unpaid or confirmed as needed.
