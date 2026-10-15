# Deploy

How rindetuplata runs, and what the owner has to decide before it runs in the real world (S16, T7e).
Nothing here creates accounts or deploys anything: it is the shape of the deployment and the exact
settings each piece needs.

## Topology

| Piece | Where | Why |
|---|---|---|
| Static site (`web/dist`) | CDN (Cloudflare Pages or equivalent) at the apex domain | Astro builds it to files; the CDN serves them, free TLS included, and the ingestion deploy hook triggers a rebuild |
| API (`Dockerfile`) | VPS with Docker Compose, behind Caddy at `api.<domain>` | It needs the PostgreSQL data, the broker and the daily ingestion close to each other |
| Postgres, RabbitMQ | Same VPS, same Compose project | One box, one `docker compose up` (S16: USD 5–8/month at launch) |

The site and the API share a registrable domain (`rindetuplata.ar` and `api.rindetuplata.ar`), which is
what lets the `SameSite=Lax` session cookie travel on the browser's credentialed `fetch` (L48). The
browser never reads cookies: it asks `GET /api/v1/auth/csrf` for the token and sends it as
`X-XSRF-TOKEN`.

## Local stack

The same Compose file as the VPS, with the site served by Caddy instead of the CDN:

```bash
mkdir -p web/dist                     # Caddy serves it; Docker would create it owned by root
docker compose --profile stack up -d --wait
(cd web && API_URL=http://localhost:4321 SITE_URL=http://localhost:4321 npm run build:api)
```

- `postgres`, `rabbitmq` and `mailpit` have no profile, so the plain `docker compose up -d postgres`
  workflow (AGENTS.md) is unchanged.
- `web` is Caddy (`deploy/Caddyfile.local`): it serves `web/dist` read-only and proxies `/api/*` to
  `api:8080`, so the whole product is one origin, exactly like production.
- The site is built *against* the running API: `build:api` refuses to run without `API_URL` and strips
  `WEB_FIXTURES`, so a fixture build can never be published by mistake.
- The end-to-end smoke does all of the above and takes the stack down again:
  `deploy/e2e.sh` (needs `cd web && npm ci && npx playwright install chromium` once).
- Locally the database has no named volume on purpose: every `down` leaves a clean database, which is
  what makes the smoke repeatable. `./mvnw spring-boot:test-run` uses its own Testcontainers
  PostgreSQL and is unaffected.
- `deploy/e2e.sh` runs in its own Compose project (`E2E_PROJECT`, default `rindetuplata-e2e`) with
  `deploy/compose.e2e.yaml`, an overlay that leaves Postgres, RabbitMQ and Mailpit's SMTP port off the
  host: its teardown removes only what it started, and a local database you started by hand keeps
  running with its data and its port. Only the site port and Mailpit's HTTP API are published, and the
  script refuses to start — naming the port — when one of those is already taken (finding R1 of
  `review-d018a62efca39d30`).

## VPS: the API

The VPS runs the same `compose.yaml` plus `deploy/compose.vps.yaml`, an overlay that makes it
production-shaped (every statement below was checked with `docker compose config`):

- Postgres and RabbitMQ are **not published at all**. The base file publishes them for local
  development with the password that is in this repository; that password must never sit on a public
  interface, and the overlay also replaces it.
- Postgres gets the `pgdata` volume and RabbitMQ its own `rabbitmq_data` volume at
  `/var/lib/rabbitmq` with a fixed `hostname` (the broker derives its node name, and so its data
  directory, from it): `docker compose down` no longer destroys the database or the queued alert
  events.
- Every service carries `restart: unless-stopped`, so a reboot or an OOM brings the API and its
  dependencies back without anyone running `up` again.
- Mailpit and the local site server are dropped (`profiles: !override`), and a `caddy` service
  terminates TLS for `api.<domain>` using `deploy/Caddyfile.vps`, which proxies only `/api/*` and
  answers 404 for every other path (the site itself lives on the CDN).
- Every production value comes from an env file that is never committed. `--env-file` only feeds
  `${VAR}` substitution, so the overlay names each variable, and a missing one fails the command
  instead of silently starting with the local default (Mailpit on 1025).

```ini
# /srv/rindetuplata/api.env — root-only, never committed
POSTGRES_PASSWORD=...            # the database user stays `rindetuplata`
RABBITMQ_DEFAULT_PASS=...        # the broker user stays `rindetuplata`
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
RINDETUPLATA_AUTH_MAGICLINKURL=https://rindetuplata.ar/ingresar
RINDETUPLATA_ALERT_MANAGEURL=https://rindetuplata.ar/alertas
RINDETUPLATA_WEB_ALLOWEDORIGINS=https://rindetuplata.ar
RINDETUPLATA_AUTH_ADMINEMAILS=mateo@example.com
RINDETUPLATA_INGEST_REBUILDHOOKURL=https://api.cloudflare.com/client/v4/pages/webhooks/deploy_hooks/...
```

```bash
cd /srv/rindetuplata                     # a checkout of this repository
chmod 600 /srv/rindetuplata/api.env
docker compose -f compose.yaml -f deploy/compose.vps.yaml \
  --profile stack --env-file /srv/rindetuplata/api.env up -d --wait
```

