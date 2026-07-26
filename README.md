# airBnbApp

Spring Boot API for hotel search + booking (Airbnb-ish). No frontend here — hit it with Postman or Swagger.

Base URL: `http://localhost:8080/api/v1`

## Docs

- [docs/SETUP.md](docs/SETUP.md) — Postgres, properties, Stripe, how to run
- [docs/FLOWS.md](docs/FLOWS.md) — walkthroughs (manager listing, guest booking, etc.)
- [docs/DOMAIN.md](docs/DOMAIN.md) — inventory, booking statuses, pricing
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — package layout, security, jobs
- [docs/API.md](docs/API.md) — endpoint cheat sheet
- [postman/](postman/) — collection + local env

## Stack

Java 21, Spring Boot 4.1, Security + JWT, JPA/Postgres, Stripe Checkout, ModelMapper, springdoc. Maven wrapper is in the repo (`mvnw` / `mvnw.cmd`).

## Quick start

1. Create a Postgres DB named `airBnb`
2. Copy your settings into `src/main/resources/application.properties` (gitignored — see SETUP for keys)
3. `mvnw.cmd spring-boot:run`

Swagger: http://localhost:8080/api/v1/swagger-ui.html

If seed is on (`app.seed.enabled=true`):

| | email | password |
|--|--|--|
| manager | manager@example.com | Manager@123 |
| admin | admin@example.com | Admin@123 |

Signup creates a normal `GUEST`. Admin can promote someone to manager via `POST /admin/users/{id}/promote-manager`.

## How it roughly works

Managers create a hotel + rooms, then activate (that spins up ~a year of inventory). Guests search, init a booking (soft-reserves inventory), add guests, pay through Stripe. Unpaid holds die after ~10 minutes. There's also wishlist, reviews after checkout, cancel/refund rules, and a couple of admin reports.

Access token goes in `Authorization: Bearer …`. Login also sets a `refreshToken` cookie for `/auth/refresh`. Tokens expire pretty fast (~10 min) so use refresh.

Responses are wrapped:

```json
{ "timeStamp": "...", "data": {}, "error": null }
```

## Layout

```
controller/   HTTP
service/      business logic
repository/   JPA
entity/       tables + enums
dto/          request/response
security/     JWT + WebSecurityConfig
strategy/     pricing decorators
config/       OpenAPI, seed, holidays, Stripe key
advice/       ApiResponse + exception handler
```

## Caveats

- No UI, no Flyway/Docker/CI baked in
- Holidays are just `MM-dd` strings in config, not a real calendar API
- Postgres `TEXT[]` for photos/amenities — don't expect H2 to love a full context test
