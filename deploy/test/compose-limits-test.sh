#!/usr/bin/env bash
#
# Memory limits and log rotation of the VPS overlay.
#
#   deploy/test/compose-limits-test.sh
#
# Renders `compose.yaml` + `deploy/compose.vps.yaml` with `docker compose config` (fake env values, every
# profile, nothing is started) and asserts per service what the box depends on:
#   - the 2 GB VPS split: postgres 512m, rabbitmq 384m, api 768m, caddy 128m, so the JVM (75% of its
#     container limit, see the Dockerfile) cannot take memory from the database or the broker;
#   - json-file log rotation, 10m x 3, on every service (a long-lived box with unrotated logs fills its disk).
set -Eeuo pipefail

cd "$(dirname "$0")/../.."

scratch="$(mktemp -d)"
trap 'rm -r "$scratch" 2>/dev/null || true' EXIT

passed=0
failed=0

check() { # description expected actual
  if [ "$2" = "$3" ]; then
    echo "ok   - $1"
    passed=$((passed + 1))
  else
    echo "FAIL - $1" >&2
    echo "         expected: $2" >&2
    echo "         actual:   $3" >&2
    failed=$((failed + 1))
  fi
}

cat > "$scratch/fake.env" <<'ENV'
POSTGRES_PASSWORD=fake-postgres-password
RABBITMQ_DEFAULT_PASS=fake-rabbit-password
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_USERNAME=fake-user
SPRING_MAIL_PASSWORD=fake-mail-password
RINDETUPLATA_AUTH_MAGICLINKURL=https://example.com/ingresar
RINDETUPLATA_ALERT_MANAGEURL=https://example.com/alertas
RINDETUPLATA_WEB_ALLOWEDORIGINS=https://example.com
RINDETUPLATA_AUTH_ADMINEMAILS=admin@example.com
RINDETUPLATA_INGEST_REBUILDHOOKURL=https://example.com/hook
ENV

echo "== the overlay renders =="
set +e
docker compose -f compose.yaml -f deploy/compose.vps.yaml --env-file "$scratch/fake.env" --profile '*' \
  config --format json > "$scratch/config.json" 2> "$scratch/config.err"
rc=$?
set -e
check "docker compose config exits 0" "0" "$rc"
if [ "$rc" -ne 0 ]; then
  cat "$scratch/config.err" >&2
  echo
  echo "passed: $passed, failed: $((failed + 1))"
  exit 1
fi

echo
echo "== memory limits (bytes) =="
mib() { echo $(($1 * 1024 * 1024)); }
declare -A limits=([postgres]=512 [rabbitmq]=384 [api]=768 [caddy]=128)
for service in postgres rabbitmq api caddy; do
  actual="$(jq -r --arg s "$service" '.services[$s].mem_limit // "unset"' "$scratch/config.json")"
  check "$service mem_limit is ${limits[$service]}m" "$(mib "${limits[$service]}")" "$actual"
done

echo
echo "== log rotation on every service =="
for service in $(jq -r '.services | keys[]' "$scratch/config.json"); do
  actual="$(jq -r --arg s "$service" \
    '.services[$s].logging | if . == null then "unset" else "\(.driver) \(.options["max-size"]) \(.options["max-file"])" end' \
    "$scratch/config.json")"
  check "$service logs with json-file 10m x 3" "json-file 10m 3" "$actual"
done

echo
echo "== the JVM keeps sizing its heap from the container limit =="
if grep -q -- '-XX:MaxRAMPercentage=75' Dockerfile; then
  echo "ok   - Dockerfile keeps -XX:MaxRAMPercentage=75"
  passed=$((passed + 1))
else
  echo "FAIL - Dockerfile no longer sets -XX:MaxRAMPercentage=75" >&2
  failed=$((failed + 1))
fi
check "no JAVA_OPTS heap cap overrides it on the api service" "unset" \
  "$(jq -r '.services.api.environment.JAVA_OPTS // "unset"' "$scratch/config.json")"

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