- The SMTP port defaults to 587 and STARTTLS is on; a provider on 465 wants
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` instead. `RINDETUPLATA_MAIL_FROM` defaults to
  `rindetuplata <no-reply@rindetuplata.ar>`.
- `RINDETUPLATA_AUTH_SECURECOOKIE=true` is set by the overlay and is mandatory once TLS terminates in
  front of the API: without it the session and the CSRF cookies travel in clear.
  `RINDETUPLATA_WEB_ALLOWEDORIGINS` must be the site's own origin, and nothing else.
- Caddy needs ports 80 and 443 open and is the only published service; the API is reachable only from
  the Compose network. `JAVA_OPTS` (for example `JAVA_OPTS=-Xmx512m`) caps the heap on a small box.
- The daily ingestion runs in LIVE mode and needs nothing extra. To replay a day of the T9 archive
  (REPLAY mode) mount a checkout of the `data` branch at `/app/archive`: uncomment the `volumes:` line
  of the `api` service in `deploy/compose.vps.yaml`.

## CDN: the site

Cloudflare Pages (or any static host with a build pipeline), pointing at this repository:

| Setting | Value |
|---|---|
| Root directory | `web` |
| Build command | `npm ci && npm run build:api` |
| Node version | 24 |
| Build-time env `SITE_URL` | `https://rindetuplata.ar` (canonicals, sitemap, `robots.txt`) |
| Build-time env `API_URL` | `https://api.rindetuplata.ar` (the API the pages are built from) |
| Build-time env `PUBLIC_API_URL` | `https://api.rindetuplata.ar` (the URL the browser island calls) |
| SPA fallback / routing | off: the site is fully prerendered, and `404.html` is the error page |

`API_URL` and `PUBLIC_API_URL` are different on purpose: the first one is only read while building,
the second one ships to the browser (`import.meta.env.PUBLIC_API_URL`). Leaving `PUBLIC_API_URL` empty
would make the browser call the CDN's own origin, where there is no API.

Authenticated flows (`/ingresar`, `/alertas`, `/admin/tasas`) need the site and the API on the same
registrable domain: the `SameSite=Lax` session cookie does not travel from a `*.pages.dev` preview
domain to `api.rindetuplata.ar`. Test the login on the production domain, not on a preview URL.

## Rebuild hook

The daily ingestion POSTs once to `rindetuplata.ingest.rebuild-hook-url` after a COMPLETED job (T4d),
which is how the static site gets today's ranking. On Cloudflare Pages that URL is the project's deploy
hook; set it as the `RINDETUPLATA_INGEST_REBUILDHOOKURL` environment variable of the API (see above).
An empty value disables the call, which is the default locally.

## Backups

The database holds only what cannot be rebuilt: users and their alert rules. The ranking and the
history come from the sources and can be re-ingested.

```bash
#!/usr/bin/env bash
# Daily dump next to the Compose project, 30 days kept; run it from cron.
#
# `set -euo pipefail` is the point of the recipe, not decoration: without it a failed
# `docker compose exec` (box down, container restarting, volume missing) left a truncated dump and the
# `find` below still ran, deleting the older dumps that were still good. The dump now lands in a
# temporary file and only replaces the day's dump once it is non-empty, so pruning can never run on a
# day whose dump failed (finding R4 of `review-d018a62efca39d30`).
set -euo pipefail

cd /srv/rindetuplata # the checkout; cron starts in the home directory
compose=(docker compose -f compose.yaml -f deploy/compose.vps.yaml --env-file /srv/rindetuplata/api.env)
backups=/srv/rindetuplata/backups
dump="$backups/rindetuplata-$(date +%F).dump"

mkdir -p "$backups"
trap 'rm -f "$dump.tmp"' EXIT

"${compose[@]}" exec -T postgres pg_dump -U rindetuplata -Fc rindetuplata > "$dump.tmp"

if [ ! -s "$dump.tmp" ]; then
  echo "pg_dump produced an empty file; keeping every existing dump" >&2
  exit 1
fi

mv "$dump.tmp" "$dump"
find "$backups" -name 'rindetuplata-*.dump' -mtime +30 -delete

# Restore (a new or broken volume), same pgdata volume in place:
docker compose -f compose.yaml -f deploy/compose.vps.yaml --env-file /srv/rindetuplata/api.env \
  exec -T postgres pg_restore -U rindetuplata -d rindetuplata --clean \
  < /srv/rindetuplata/backups/rindetuplata-YYYY-MM-DD.dump
```

Copy the dumps off the box (object storage, `rclone`, or a second host): a backup on the same disk
survives a bad deploy, not a lost VPS. The `pgdata` volume is the only stateful thing to preserve.

## What the owner still has to decide

1. **Domain.** `rindetuplata.ar` is assumed everywhere (`SITE_URL`, TLS, magic links). Registering it,
   or choosing another one, is a purchase decision, not a deploy step.
2. **SMTP provider.** The VPS section above uses a placeholder. Any provider that speaks SMTP with a
   login works; the sender is `rindetuplata.mail.from` (default `rindetuplata <no-reply@rindetuplata.ar>`),
   and the domain needs SPF/DKIM records for the magic links to land in the inbox instead of spam.
3. **VPS region.** L-H: the Argentine public APIs (BCRA, ArgentinaDatos) block some foreign IPs —
   they did not block GitHub's US runners (L23), but the region must be checked from the actual box
   before paying for it. Any provider with Docker and 2 GB of RAM is enough.
4. **First admin.** `RINDETUPLATA_AUTH_ADMINEMAILS` must name a real mailbox before `/admin/tasas` is
   usable; the local default (`admin@example.com`) is a harmless placeholder.
