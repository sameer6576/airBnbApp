# API cheat sheet

Everything is under `/api/v1`. Bodies/schemas: Swagger or the Postman collection is easier than maintaining them here.

Auth header: `Authorization: Bearer <accessToken>` unless noted public.

Responses come back in `ApiResponse` (`timeStamp`, `data`, `error`).

---

### Auth (public)

```
POST /auth/signup
POST /auth/login          → access token + refreshToken cookie
POST /auth/refresh        → new access token (uses refresh cookie)
POST /auth/logout         → clears refresh cookie
```

### Browse (mostly public)

```
POST /hotels/search
GET  /hotels/{hotelId}/info      # active hotels only
GET  /hotels/{hotelId}/reviews
```

### Bookings (logged in)

```
POST   /bookings/init                    # optional Idempotency-Key
POST   /bookings/{id}/addGuests          # optional; also ok after payment started
PUT    /bookings/{id}/guests
PATCH  /bookings/{id}/dates              # unpaid only (RESERVED / GUEST_ADDED)
POST   /bookings/{id}/payments           # → { id, url }; guests not required; extends hold
GET    /bookings/{id}/status             # includes holdExpiresAt, refundAmount when set
GET    /bookings/{id}/cancellation-quote # $0 if unpaid
POST   /bookings/{id}/cancel             # unpaid or confirmed
```

Booking statuses you will see: `RESERVED`, `GUEST_ADDED`, `PAYMENT_PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `REFUNDED`.

### Guests / profile / wishlist

```
POST|GET /guests
PUT|DELETE /guests/{guestId}

GET   /users/getMyProfile
PATCH /users/profile
GET   /users/myBookings
GET   /users/myReviews

POST|DELETE /wishlists/hotels/{hotelId}
GET         /wishlists
```

### Reviews

```
POST /bookings/{bookingId}/reviews   # after checkout, booking owner
GET  /hotels/{hotelId}/reviews
GET  /users/myReviews
```

### Admin — hotels & rooms (manager or admin)

```
POST|GET          /admin/hotels
GET|PUT|DELETE    /admin/hotels/{hotelId}   # delete refused while live bookings exist
PATCH             /admin/hotels/{hotelId}/activate
GET               /admin/hotels/{hotelId}/bookings
GET               /admin/hotels/{hotelId}/reports
GET               /admin/hotels/{hotelId}/analytics

POST|GET          /admin/hotels/{hotelId}/rooms
GET|PUT|DELETE    /admin/hotels/{hotelId}/rooms/{roomId}
```

### Admin — inventory

```
GET   /admin/inventory/rooms/{roomId}
PATCH /admin/inventory/rooms/{roomId}
PATCH /admin/inventory/hotels/{hotelId}    # bulk
```

### Admin — users (ADMIN only)

```
POST /admin/users/{userId}/promote-manager
```

### Webhook (public, but signature-checked)

```
POST /webhook/payment
```

Handles `checkout.session.completed` and `checkout.session.expired`. Events are deduplicated by Stripe event id.

### Ops

```
GET /actuator/health              # public; readiness includes db
GET /actuator/health/liveness
GET /actuator/health/readiness
```

---

Typical status codes: 400 validation, 401 auth, 403 wrong role/owner, 404 missing, 409 conflicts (inventory / idempotency / bad state). See `GlobalExceptionHandler` for the exact shape.
