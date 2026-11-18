# pulse-backend

The API for **Pulse**, a mobile-first personal fitness app (workouts, body tracking,
nutrition, progress, AI assistant) that works fully without wearables.

Java 21, Spring Boot 4, PostgreSQL 18, Flyway, Maven wrapper.

Status: **Module 2 (onboarding and profile)**. Module 1: register with email
verification, login on server sessions, password reset, passkeys (Face ID) and sign-in
history. Module 2: the public onboarding plan (`POST /api/onboarding/plan`, formula
`pulse-energy-v1`), the profile sent with register, and `GET /api/me/profile`. The API
contracts are `docs/api/auth.md` and `docs/api/onboarding-profile.md` in the project hub
(outside this repo).

## Run locally

Needs Java 21 and Docker.

```bash
./mvnw spring-boot:run
```

Spring Boot starts the local Postgres from `compose.yaml` (database/user/password
`pulse`) and Flyway runs the migrations. The API listens on `http://localhost:8080`.

```bash
curl http://localhost:8080/api/ping                      # {"status":"ok"}
curl http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
```

Settings come from environment variables; see `.env.example`.

### Proxy secret

In production the browser never calls this API directly: the Vercel frontend proxies
`/api/*` and adds the `X-Pulse-Proxy-Secret` header. Every `/api/**` request without
the right header gets `403`. Actuator health is not gated.

Locally `APP_PROXY_SECRET` is empty by default, which turns the check off (a log line
says so at startup). To test with it on:

```bash
APP_PROXY_SECRET=dev-secret ./mvnw spring-boot:run
curl -H 'X-Pulse-Proxy-Secret: dev-secret' http://localhost:8080/api/ping
```

In the `prod` profile the secret is required: the app does not start without it.

### Auth locally

- Sessions use the `SESSION` cookie; every POST/PUT/PATCH/DELETE needs the
  `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie (get it from
  `GET /api/auth/csrf`). The frontend's `lib/api.ts` does this; with curl, keep a cookie jar.
- With `RESEND_API_KEY` empty, emails are **printed to the console** instead of sent.
  Copy the verify or reset link from the log.
- Links and passkeys use `APP_FRONTEND_URL` (default `http://localhost:3000`). On
  `localhost` any port works for passkeys.

```bash
curl -c jar -b jar http://localhost:8080/api/auth/csrf
curl -c jar -b jar -H "X-XSRF-TOKEN: $(awk '/XSRF-TOKEN/ {print $7}' jar)" \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"a-long-test-password"}' \
  http://localhost:8080/api/auth/register             # 202, then see the console for the link
```

In the `prod` profile `APP_FRONTEND_URL`, `APP_MAIL_FROM` and `RESEND_API_KEY` are
required: the app does not start without them.

## Test

```bash
./mvnw verify
```

Integration tests use Testcontainers (a throwaway Postgres 18), so Docker must be
running. Emails are captured in tests, never sent. `TestPulseApplication` (in `src/test`)
runs the app against such a container.

## Deploy

Push to `main` → CI (`./mvnw verify` + `docker build`) → deploy to the VPS.
See [deploy/README.md](deploy/README.md).
