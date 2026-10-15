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
# Environment (all optional; the defaults are the local stack):
#   WEB_PORT      published port of the web service (Caddy)         4321
#   E2E_BASE_URL  where the browser goes; defaults to http://localhost:$WEB_PORT
#   MAILPIT_URL   Mailpit's HTTP API                                 http://localhost:8025
#   SITE_URL      the site's own URL used at build time              $E2E_BASE_URL
#
# `down` removes every service of this Compose project, not only the two the `stack` profile adds:
# postgres has no volume on purpose, so the smoke always starts from an empty database. Do not run this
# while you depend on a local database you started by hand.
set -Eeuo pipefail

cd "$(dirname "$0")/.."

WEB_PORT="${WEB_PORT:-4321}"
E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:${WEB_PORT}}"
E2E_BASE_URL="${E2E_BASE_URL%/}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
SITE_URL="${SITE_URL:-$E2E_BASE_URL}"

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

stack_down() {
  docker compose --profile stack down
}
trap stack_down EXIT

echo "== building the API image =="
docker compose --profile stack build api

echo "== starting the stack =="
docker compose --profile stack up -d --wait

echo "== building the site against the API ($SITE_URL) =="
(cd web && API_URL="$E2E_BASE_URL" SITE_URL="$SITE_URL" npm run build:api)

echo "== running the end-to-end smoke =="
(cd web && npm run e2e)
