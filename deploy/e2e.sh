#!/usr/bin/env bash
#
# End-to-end smoke of the whole product on the local Compose stack (T7e).
#
#   deploy/e2e.sh
#
# It builds the API image, brings the `stack` profile up (postgres, rabbitmq, mailpit, api, web),
# builds the site against that API, runs the Playwright spec through Caddy, and always takes the stack
# down again — including when the smoke fails. Node dependencies and the Playwright browser have to be
# installed already: `cd web && npm ci && npx playwright install chromium`.
#
# The smoke runs in its own Compose project (`E2E_PROJECT`, default `rindetuplata-e2e`), never in the
# project the developer uses day to day (finding R1 of review-d018a62efca39d30): the teardown removes
# only what this script started, so a database that was already running keeps running with its data.
# `deploy/compose.e2e.yaml` is the overlay that makes that possible without a port fight: it drops the
# host publications of Postgres, RabbitMQ and Mailpit's SMTP port, so a local `up -d postgres` can keep
# 5432 while the smoke runs. What is still published on the host is checked below and named when taken.
#
# Environment (all optional; the defaults are the local stack):
#   E2E_PROJECT   Compose project name of the throwaway stack     rindetuplata-e2e
#   WEB_PORT      published port of the web service (Caddy)       4321
#   E2E_BASE_URL  where the browser goes; defaults to http://localhost:$WEB_PORT
#   MAILPIT_URL   Mailpit's HTTP API                              http://localhost:8025
#   SITE_URL      the site's own URL used at build time           $E2E_BASE_URL
set -Eeuo pipefail

cd "$(dirname "$0")/.."

E2E_PROJECT="${E2E_PROJECT:-rindetuplata-e2e}"
WEB_PORT="${WEB_PORT:-4321}"
E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:${WEB_PORT}}"
E2E_BASE_URL="${E2E_BASE_URL%/}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
SITE_URL="${SITE_URL:-$E2E_BASE_URL}"

# Every command targets the throwaway project and its overlay, so `down` below can never touch the
# developer's stack, and `up` never binds a port that stack already holds.
compose() {
  docker compose --project-name "$E2E_PROJECT" \
    --file compose.yaml \
    --file deploy/compose.e2e.yaml \
    "$@"
}

# One origin, so the magic link, the alert page and the allowed browser origin all match the URL the
# browser really visits — even when WEB_PORT is not 4321. Compose reads these as its `${VAR:-default}`.
export WEB_PORT E2E_BASE_URL MAILPIT_URL SITE_URL
export RINDETUPLATA_AUTH_MAGICLINKURL="${E2E_BASE_URL}/ingresar"
export RINDETUPLATA_ALERT_MANAGEURL="${E2E_BASE_URL}/alertas"
export RINDETUPLATA_WEB_ALLOWEDORIGINS="$E2E_BASE_URL"

# Caddy serves web/dist from the host, so the directory must exist and belong to this user before
# Compose binds it; Docker would otherwise create it owned by root and the build could not write.
mkdir -p web/dist

if [ ! -d web/node_modules ]; then
  echo "web/node_modules is missing: run 'cd web && npm ci && npx playwright install chromium' first." >&2
  exit 2
fi

# The host ports the e2e project publishes (compose.yaml + deploy/compose.e2e.yaml): Caddy for the
# browser (WEB_PORT) and Mailpit's HTTP API (8025, the port MAILPIT_URL defaults to). Anything else
# stays on the Compose network, which is what lets the smoke start next to a local postgres.
E2E_PORTS=(8025 "$WEB_PORT")

# True when something already accepts connections on the loopback port.
port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

for port in "${E2E_PORTS[@]}"; do
  if port_in_use "$port"; then
    echo "port ${port} is already in use, so the '${E2E_PROJECT}' stack cannot start." >&2
    echo "This smoke runs next to your local stack, not instead of it; only the site and Mailpit's HTTP API" >&2
    echo "are published. Stop what owns the port (for a dev stack or 'npm run dev' on ${WEB_PORT}) or remove a" >&2
    echo "leftover e2e stack with 'docker compose --project-name ${E2E_PROJECT} --profile stack down'." >&2
    exit 2
  fi
done

stack_down() {
  compose --profile stack down
}
trap stack_down EXIT

echo "== building the API image =="
compose --profile stack build api

echo "== starting the stack (compose project '$E2E_PROJECT') =="
compose --profile stack up -d --wait

echo "== building the site against the API ($SITE_URL) =="
(cd web && API_URL="$E2E_BASE_URL" SITE_URL="$SITE_URL" npm run build:api)

echo "== running the end-to-end smoke =="
(cd web && npm run e2e)
