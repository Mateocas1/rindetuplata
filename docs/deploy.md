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
  workflow is unchanged.
- `web` is Caddy (`deploy/Caddyfile.local`): it serves `web/dist` read-only and proxies `/api/*` to
  `api:8080`, so the whole product is one origin, exactly like production.
- The site is built *against* the running API: `build:api` refuses to run without `API_URL` and strips
  `WEB_FIXTURES`, so a fixture build can never be published by mistake.
- The end-to-end smoke brings the same stack up and takes it down again: `deploy/e2e.sh`. It builds the site in
  fixture mode (`WEB_FIXTURES=1 npm run build`), not with `build:api`: the fresh stack has ingested nothing, so the
  build gate (below) would refuse it, and the smoke tests sign-in and alerts, not rankings. The browser still calls the
  stack's own API through Caddy, because the account pages use `PUBLIC_API_URL`, which is unset (same origin). The gate
  has no off-switch; `web/test/build-gate.test.ts` checks both halves. It needs
  `cd web && npm ci && npx playwright install chromium` once.
- Locally the database has no named volume on purpose: every `down` leaves a clean database, which is
  what makes the smoke repeatable. `./mvnw spring-boot:test-run` uses its own Testcontainers
  PostgreSQL and is unaffected.
- `deploy/e2e.sh` runs in its own Compose project (`E2E_PROJECT`, default `rindetuplata-e2e`) with
  `deploy/compose.e2e.yaml`, an overlay that leaves Postgres, RabbitMQ and Mailpit's SMTP port off the
  host: its teardown removes only what it started, and a local database you started by hand keeps
  running with its data and its port. Only the site port and Mailpit's HTTP API are published, and the
  script refuses to start — naming the port — when one of those is already taken (finding R1 of
  `review-d018a62efca39d30`).

## CI for the deploy files

Two GitHub Actions workflows and Dependabot watch what the Java and web test workflows do not (T4e, S4):

| Workflow | Runs when | What it proves |
|---|---|---|
| `.github/workflows/image-build.yml` | a PR or a push to `main` touches `Dockerfile`, `.dockerignore`, `src/**` or `pom.xml` | `docker build` of the API image succeeds. Nothing is pushed |
| `.github/workflows/deploy-test.yml` | a PR or a push to `main` touches `deploy/**` or `compose.yaml` | every `deploy/test/*-test.sh` passes, `compose.yaml` renders, and `deploy/compose.vps.yaml` renders with fake env values and refuses an empty env file |

- `deploy-test.yml` finds the scripts by the glob `deploy/test/*-test.sh`, so a new test is picked up without
  editing the workflow. It installs Node 24 and runs `npm ci` in `web/` first, because `deploy/preview.sh up` (driven
  by `preview-script-test.sh`) refuses to start without `web/node_modules`.
- The fake values are written to a scratch env file inside the job. When the overlay gains a new
  `${VAR:?...}` guard, add the variable to that file in the workflow, or the render step fails.
- Run the same checks locally: `docker build -t rindetuplata-api:ci .`, then `for t in deploy/test/*-test.sh; do bash "$t"; done`
  (after `cd web && npm ci`).
