# pulse-backend

The API for **Pulse**, a mobile-first personal fitness app (workouts, body tracking,
nutrition, progress, AI assistant) that works fully without wearables.

Java 21, Spring Boot 4, PostgreSQL 18, Flyway, Maven wrapper.

Status: **Phase 0 (setup)**. There is only a health check and `GET /api/ping`.

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

## Test

```bash
./mvnw verify
```

Integration tests use Testcontainers (a throwaway Postgres 18), so Docker must be
running. `TestPulseApplication` (in `src/test`) runs the app against such a container.

## Deploy

Push to `main` → CI (`./mvnw verify` + `docker build`) → deploy to the VPS.
See [deploy/README.md](deploy/README.md).
