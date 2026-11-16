# Backups and restore

## What is backed up

The `backup` service in `compose.prod.yaml` runs every night at **03:00 Morocco time**
(`TZ=Africa/Casablanca`). It keeps the **last 14 nights** on this VPS only, in
`/opt/pulse/backups` (`chmod 700`):

```
/opt/pulse/backups/
  backup.log              one line per run (OK or FAILED)
  2026-11-13/
    db.dump               the database (pg_dump custom format, compressed)
```

If the server was off at 03:00, the backup runs as soon as it is back.
A failed backup only writes a `FAILED` line. The app keeps running.

Note: the backups are on the same server as the app. If the server is lost, the
backups are lost too. (Off-server copies are a later step.)

## Check it works

```bash
cd /opt/pulse
tail -5 backups/backup.log
ls -l backups/
docker compose -f compose.prod.yaml logs --tail 20 backup
```

Back up right now (replaces today's folder):

```bash
docker compose -f compose.prod.yaml exec backup bash /usr/local/bin/pulse-backup.sh now
```

## Restore one night

Run on the VPS as the admin user. Only Pulse's containers are touched. Pick the night:

```bash
cd /opt/pulse
D=2026-11-13            # folder name in /opt/pulse/backups
ls -l backups/$D
```

### 1. Stop the API and the backup (the database stays on)

```bash
docker compose -f compose.prod.yaml stop api backup
```

### 2. Safety copy of what is there now

```bash
mkdir -p /opt/pulse/before-restore && chmod 700 /opt/pulse/before-restore
docker compose -f compose.prod.yaml exec -T db sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > /opt/pulse/before-restore/db.dump
```

### 3. Restore the database

First empty the database (drops ALL current tables, also ones newer than the backup,
so the next app start can run its migrations cleanly), then load the backup.

```bash
docker compose -f compose.prod.yaml exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "drop schema public cascade; create schema public;"'
docker compose -f compose.prod.yaml exec -T db sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' \
  < backups/$D/db.dump
```

### 4. Start again and check

```bash
docker compose -f compose.prod.yaml up -d --wait
curl -s http://127.0.0.1:8091/actuator/health/readiness
```

## Look inside a backup without touching the app

```bash
docker run --rm -v /opt/pulse/backups/$D:/bk:ro postgres:18 pg_restore --list /bk/db.dump | head
```
