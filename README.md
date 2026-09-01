# airBnbApp

Spring Boot API for hotel search + booking, plus a React UI (**Stayline**) in [`frontend/`](frontend/).

**API:** `http://localhost:8080/api/v1`  
**UI:** `http://localhost:5173`

## Docs

- [docs/COMPLETE_GUIDE.md](docs/COMPLETE_GUIDE.md) — **one-shot: entire project explained**
- [docs/INTERVIEW.md](docs/INTERVIEW.md) — SDE2 interview walkthrough
- [docs/INTERVIEW_QUESTIONS.md](docs/INTERVIEW_QUESTIONS.md) — interview Q&A around this project
- [docs/SETUP.md](docs/SETUP.md) — Postgres, properties, Stripe, frontend
- [frontend/README.md](frontend/README.md) — Stayline UI
- [docs/FLOWS.md](docs/FLOWS.md) — walkthroughs
- [docs/DOMAIN.md](docs/DOMAIN.md) — inventory, booking statuses, pricing
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — package layout, security, jobs
- [docs/API.md](docs/API.md) — endpoint cheat sheet
- [postman/](postman/) — collection + local env

## Stack

Java 21, Spring Boot 4.1, Security + JWT, JPA/Postgres, Flyway, Stripe Checkout, ModelMapper, springdoc. UI: React + Vite + TypeScript. Maven wrapper in the repo (`mvnw` / `mvnw.cmd`).

## Quick start

1. Create a Postgres DB named `airBnb`
2. `cp src/main/resources/application-dev.properties.example src/main/resources/application-dev.properties` and fill in your DB password, a 32+ char `jwt.secretKey`, and Stripe test keys. That file is gitignored; shared settings already live in `application.properties`. See [SETUP](docs/SETUP.md).
3. API: `mvnw.cmd spring-boot:run`
4. UI: `cd frontend && npm install && npm run dev`

Swagger: http://localhost:8080/api/v1/swagger-ui.html

If seed is on (`app.seed.enabled=true`):

| | email | password |
|--|--|--|
| manager | manager@example.com | Manager@123 |
| admin | admin@example.com | Admin@123 |

Signup creates a normal `GUEST`. Admin can promote someone to manager via `POST /admin/users/{id}/promote-manager`.

## How it roughly works

Managers create a hotel + rooms, then activate (that spins up ~a year of inventory). Guests search, init a booking (soft-reserves inventory), optionally add guests, pay through Stripe. Unpaid holds die after ~10 minutes (payment extends to ~30). There's also wishlist, reviews after checkout, cancel/refund rules, late-payment auto-refund (`REFUNDED`), and admin reports.

Access token goes in `Authorization: Bearer …`. Login also sets a `refreshToken` cookie for `/auth/refresh`; `POST /auth/logout` clears it. Tokens expire pretty fast (~10 min) so use refresh.

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

- No Docker/CI baked in. Schema changes go through Flyway (`src/main/resources/db/migration`); Hibernate `ddl-auto=validate` will not start against a mismatch.
- Scheduled jobs assume a single instance — there's no distributed lock, so two replicas would duplicate the pricing sweep.
- Holidays are just `MM-dd` strings in config, not a real calendar API
- Postgres `TEXT[]` for photos/amenities — don't expect H2 to love a full context test, which is why the context test is `@Disabled`
- Search filters and sorts in memory after loading the matching set, then pages. Fine at this size, not at scale.
