# Setup

Need JDK 21 and Postgres. Maven wrapper is already in the project.

## DB

```sql
CREATE DATABASE "airBnb";
```

Flyway owns the schema. Scripts live in `src/main/resources/db/migration/`; `V1__baseline_schema.sql` creates the current tables. Hibernate then runs with `ddl-auto=validate` and refuses to start if the database does not match the entities.

Add a new versioned file (`V2__…sql`) for any later column, index or constraint change. Do not set `ddl-auto=create` — that drops data on restart.

If this database already has tables from the old Hibernate `ddl-auto` setup, the first boot baselines at V1 and does not re-run CREATE TABLE. For a clean start: `DROP DATABASE "airBnb"; CREATE DATABASE "airBnb";`

## Config

Three files, split by whether a file can hold a secret:

| file | committed? | what goes in it |
|------|-----------|-----------------|
| `application.properties` | yes | shared, non-secret settings; defaults the active profile to `dev` |
| `application-prod.properties` | yes | production, every value from an environment variable |
| `application-dev.properties` | **no**, gitignored | local database credentials, dev keys |

To get started:

```bash
cp src/main/resources/application-dev.properties.example src/main/resources/application-dev.properties
```

Then fill in your Postgres password, a `jwt.secretKey` of 32+ characters, and your Stripe test keys. Generate a signing key with:

```bash
openssl rand -base64 48
```

Anything shared — context path, hold durations, cancellation policy, scheduling, holidays — already lives in `application.properties` and needs no change.

Running the prod profile needs these environment variables: `DB_HOST_URL`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `FRONTEND_URL`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`. Activate it with `SPRING_PROFILES_ACTIVE=prod`.

Notes:
- `jwt.secretKey` must be at least 32 characters for HMAC. Never reuse a value that has appeared in a repository.
- `frontend.url` is the SPA origin (`http://localhost:5173`), not the API port — Stripe returns the guest there after checkout.
- Holiday dates are month-day only (`MM-dd`), comma-separated in `app.holidays.dates`.
- Hold minutes control when unpaid bookings auto-expire. Keep `payment-hold-minutes` at 30 or above: the Stripe Checkout Session is pinned to that deadline and Stripe refuses a session shorter than 30 minutes.
- Every scheduled job's cron is a property; set one to `-` to disable it. The dev profile already disables the pricing sweep and the expiry-warning job.

## Run

```bash
# Windows
mvnw.cmd spring-boot:run

# Unix
./mvnw spring-boot:run
```

Port 8080, context `/api/v1`.

- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html  
- OpenAPI JSON: http://localhost:8080/api/v1/v3/api-docs  
- Stayline UI: see [frontend/README.md](../frontend/README.md) (`npm run dev` → http://localhost:5173)

The Swagger UI page itself is public. Endpoints are not: call `POST /auth/login`, copy `data.accessToken`, hit **Authorize** and paste the raw token only. The scheme is `type: http, scheme: bearer`, so Swagger adds the `Bearer ` prefix itself — pasting `Bearer <token>` sends it twice and every call comes back 401.

## Frontend (Stayline)

```bash
cd frontend
npm install
npm run dev
```

Log in as manager seed user to create hotels, or sign up as a guest to book.

## Postman

Import both files under `postman/`, pick the "AirbnbApp Local" env.

Rough order that works: login as manager → create hotel → room → activate → optional create room after activate. Then login as guest → search → init → payments (guests optional) → Stripe. Cancel works on unpaid holds too.

Login requests save `accessToken` into the env; the collection uses Bearer `{{accessToken}}`.

## Stripe

You can search / create hotels / init bookings without Stripe. Confirming payment needs the webhook.

```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
```

Drop the CLI `whsec_...` into `stripe.webhook.secret`, put your test secret key in `stripe.secret.key`, call payments, open the session URL, pay with a test card. Booking should flip to `CONFIRMED` when the event lands.

Events are deduplicated on Stripe's event id, so redelivering one is safe. The session expires with the booking hold, and a payment that lands after the hold died will try to re-acquire the rooms and refund automatically if they are gone — that booking ends up `REFUNDED`.

Don't fake Stripe signatures from Postman — verification will reject them.

## Tests

```bash
mvnw.cmd test
```

Most unit/WebMvc tests are fine without a live DB. Full Spring Boot context tests are awkward here because of Postgres-specific types (`TEXT[]`).

## If something breaks

- Won't start → no `application-dev.properties` (copy the `.example`) or bad DB creds. `GET /api/v1/actuator/health` reports `DOWN` when Postgres is unreachable.
- 403 on admin routes → you're on a guest token
- 401 after a bit → access token expired, refresh or login again
- Search empty → hotel not activated, no inventory, or filters too tight
- Stuck in `PAYMENT_PENDING` → webhook not forwarding / wrong webhook secret
- Can't review → need `CONFIRMED` and check-out date already past
