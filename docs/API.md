# API cheat sheet

Everything is under `/api/v1`. Bodies/schemas: Swagger or the Postman collection is easier than maintaining them here.

Auth header: `Authorization: Bearer <accessToken>` unless noted public.

Responses come back in `ApiResponse` (`timeStamp`, `data`, `error`).

---

### Auth (public)

```
POST /auth/signup
POST /auth/login          → access token + refreshToken cookie
POST /auth/refresh
```

### Browse (mostly public)

```
POST /hotels/search
GET  /hotels/{hotelId}/info
GET  /hotels/{hotelId}/reviews
```

### Bookings (logged in)

```
POST   /bookings/init                    # optional Idempotency-Key header
POST   /bookings/{id}/addGuests
PUT    /bookings/{id}/guests
PATCH  /bookings/{id}/dates
POST   /bookings/{id}/payments           # returns Stripe sessionUrl
GET    /bookings/{id}/status
GET    /bookings/{id}/cancellation-quote
POST   /bookings/{id}/cancel
```

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
GET|PUT|DELETE    /admin/hotels/{hotelId}
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

---

Typical status codes: 400 validation, 401 auth, 403 wrong role/owner, 404 missing, 409 conflicts (inventory / idempotency / bad state). See `GlobalExceptionHandler` for the exact shape.
