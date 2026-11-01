#!/usr/bin/env bash
#
# The `prod` profile reaches the VPS API (T4b, S4, L33b): `compose.yaml` + `deploy/compose.vps.yaml`.
#
#   deploy/test/prod-profile-test.sh
#
# The startup guard that refuses a development default (ProdProfileGuard) only protects the VPS if the overlay
# actually activates the profile, so this script renders the stack with a scratch env file (fake values, no real
# secret, no container started, no image pulled) and asserts what the API container would receive. It needs Docker's
# Compose CLI and `jq`; it never needs a Docker daemon, a network or a real env file. The guard rules themselves are
# tested in src/test/java/ar/rindetuplata/config/ProdProfileGuardTest.java.
#
# Checked:
#   - the API gets SPRING_PROFILES_ACTIVE=prod (and nothing else activates a profile on the other services);
#   - the values a prod env file supplies reach the API under the names the guard reads;
#   - the API keeps the image's HEALTHCHECK, which probes /actuator/health and not the ingestion group, so STALE data
#     (HTTP 200 there, 503 only on /actuator/health/ingestion) never makes `depends_on: service_healthy` fail;
#   - a missing required key fails the render with a non-zero exit and its message on stderr.
set -Eeuo pipefail

cd "$(dirname "$0")/../.."

scratch="$(mktemp -d)"
trap 'rm -r "$scratch" 2>/dev/null || true' EXIT

failures=0
check() {
  local description="$1"
  shift
  if "$@"; then
    echo "ok   - $description"
  else
    echo "FAIL - $description" >&2
    failures=$((failures + 1))
  fi
}

write_env() {
  cat > "$1" <<'ENV'
POSTGRES_PASSWORD=scratch-postgres-password
RABBITMQ_DEFAULT_PASS=scratch-rabbit-password
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_USERNAME=api@example.com
SPRING_MAIL_PASSWORD=scratch-smtp-password
RINDETUPLATA_AUTH_MAGICLINKURL=https://rindetuplata.example/ingresar
RINDETUPLATA_ALERT_MANAGEURL=https://rindetuplata.example/alertas
RINDETUPLATA_WEB_ALLOWEDORIGINS=https://rindetuplata.example
RINDETUPLATA_AUTH_ADMINEMAILS=admin@example.com
RINDETUPLATA_INGEST_REBUILDHOOKURL=https://hooks.example/deploy
API_BUILD_TOKEN=0123456789abcdef0123456789abcdef
ENV
}

compose_config() {
  local env_file="$1"
  shift
  docker compose \
    --project-name rindetuplata-prod-profile \
    --env-file "$env_file" \
    --file compose.yaml \
    --file deploy/compose.vps.yaml \
    --profile stack \
    config "$@"
}

ENV_FILE="$scratch/api.env"
write_env "$ENV_FILE"

echo "== rendering the VPS stack (docker compose config) =="
set +e
compose_config "$ENV_FILE" --format json > "$scratch/config.json" 2> "$scratch/config.err"
rc=$?
set -e
echo "docker compose config rc=$rc"
if [ "$rc" -ne 0 ]; then
  echo "FAIL - the VPS overlay does not render:" >&2
  cat "$scratch/config.err" >&2
  exit 1
fi

api_env() { jq -r --arg key "$1" '.services.api.environment[$key] // "<unset>"' "$scratch/config.json"; }

echo "== asserting the rendered API service =="
check "the API activates exactly the prod profile" test "$(api_env SPRING_PROFILES_ACTIVE)" = "prod"
check "no other service activates a Spring profile" test "$(jq '[.services | to_entries[] | select(.key != "api") | select(.value.environment.SPRING_PROFILES_ACTIVE != null)] | length' "$scratch/config.json")" = "0"
check "the secure cookie stays on" test "$(api_env RINDETUPLATA_AUTH_SECURECOOKIE)" = "true"
check "the site origin comes from the env file" test "$(api_env RINDETUPLATA_WEB_ALLOWEDORIGINS)" = "https://rindetuplata.example"
check "the magic link URL comes from the env file" test "$(api_env RINDETUPLATA_AUTH_MAGICLINKURL)" = "https://rindetuplata.example/ingresar"
check "the alert manage URL comes from the env file" test "$(api_env RINDETUPLATA_ALERT_MANAGEURL)" = "https://rindetuplata.example/alertas"
check "the mail host comes from the env file" test "$(api_env SPRING_MAIL_HOST)" = "smtp.example.com"
check "the admin email comes from the env file" test "$(api_env RINDETUPLATA_AUTH_ADMINEMAILS)" = "admin@example.com"
check "the build token reaches the rate limit bypass" test "$(api_env RINDETUPLATA_API_RATELIMIT_BYPASSTOKEN)" = "0123456789abcdef0123456789abcdef"

echo "== the API health probe cannot fail on stale data =="
check "the overlay leaves the image's HEALTHCHECK alone" test "$(jq '.services.api.healthcheck' "$scratch/config.json")" = "null"
check "the Dockerfile HEALTHCHECK probes /actuator/health itself" grep -Eq 'curl -fsS http://localhost:8080/actuator/health( |\|\||$)' Dockerfile
check "caddy waits for a healthy API" test "$(jq -r '.services.caddy.depends_on.api.condition' "$scratch/config.json")" = "service_healthy"

echo "== a missing required key fails the render =="
grep -v '^RINDETUPLATA_AUTH_ADMINEMAILS=' "$ENV_FILE" > "$scratch/missing.env"
set +e
compose_config "$scratch/missing.env" --format json > /dev/null 2> "$scratch/missing.err"
missing_rc=$?
set -e
check "the render exits non-zero without the admin email (rc=$missing_rc)" test "$missing_rc" -ne 0
check "stderr says what to set" grep -q 'set the admin email' "$scratch/missing.err"

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed" >&2
  exit 1
fi
echo "all checks passed"
