# Setup

Need JDK 21 and Postgres. Maven wrapper is already in the project.

## DB

```sql
CREATE DATABASE "airBnb";
```

Hibernate `ddl-auto=update` creates/updates tables on startup. Fine for local; don't treat that as a prod migration strategy.

## Config

Put secrets in `src/main/resources/application.properties` — that file is gitignored.

Something like:

```properties
spring.application.name=airBnbApp

spring.datasource.url=jdbc:postgresql://localhost:5432/airBnb
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

server.servlet.context-path=/api/v1

jwt.secretKey=ThisIsASecretKeyThatIsAtLeast32CharactersLong123456

frontend.url=http://localhost:8080
stripe.secret.key=sk_test_...
stripe.webhook.secret=whsec_...

app.seed.enabled=true
app.seed.manager.email=manager@example.com
app.seed.manager.password=Manager@123
app.seed.manager.name=Hotel Manager
app.seed.admin.email=admin@example.com
app.seed.admin.password=Admin@123
app.seed.admin.name=Platform Admin

app.holidays.dates[0]=01-01
app.holidays.dates[1]=12-25
app.holidays.dates[2]=07-04

app.cancellation.free-cancel-days=7
app.cancellation.partial-refund-percent=50

# notifications just log unless you flip this on
app.mail.enabled=false
app.mail.from=noreply@airbnbapp.local
# spring.mail.host=...
# spring.mail.port=587
# spring.mail.username=
# spring.mail.password=
```

Notes:
- `jwt.secretKey` should be long enough for HS (32+ chars)
- `frontend.url` is where Stripe sends people after checkout
- holiday dates are month-day only (`MM-dd`)

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

In Swagger hit Authorize and paste `Bearer <accessToken>`.

## Postman

Import both files under `postman/`, pick the "AirbnbApp Local" env.

Rough order that works: login as manager → create hotel → room → activate → maybe tweak inventory. Then login as guest → search → init booking → add guests → payments.

Login requests save `accessToken` into the env; the collection uses Bearer `{{accessToken}}`.

## Stripe

You can search / create hotels / init bookings without Stripe. Confirming payment needs the webhook.

```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
```

Drop the CLI `whsec_...` into `stripe.webhook.secret`, put your test secret key in `stripe.secret.key`, call payments, open the session URL, pay with a test card. Booking should flip to `CONFIRMED` when the event lands.

Don't fake Stripe signatures from Postman — verification will reject them.

## Tests

```bash
mvnw.cmd test
```

Most unit/WebMvc tests are fine without a live DB. Full Spring Boot context tests are awkward here because of Postgres-specific types (`TEXT[]`).

## If something breaks

- Won't start → missing properties file or bad DB creds
- 403 on admin routes → you're on a guest token
- 401 after a bit → access token expired, refresh or login again
- Search empty → hotel not activated, no inventory, or filters too tight
- Stuck in `PAYMENT_PENDING` → webhook not forwarding / wrong webhook secret
- Can't review → need `CONFIRMED` and check-out date already past