- `.github/dependabot.yml` opens weekly update PRs for Maven (`/`), npm (`/web`), GitHub Actions and the base images
  of the API `Dockerfile` (`/`, the only Dockerfile). Minor and patch updates of one ecosystem arrive as a single
  grouped PR; a major update is its own PR. At most three PRs per ecosystem (two for the Dockerfile) stay open. Every
  action in the workflows is pinned by commit SHA with a version comment, and Dependabot bumps both together.
  The images Compose runs as published (`postgres`, `rabbitmq`, `caddy`, the preview's `node:24` rebuilder) are not
  watched: bump their tags by hand.
- Three major updates are ignored on purpose, each with its reason next to the rule: `eclipse-temurin` (a Java major
  moves the build image, the runtime image and `java.version` together, on an LTS release), `@types/node` (the
  typings follow the Node 24 runtime) and `typescript` (`@astrojs/check` does not accept TypeScript 7 yet; lift the
  rule when it does). Every other major still arrives as its own PR.

## Private preview (Tailscale Funnel)

The whole product — login, alerts, simulator, daily ingestion — on the owner's own machine, published at
one public HTTPS URL through Tailscale Funnel (S20): USD 0, no domain, no VPS, no CDN. It exists to test
with a few people before spending money, so it is deliberately unlisted: every response carries
`X-Robots-Tag: noindex, nofollow` (`deploy/Caddyfile.preview`), the site is the same Astro build but
with its canonicals, sitemap and `robots.txt` pointing at the Funnel URL, and Mailpit is off, so magic
links and alert emails go to real inboxes through a real SMTP account.

`deploy/preview.sh` runs it in its own Compose project (`rindetuplata-preview`) with `compose.yaml` +
`deploy/compose.preview.yaml`. The overlay, all checked with `docker compose config` by
`deploy/test/preview-config-test.sh`:

- Postgres and RabbitMQ are **not published at all** (a Funnel exposes the machine, and the base file's
  password is in this repository), and both get named volumes — `pgdata` at `/var/lib/postgresql`, the
  path the `postgres:18` image really declares, and `rabbitmq_data` at `/var/lib/rabbitmq` with a fixed
  hostname — so `down` keeps the accounts, the alert rules and the queued events.
- `web` (Caddy) publishes **only** `127.0.0.1:${WEB_PORT}`: the Funnel process connects to the loopback
  address, so nothing else on the LAN can reach the site. It serves `deploy/Caddyfile.preview` — the
  local Caddyfile plus the `noindex` header — from `web/.site`, the build root the sidecar publishes
  into, with `root /srv/current`: a rebuild is never visible as a half-written site.
- The preview Caddyfile also configures `servers { trusted_proxies static private_ranges }` and sets
  `header_up X-Forwarded-Proto https` on the API proxy. Funnel terminates TLS and connects from the
  loopback address with the visitor in `X-Forwarded-For`; without a trusted-proxy list Caddy overwrites
  both headers with its own peer and its own `http` view of the request, so every visitor would share one
  magic-link rate-limit bucket and the API would distrust its own `Secure` cookies.
- The API is production-shaped: STARTTLS SMTP from the env file, `secure-cookie=true`, and magic link,
  alert page and allowed browser origin all derived from one `PREVIEW_URL` — one origin, so the
  `SameSite=Lax` session cookie and the CSRF token travel exactly as in production.
- `mailpit` is dropped (`profiles: !override`), and every service carries `restart: unless-stopped`.
- `rebuilder`: a small Node sidecar (`deploy/rebuilder/server.mjs`, dependency-free) that answers the
  T4d rebuild hook. It publishes no port; only the API reaches it. It mounts the host's `web/`, so the
  build reuses `web/node_modules`, and it runs as the host user so the files it writes stay writable.
  Every build goes to its own directory `web/.site/build-<n>` (`node_modules/.bin/astro build --outDir
  .site/<build>`), and only a build that exited 0 is published, by swapping the `web/.site/current`
  symlink with a single `rename`. The last two builds are kept; a failed build is deleted and the
  previous build keeps serving. The sidecar drives Astro directly because the project's `npm run
  build:api` always builds into `dist`; the guards of that script (`API_URL` and `SITE_URL` mandatory,
  `WEB_FIXTURES` stripped) are replicated by the sidecar, which refuses to start without either.

The env file is `deploy/preview.env` (gitignored, template `deploy/preview.env.example`): the database
and broker passwords, the SMTP account, `PREVIEW_URL` and the admin emails. `up` refuses to start
without it, naming the file and every key that is missing or empty, and every command that reads it
refuses a `PREVIEW_URL` that would silently break the one origin: one that does not start with
`https://` (the session and CSRF cookies are `Secure`) or that ends in `/` (Compose appends the routes to
it, so `.../` turns the magic link into `...//ingresar` and makes the allowed browser origin disagree
with the page's). The refusal names the key and prints the corrected value.

### Once, in the Tailscale and Google consoles (owner)

1. Tailscale admin console → **DNS** → enable **HTTPS Certificates** (a Funnel URL is HTTPS and needs a
   certificate for the node's `*.ts.net` name).
2. Tailscale admin console → **Access controls**: the node attribute `funnel` must be allowed. The
   default policy allows it for the tailnet's own nodes; a restrictive ACL can remove it.
3. Gmail: 2-Step Verification on, then an **App Password** (myaccount.google.com/apppasswords). The
   account password is refused on port 587; the app password is what `SPRING_MAIL_PASSWORD` wants.

The public URL is `https://<node>.<tailnet>.ts.net`, printed by `tailscale funnel status` and by
`deploy/preview.sh funnel-on`; `PREVIEW_URL` must be exactly that URL, with no trailing slash.

### Commands

```bash
cp deploy/preview.env.example deploy/preview.env
chmod 600 deploy/preview.env
$EDITOR deploy/preview.env         # PREVIEW_URL, the SMTP app password, the admin email

deploy/preview.sh up               # API image, stack up --wait, first site build through the sidecar
deploy/preview.sh funnel-on        # serve 127.0.0.1:${WEB_PORT} on the Funnel URL
deploy/preview.sh status           # containers, Funnel state, last site rebuild
deploy/preview.sh logs [service]   # follow the logs (all services by default)
deploy/preview.sh rebuild          # ask the sidecar for a site rebuild right now
deploy/preview.sh funnel-off       # stop sharing; the stack keeps running on the loopback
deploy/preview.sh down             # stop the stack and stop sharing; the volumes keep the data
```

`up` builds the API image, brings the stack up with `--wait`, and then asks the rebuild sidecar for the
first site build and waits for it (up to `PREVIEW_BUILD_TIMEOUT` seconds, 600 by default). The host never
runs the site build: one builder, one output directory, so the Funnel never serves a half-written
directory and a startup ingestion (T14a) can trigger its own rebuild without racing anything. `funnel-off`
is the one command that reads no env file, and it always runs `tailscale funnel reset`: an unreachable or
broken secrets file must never be able to keep the machine exposed. `down` turns the Funnel off first,
before it validates the env file, and never passes `--volumes`, so the data survives stopping the
preview; only `docker compose --project-name rindetuplata-preview down --volumes` would delete it.

Trying it without the real SMTP account: the overlay drops Mailpit, but the `mailpit` profile is still
there. Add it with `PREVIEW_PROFILES=mailpit deploy/preview.sh up` (or `--profile mailpit` on a manual
`docker compose` call — a `--profile` flag makes Compose ignore `COMPOSE_PROFILES`) and point the env
file at it with `SPRING_MAIL_HOST=mailpit` and `SPRING_MAIL_PORT=1025`; magic links then land in
Mailpit's UI on `127.0.0.1:8025` instead of real inboxes. That is how the T14c fix round dry-ran these
commands, on a scratch project name and a scratch env file, never on `rindetuplata-preview`.

`/actuator/health` reports the database, the broker **and the mail server**, and `up -d --wait` waits
for it: if `up` ends with `dependency failed to start: container … is unhealthy` and the API log shows an
SMTP error, the app password or the SMTP host in `deploy/preview.env` is wrong. The stack itself is up
(the app serves requests either way); fix the env file and run `up` again.

The exposure rules, the rebuild sidecar's queueing and the script's refusals are checked without
starting anything real by `deploy/test/preview-config-test.sh`, `deploy/test/preview-rebuilder-test.sh`
and `deploy/test/preview-script-test.sh`.

### What happens every day

- **19:30 Argentina** (`rindetuplata.ingest.cron`): the daily ingestion runs in the API container. When
  the job COMPLETES, the T4d hook POSTs `http://rebuilder:8787/` and the sidecar rebuilds the site, so the
  published ranking is today's a minute or two later. The sidecar runs one build at a time and collapses
  every request that arrives during a build into exactly one rerun; visitors keep getting the current
  build until the new one exits 0, so a slow or failed build costs nothing but a delay. Each run, its
  exit code and what it published are in `deploy/preview.sh logs rebuilder`, and `deploy/preview.sh
  status` reads the sidecar state (current build, kept builds, last run).
- **After every restart** of the API, `RINDETUPLATA_INGEST_RUNONSTARTUP=true` (T14a) ingests today once
  if it has not been ingested yet and triggers the same rebuild: a fresh volume has data before the
  evening, and a restart never leaves the site a day behind. The API does not retry its hook, and its
  startup ingestion can finish before the rebuilder listens, so the rebuilder also builds once when it
  starts (`REBUILDER_BUILD_ON_START=true`); a hook that arrives during that build queues one more.
- **Banking the data**: the ingestion is idempotent per data date, so a restart, a re-run or a rebuild
  never duplicates a day.
- **What a rebuild publishes**: nothing changes until a build exits 0 (the atomic `current` swap); a
  failed build is discarded and the previous one keeps serving. `web/.site` holds the current build and
  the previous one, so there is always a site to roll back to by hand if needed.

### Limits

- **The machine must stay on, awake and connected to Tailscale.** This is the owner's computer, not a
  VPS: suspend, lid close, a reboot or a Tailscale logout takes the public URL down (the stack comes back
  on its own thanks to `restart: unless-stopped` and Tailscale, once the machine is on again).
- **One port.** Funnel serves HTTPS on 443 and other protocols on 8443/10000; the stack itself publishes
  exactly one host port, on 127.0.0.1. Postgres, RabbitMQ and the rebuilder are reachable from the
  Compose network only, and the API only through Caddy.
- **No domain, no CDN.** `PREVIEW_URL` is a `*.ts.net` name: renaming the node, changing the tailnet or
  revoking the device changes the URL, and every magic link already sent becomes wrong. Canonicals and the
  sitemap point at that name, not at `rindetuplata.ar`.
- **Unlisted, not private.** Anyone with the URL can use the site (the Funnel is public by design);
  access is the owner's control of who receives the link, exactly as S20 states. Everything else in the
  VPS section applies unchanged: single API instance, per-IP magic-link limit, admin role by email list.
- **Not a launch.** Cloudflare Pages, the `api.` subdomain and a real domain stay the production plan
  (above); the preview only borrows their shape.

### Stopping sharing

```bash
deploy/preview.sh funnel-off        # the public URL stops answering; localhost keeps working
# or, if the stack should stop too:
deploy/preview.sh down
```

`tailscale funnel status` shows whether anything is still served (its output names the public URL while
`deploy/preview.sh status` reports `on` only when it sees a `Funnel on` line), and `tailscale funnel
reset` is what `funnel-off` runs — unconditionally, whether or not a `Funnel on` line is reported, since
resetting a Funnel that is already off is harmless and failing to reset a live one leaves this machine
exposed. It removes the Funnel configuration, so the `*.ts.net` name stops answering until `funnel-on` is
run again.

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
- **Memory limits and log rotation (T4c).** The overlay splits a 2 GB box: Postgres 512m, RabbitMQ 384m, the API
  768m and Caddy 128m (1,792m in all, the rest is for the OS and Docker). The JVM keeps
  `-XX:MaxRAMPercentage=75` and sizes its heap from the container limit, so the API heap is about 576m and it can no
  longer starve the database or the broker; an overrun is killed (and restarted) inside its own container. Every
  service also logs through `json-file` rotated at 10m x 3, so container logs cannot fill the disk. Both are
  checked per service by `deploy/test/compose-limits-test.sh` (`docker compose config` with fake values). On a
  bigger box raise the numbers in `deploy/compose.vps.yaml`, keeping the API at 75% of its limit or less for heap.
- Mailpit and the local site server are dropped (`profiles: !override`), and a `caddy` service
  terminates TLS for `api.<domain>` using `deploy/Caddyfile.vps`, which proxies only `/api/*`, `/v3/api-docs`, `/mcp`
  and the two health URLs `/actuator/health` and `/actuator/health/ingestion` (the ones an external uptime monitor and
  the post-restore check read; the rest of `/actuator/*` stays unreachable from outside) and answers 404 for every
  other path (the site itself lives on the CDN). `deploy/test/caddy-routes-test.sh` checks this set in all three
  Caddyfiles.
- Every production value comes from an env file that is never committed. `--env-file` only feeds
  `${VAR}` substitution, so the overlay names each variable, and a missing one fails the command
  instead of silently starting with the local default (Mailpit on 1025).

```ini
# /srv/rindetuplata/api.env — root-only, never committed; deploy/vps.env.example is the template
COMPOSE_PROFILES=stack           # loads api and caddy in every Compose command that reads this file; keep it
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
install -m 600 deploy/vps.env.example /srv/rindetuplata/api.env     # then fill every value
docker compose -f compose.yaml -f deploy/compose.vps.yaml \
  --env-file /srv/rindetuplata/api.env up -d --wait
```

`COMPOSE_PROFILES=stack` in the env file is what puts `api` and `caddy` in the project: `compose.yaml` keeps `api` behind
the `stack` profile and the overlay makes `caddy` depend on it, so a Compose command that reads the overlay without the
profile (a restore, a `stop api`, the nightly backup's `exec`) is rejected with `service "caddy" depends on undefined
service "api": invalid compose project`. `deploy/test/restore-drill-test.sh` runs every documented command of the drill
with `--dry-run` against the template, and `backup.sh` and `restore.sh` with their defaults, and fails on that error.
(An explicit `--profile` flag overrides `COMPOSE_PROFILES`, so do not mix them.)

- The SMTP port defaults to 587 and STARTTLS is on; a provider on 465 wants
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` instead. `RINDETUPLATA_MAIL_FROM` defaults to
  `rindetuplata <no-reply@rindetuplata.ar>`.
- `RINDETUPLATA_AUTH_SECURECOOKIE=true` is set by the overlay and is mandatory once TLS terminates in
  front of the API: without it the session and the CSRF cookies travel in clear.
  `RINDETUPLATA_WEB_ALLOWEDORIGINS` must be the site's own origin, and nothing else.
- **The `prod` profile (T4b).** The overlay sets `SPRING_PROFILES_ACTIVE=prod` on the API
  (`application-prod.properties`). Under that profile the API **refuses to start**, and its error lists every
  offending property at once, when any of these is still a development default:
  `rindetuplata.auth.secure-cookie` is `false`; `rindetuplata.web.allowed-origins`,
  `rindetuplata.auth.magic-link-url` or `rindetuplata.alert.manage-url` is blank, not `https`, or points at
  `localhost` (also `*.localhost`, `127.x.x.x`, `::1`, `0.0.0.0`); `spring.mail.host` is blank or one of those local
  hosts (the Mailpit default); `rindetuplata.auth.admin-emails` names nobody; `rindetuplata.api.rate-limit.bypass-token`
  (`API_BUILD_TOKEN`) is set but, trimmed, shorter than 32 characters (blank keeps the bypass off). The check runs
  before the database, the broker or Flyway are touched, reads the same environment variables the API binds, and never
  prints a token. The profile also turns `secure-cookie` on by default. Without the profile (local development,
  tests, the preview) nothing is checked. `deploy/test/prod-profile-test.sh` renders the overlay with fake values and
  asserts the profile reaches the container.
- **Ingestion freshness and monitoring (T4b).** The `ingestionFreshness` health contributor reads the newest
  `source_run` row that is `LIVE` and `OK` or `DEGRADED` (both stored rows from a source that answered; `FAILED`
  stored nothing, and `REPLAY` and backfill rows are old history that says nothing about today's fetch). Any source
  counts, so check `source_run` itself for a single failing source. The data is fresh until
  `RINDETUPLATA_INGEST_STALEAFTER` (default `36h`) after the run the cron was expected to make for that capture date
  (19:30 `America/Argentina/Buenos_Aires`, so until 07:30 two days later: one missed run is tolerated, two are not);
  past that, or with no successful run recorded at all, its status is the custom `STALE`, ordered between `DOWN` and
  `UP`. `STALE` is HTTP **200** on `/actuator/health`, so Docker's `HEALTHCHECK`, `depends_on: service_healthy` and
  Caddy keep working while the data is old (a stale site is better than no site); it is HTTP **503** on the group
  `/actuator/health/ingestion`, the URL an external uptime monitor should watch. The group (public, like health) shows
  `lastSuccessDate`, `lastSuccessAt`, `ageSeconds`, `staleAfter` and `staleAt`; the main endpoint shows the bare status.
  (The contributor is `ingestionFreshness` and the group `ingestion`: Spring Boot refuses one name for both.) The
  Micrometer gauge `rindetuplata.ingest.last.success.age` (base unit seconds, NaN when there is no success) carries the
  same age for any registry added later; no metrics endpoint is exposed today.
- Caddy needs ports 80 and 443 open and is the only published service; the API is reachable only from
  the Compose network. `JAVA_OPTS` (for example `JAVA_OPTS=-Xmx512m`) caps the heap on a small box.
- **Rate limit of the public read API (optional settings).** `GET /api/v1/ranking`, `/api/v1/simulations`,
  `/api/v1/instruments/**` and `/api/v1/reports/**` (the monthly reports) share one bucket per client address:
  a burst of 120 requests, refilled 2 per second;
  beyond that the answer is a 429 `ProblemDetail` with `Retry-After`. The address is the `X-Forwarded-For`
  that Caddy sends (`server.forward-headers-strategy=native`). The counters live in the API's memory, so
  the limit assumes the single API instance. `RINDETUPLATA_API_RATELIMIT_CAPACITY` and
  `RINDETUPLATA_API_RATELIMIT_REFILLPERSECOND` change the two numbers. An IPv6 client is counted per /64
  prefix (a subscriber controls a whole /64, so one bucket per address would let it rotate past the limit);
  IPv4 clients are counted per address. The API tracks at most
  `RINDETUPLATA_API_RATELIMIT_MAXTRACKEDADDRESSES` clients (default 100000): when the table is full it
  first drops the buckets that have refilled completely and, if it is still full, answers the same 429 to
  any client it does not know yet, while the ones it already tracks keep being served. That fail-closed choice is a trade-off: a sustained flood from many addresses can keep new visitors out for as long as it lasts (each refused visitor gets a `Retry-After` of up to 60 s), while visitors already being served are unaffected. `clientOf` takes at most 100 ids (400 beyond that, repeats counted; the MCP tools refuse the same input), `POST /mcp` reads at most 64 KiB of body (413 beyond that), `days` is capped at 1825 and
  `amount` at 1000000000000 (a 400 naming the parameter), because the simulation cost grows with `days`.
- **API keys and plans (T6d).** A request with a valid `X-Api-Key` on the public read API is limited by the plan of
  its key instead of its address: a bucket per key and a quota of counted requests per Argentine day
  (`America/Argentina/Buenos_Aires`). `FREE` is every key a user creates: a bucket of 600 refilled 10 per second and
  20000 requests per day. `PARTNER` is a key the operator promotes: 3000, 50 per second and 500000 per day. The
  anonymous plan (no key) is the per-address bucket above. Change the numbers with
  `RINDETUPLATA_API_PLANS_FREE_CAPACITY`, `..._REFILLPERSECOND`, `..._DAILYQUOTA` and the same three under
  `RINDETUPLATA_API_PLANS_PARTNER_*`. Buckets and quotas live in the API's memory (single instance, S16): a restart
  gives every key a full bucket and a fresh quota, and a second instance would need a shared store. The plan lives in
  the `api_key` table; promote a key by hand and the next request already uses the new plan (the key is looked up in
  Postgres on every request, which is also why a revoked key stops at once):

  ```sql
  -- find the key by owner and display prefix (the full key is never stored), then promote it
  select k.id, u.email, k.key_prefix, k.plan, k.revoked_at
  from api_key k join app_user u on u.id = k.user_id order by k.id;
  update api_key set plan = 'PARTNER' where id = 42;
  -- back to the normal plan
  update api_key set plan = 'FREE' where id = 42;
  ```

  An unknown or revoked key is a 401 `ProblemDetail`, never a silent fall back to the address bucket, and costs one token
  of the per-address bucket (once it is spent the answer is the address 429 with no database lookup, so invented keys
  cannot flood Postgres); over the bucket
  or the quota is a 429 `ProblemDetail` with `Retry-After` (seconds to the next token, or to midnight for the quota).
  The header is read only on the public read paths; a key never signs anyone in. Details in
  [`api-keys.md`](api-keys.md).
- **Ranking cache and conditional requests (T3a).** `GET /api/v1/ranking` keeps up to
  `RINDETUPLATA_RANKING_CACHEMAXENTRIES` results in the API's memory (default 64, least recently used out
  first; one entry is a whole ranking, so the cap is a memory bound). An entry is reused only for the same data
  version and the same resolved query. The data version is read from Postgres on every request (one aggregate
  over `source_run`, the curated wallet rates with their audit and the newest instrument id), so a new
  ingestion run, a backfill or a curation save is visible on the next request, across restarts, with no
  invalidation call; a process that writes ranking data without recording a `source_run` row or a curation
  audit entry would not be noticed. Both read endpoints answer `ETag: W/"…"` and
  `Cache-Control: public, no-cache`: a CDN or browser may store the answer and must revalidate it, and an
  `If-None-Match` carrying the current tag gets a `304` with no body for the cost of that one query. The cache
  lives in one JVM, which matches the single API instance of the rate limit above.
- **Build token (optional).** The site build calls the API hundreds of times from one machine and would
  trip that limit. Put the same random string in `API_BUILD_TOKEN` in `api.env` (the overlay hands it to
  the API as `RINDETUPLATA_API_RATELIMIT_BYPASSTOKEN`) and as a build-time env var `API_BUILD_TOKEN` of
  the CDN project: `web/src/lib/data.ts` then sends it as `X-Build-Token` and the API skips the limit for
  that request. Blank or unset disables the bypass, and the token is never logged.
- The daily ingestion runs in LIVE mode and needs nothing extra. To replay a day of the T9 archive
  (REPLAY mode) mount a checkout of the `data` branch at `/app/archive`: uncomment the `volumes:` line
  of the `api` service in `deploy/compose.vps.yaml`.
- `RINDETUPLATA_INGEST_RUNONSTARTUP=true` makes the API ingest today's data once when it starts, in the
  background (T14a): a stack that boots fresh shows a real ranking instead of waiting for the 19:30 cron.

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
| Build-time env `API_BUILD_TOKEN` | optional: the API's rate-limit bypass token (see "VPS: the API"); never `PUBLIC_` |
| Build-time env `API_TIMEOUT_MS` | optional: per-request deadline in ms (default `15000`) |
| Build-time env `BUILD_MAX_DATA_AGE_DAYS` | optional: the freshness gate's limit (default `3`) |
| Build-time env `BUILD_MIN_RANKED` | optional: the minimum-data gate's limit (default `20`) |
| SPA fallback / routing | off: the site is fully prerendered, and `404.html` is the error page |

`API_URL` and `PUBLIC_API_URL` are different on purpose: the first one is only read while building,
the second one ships to the browser (`import.meta.env.PUBLIC_API_URL`). Leaving `PUBLIC_API_URL` empty
would make the browser call the CDN's own origin, where there is no API.

Authenticated flows (`/ingresar`, `/alertas`, `/admin/tasas`) need the site and the API on the same
registrable domain: the `SameSite=Lax` session cookie does not travel from a `*.pages.dev` preview
domain to `api.rindetuplata.ar`. Test the login on the production domain, not on a preview URL.

### Build gate and API client (T4d)

The build used to publish whatever the API answered: an empty or days-old ranking became an empty or stale
site, and Google indexed it. Now `astro build` runs a **gate** before it renders a single page
(`web/src/lib/buildGate.ts`, wired as the `astro:build:start` hook of `web/astro.config.mjs`, so `build:api`,
the private-preview rebuilder and a CDN that calls `astro build` all go through it). It fails the build,
exit code non-zero, with a message that names each failed check, and the previous site keeps being served:

| Check | Fails when |
|---|---|
| `data-age` | the newest capture date in the default ranking is more than `BUILD_MAX_DATA_AGE_DAYS` (default 3) Argentine calendar days old, or the ranking has no capture date |
| `min-ranked` | the default ranking has fewer than `BUILD_MIN_RANKED` (default 20) ranked entries |
| `category-rankings` | the ranking of any category page (`web/src/lib/categories.ts`) has no ranked entries; the message lists the empty categories |
| `api-read` | the API could not be read after the retries below |

With `WEB_FIXTURES=1` (CI and tests) only `data-age` is skipped: the recordings are old by nature, but a
fixture set that is too small or has an empty category still fails. An unreadable setting (`BUILD_MIN_RANKED=many`)
fails the build naming the variable.

The build's HTTP client (`web/src/lib/fetchRetry.ts`, used by `data.ts`) gives every request a deadline
(`API_TIMEOUT_MS`, default 15 s) and retries only network errors, timeouts, 5xx and 429: 3 attempts, waiting 1 s
then 2 s, or the `Retry-After` the API sent (capped at 30 s). Any other 4xx fails at once. An identical request
(same URL and headers) is sent **once per build** however many pages ask for it, so a build costs one fetch per
distinct ranking and per instrument history instead of about thirty identical rankings. The gate reads the same
requests the pages read, so it adds no fetch. Errors name the URL and the status or cause, never the headers
(`API_BUILD_TOKEN` stays out of every message).

## History backfill

`POST /api/v1/admin/backfill?from=&to=` (admin session, CSRF) fills up to 366 days of fund, MEP and macro history from
the public sources, insert-only, so a fresh deploy gets a real 7-day fund yield and a monthly report without waiting
(T4a). It starts nothing by itself, writes `source_run` rows with mode `BACKFILL`, and neither evaluates alerts nor
calls the rebuild hook below: trigger that by hand after a large backfill. The polite delay between the per-day fund
calls is `RINDETUPLATA_BACKFILL_DELAY` (default `500ms`); the monthly report cache holds
`RINDETUPLATA_REPORT_CACHEMAXENTRIES` reports (default 24). Full description, probes and limits in
[`backfill.md`](backfill.md).

## Rebuild hook

The daily ingestion POSTs once to `rindetuplata.ingest.rebuild-hook-url` after a COMPLETED job (T4d),
which is how the static site gets today's ranking. On Cloudflare Pages that URL is the project's deploy
hook; set it as the `RINDETUPLATA_INGEST_REBUILDHOOKURL` environment variable of the API (see above).
An empty value disables the call, which is the default locally. The private preview points the same hook
at its own rebuild sidecar (`http://rebuilder:8787/`, see "Private preview" above) instead of at a CDN.

## Backups

The database holds only what cannot be rebuilt: users and their alert rules, with what the rule types remember
between evaluations (the stored best option and rate, the digests already sent; `docs/alerts.md`). The ranking and the
history come from the sources and can be re-ingested (the backfill job, `POST /api/v1/admin/backfill`).

Three files do the work, all tested on a throwaway `postgres:18-alpine` by `deploy/test/backup-test.sh`:

- `deploy/backup.sh` runs `pg_dump -Fc` inside the Compose `postgres` service, writes it to a temporary file and
  renames it to `rindetuplata-YYYY-MM-DD.dump` only after pg_dump succeeded and produced something. On any failure it
  removes the temporary file, exits non-zero and prunes nothing, so a bad night never deletes the older good dumps.
  After a good dump it deletes the dumps older than the retention window (regular `rindetuplata-*.dump` files only).
- `deploy/restore.sh --confirm <dump>` restores one dump. It refuses (exit 2, nothing changed) without `--confirm`,
  without a dump name, or with a dump that does not exist; the restore itself is one transaction, so a dump that
  fails half-way leaves the database as it was.
- `deploy/systemd/rindetuplata-backup.service` + `.timer` run the backup every day at 03:30 local time;
  `Persistent=true` runs a missed one as soon as the box is back.

### Configuration

All optional; the defaults match the VPS layout above (the checkout in `/srv/rindetuplata`).

| Variable | Default | Meaning |
|---|---|---|
| `BACKUP_DIR` | `/var/backups/rindetuplata` | where dumps go (created `0700` if missing, dumps `0600`); outside the checkout on purpose, see below |
| `BACKUP_RETENTION_DAYS` | `30` | dumps strictly older than this many days are deleted; a positive whole number |
| `BACKUP_COPY_COMMAND` | unset | shell command run after a good dump, the dump's path is `"$1"` (see below) |
| `BACKUP_COMPOSE_FILES` | `compose.yaml deploy/compose.vps.yaml` | space-separated Compose files, relative to the checkout |
| `BACKUP_ENV_FILE` | `/srv/rindetuplata/api.env` | Compose `--env-file`; set it empty for none |
| `BACKUP_SERVICE` | `postgres` | the Compose service running Postgres |
| `BACKUP_DB_NAME`, `BACKUP_DB_USER` | `rindetuplata` | database and role |

The default directory is outside the checkout on purpose: inside it, cleaning the checkout's untracked files would delete
the dumps and every `docker build` would send them to the daemon as build context. If you override `BACKUP_DIR` to a
`backups/` directory inside the checkout anyway, `.gitignore` and `.dockerignore` both exclude `backups/`. A dump holds
the users' emails, so keep the directory `0700` and copy it off the box (below).

Under systemd put overrides in `/srv/rindetuplata/backup.env`, one `KEY=value` per line (the unit reads it if it exists).
`BACKUP_COMPOSE_FILES`, `BACKUP_SERVICE` and the two `BACKUP_DB_*` variables are what the test points at its throwaway
container.

### Install the timer

```bash
sudo install -m 0644 /srv/rindetuplata/deploy/systemd/rindetuplata-backup.service \
                     /srv/rindetuplata/deploy/systemd/rindetuplata-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now rindetuplata-backup.timer

systemctl list-timers rindetuplata-backup.timer      # the next run, 03:30 local
sudo systemctl start rindetuplata-backup.service     # run it now, once
journalctl -u rindetuplata-backup.service -n 20      # "backup written: ..." or the reason it failed
```

The units assume the checkout at `/srv/rindetuplata` and a system service (root, which can reach Docker); edit
`WorkingDirectory` and `ExecStart` in the `.service` if yours lives elsewhere. A failed run leaves the unit in the
`failed` state (`systemctl --failed`), which is the signal to look at the journal.

### Copy the dumps off the box

A backup on the same disk survives a bad deploy, not a lost VPS. Where the copies go is the owner's choice (object
storage, a second host); until then `BACKUP_COPY_COMMAND` stays unset and the dumps stay local. When it is set, the
script runs it after each good dump with the dump's path as `$1`, for example in `backup.env`:

```bash
BACKUP_COPY_COMMAND='rclone copyto "$1" offsite:rindetuplata/"$(basename "$1")"'
```

(The single quotes matter: systemd must hand the text to the script untouched, `$1` and all.)

If the command fails, the script exits non-zero (the unit fails) but keeps the local dump. It never runs after a
failed dump.

### Restore drill

Do this once after installing the timer, and again whenever the schema or the host changes: an untested backup is not
a backup. The drill restores into the live database, so schedule it, or do it on a second box.

```bash
cd /srv/rindetuplata
ls -lh /var/backups/rindetuplata                                  # pick the dump
compose=(docker compose -f compose.yaml -f deploy/compose.vps.yaml --env-file /srv/rindetuplata/api.env)

"${compose[@]}" stop api                                          # its connections would block the DROPs
deploy/restore.sh --confirm rindetuplata-YYYY-MM-DD.dump          # a name in BACKUP_DIR, or a full path
"${compose[@]}" start api
```

Then check that the data is what the dump held:

```bash
"${compose[@]}" exec -T postgres psql -U rindetuplata -d rindetuplata -c 'select count(*) from app_user'
"${compose[@]}" exec -T postgres psql -U rindetuplata -d rindetuplata -c 'select count(*) from alert_rule'
curl -fsS https://api.<domain>/actuator/health
```

Without `--confirm` the script prints why it refused and changes nothing. Restoring into a new or empty volume works
the same way: bring up `postgres` alone, run `restore.sh --confirm`, then start the rest:

```bash
"${compose[@]}" up -d --wait postgres
deploy/restore.sh --confirm rindetuplata-YYYY-MM-DD.dump
"${compose[@]}" up -d --wait
```

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
