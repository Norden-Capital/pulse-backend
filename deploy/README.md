# Deploy Pulse API to the VPS

Pulse shares one VPS (Ubuntu 24.04) with two other apps, **OneBase** and **Spine**.
This repo is public: no server address, user name or secret is written here.
`<VPS_HOST>` and `<VPS_USER>` below are placeholders.

## Shared VPS rules

- **Never touch OneBase or Spine**: not `/opt/onebase`, not `/opt/spine`, not their
  containers, networks, volumes or `.env` files.
- **Do not use OneBase's Caddy** (`onebase-caddy`, `/opt/onebase/Caddyfile`). It owns
  ports 80/443 and belongs to OneBase only.
- Pulse lives only in `/opt/pulse`, with its own compose project `pulse`, its own
  network `pulse` and its own volume `pulse-db-data`.
- Its own Postgres; the database port is **not** open to the outside.
- The API is on port **8091**, plain HTTP, open to the internet. This is
  **temporary** (8090 is Spine's).

## How traffic flows now (temporary)

```
phone browser → Vercel (HTTPS) → Vercel route handler proxies /api/* → http://<VPS_HOST>:8091
                                  (adds the X-Pulse-Proxy-Secret header)
```

The browser only talks to Vercel. The API answers `403` to every `/api/**` request
without the right `X-Pulse-Proxy-Secret` header, so the open port is useless to anyone
else. The actuator health endpoints are not gated (the container health check calls
them).

**No real user sign-ups until HTTPS is in front of the API.**

**Later:** our own domain + a Cloudflare Tunnel. Then set `PULSE_API_BIND=127.0.0.1`
in `.env`, redeploy, and remove the `8091/tcp` ufw rule.

Note: ports published by Docker skip ufw. We still allow `8091/tcp` in ufw so the
rules show what is really open.

Files here:

| File | What it is |
| --- | --- |
| `compose.prod.yaml` | Postgres + API + nightly backup containers |
| `backup.sh` | Nightly database backup (03:00 Morocco time, keep 14 nights) |
| `RESTORE.md` | Where backups are and how to restore one night |
| `.env.example` | Template for the server `.env` file |

## Automatic deploy

One workflow, `.github/workflows/ci-cd.yml` ("CI/CD – Backend"), with three jobs (same
layout as LMS and MasterDB):

1. `build-test` ("Build (Java 21 / Maven)") runs on every push and pull request to
   `main`: **Build JAR** (`./mvnw package -DskipTests -B`), then **Run tests**
   (`./mvnw test -B`; Testcontainers starts its own Postgres).
2. `build-push` ("Build & Push Docker image") runs only on a push to `main`, after
   `build-test` passed. It builds the image and pushes it to GHCR
   (`ghcr.io/norden-capital/pulse-backend`) with two tags: `latest` and the 7-character
   short git sha (e.g. `abc1234`).
3. `deploy` ("Deploy to VPS") runs after `build-push`. It copies `compose.prod.yaml` and
   `backup.sh` to `/opt/pulse`, pulls the short-sha image, starts it and waits until the
   API is healthy. If not healthy, the job fails and prints the API logs. Then it removes
   Pulse's own dangling images (never other apps' ones).

Deploys never run at the same time (`concurrency`) and use the GitHub environment
`production`. No secrets are sent from GitHub to the server's `.env`: it stays on the VPS.

The server logs in to GHCR with the job's own short-lived token, pulls, and logs out.
So the image can stay **private** and the server keeps no GHCR password. (For a manual
pull you need a token with `read:packages`.)

## One-time server setup

Run on the VPS as the admin user (with `sudo` if needed). Check first, change only
Pulse's own things.

1. Check the port is free: `ss -ltnp | grep ':8091' || echo free`
2. App folder and env file:
   ```bash
   mkdir -p /opt/pulse && chmod 700 /opt/pulse
   # write /opt/pulse/.env from deploy/.env.example
   # passwords and the proxy secret: openssl rand -hex 32 (one each)
   chmod 600 /opt/pulse/.env
   ```
3. Firewall: `ufw allow 8091/tcp comment 'pulse api (temporary, until tunnel)'`
4. Deploy key for GitHub: make a **new** ed25519 key `pulse_deploy` (never reuse
   Spine's), add its public key to `~/.ssh/authorized_keys` of `<VPS_USER>`, and put
   the private key in the GitHub secret `VPS_SSH_KEY`.
5. Put the same proxy secret in the Vercel project as a **server-only** env var (never
   a `NEXT_PUBLIC_*` one), used by the route handler that proxies `/api/*`.

## GitHub secrets

Repo `Norden-Capital/pulse-backend` → Settings → Secrets and variables → Actions.

| Secret | Value |
| --- | --- |
| `VPS_HOST` | `<VPS_HOST>` |
| `VPS_USER` | `<VPS_USER>` |
| `VPS_PORT` | SSH port (`22` if not changed) |
| `VPS_SSH_KEY` | private `pulse_deploy` key (whole file) |
| `VPS_HOST_FINGERPRINT` | `SHA256:...` from `ssh-keygen -lf /etc/ssh/ssh_host_ecdsa_key.pub` on the VPS |

`VPS_HOST_FINGERPRINT` must be the **ECDSA** host key. The appleboy scp/ssh actions use
a Go SSH client that negotiates the ECDSA host key, so an ed25519 fingerprint fails with
"host key fingerprint mismatch".

The DB password and the proxy secret are **not** GitHub secrets: they live only in
`/opt/pulse/.env`.

The job uses a GitHub environment named `production`. GitHub creates it on the first
run. You can add "required reviewers" to it later.

## Check it works

```bash
cd /opt/pulse
docker compose -f compose.prod.yaml ps
curl -s http://127.0.0.1:8091/actuator/health/readiness        # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8091/api/ping   # 403 (no secret)
```

## Daily use

```bash
cd /opt/pulse
docker compose -f compose.prod.yaml logs -f api      # logs
docker compose -f compose.prod.yaml restart api      # restart
```

Roll back to an older version (use its short git sha):

```bash
cd /opt/pulse
IMAGE_TAG=abc1234 docker compose -f compose.prod.yaml up -d --wait
```

Backups and restore: see [RESTORE.md](RESTORE.md).
