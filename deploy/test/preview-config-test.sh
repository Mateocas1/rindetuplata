#!/usr/bin/env bash
#
# Exposure tests of the preview overlay (T14b, S20): `compose.yaml` + `deploy/compose.preview.yaml`.
#
#   deploy/test/preview-config-test.sh
#
# The preview puts the owner's machine on the internet through Tailscale Funnel, so the Compose merge
# is the part that must not be wrong: this script renders the stack with a scratch env file (no real
# secret, no container started, no image pulled) and asserts the exposure rules, the secret plumbing
# and the production-shaped API settings of `deploy/compose.preview.yaml`. It needs Docker's Compose
# CLI; it never needs a Docker daemon, a network or `deploy/preview.env`.
#
# Checked:
#   - Postgres, RabbitMQ and Mailpit publish no host port (the Funnel exposes this machine);
#   - Postgres mounts the pgdata volume at `/var/lib/postgresql` (the path the postgres:18 image
#     declares, `PGDATA=/var/lib/postgresql/18/docker`) and RabbitMQ its own at `/var/lib/rabbitmq`
#     with a fixed hostname, so `down` keeps the data;
#   - `web` publishes exactly one port, on 127.0.0.1;
#   - the preview Caddyfile (noindex) is the one mounted;
#   - the API gets the SMTP account, `secure-cookie=true`, the Funnel-derived URLs, the admin emails,
#     the startup ingestion and the sidecar rebuild hook;
#   - the rebuild sidecar publishes no port and mounts the repository's `web/`;
#   - every service restarts unless stopped;
#   - a missing required key fails the render instead of starting with a local default.
set -Eeuo pipefail

cd "$(dirname "$0")/../.."

scratch="$(mktemp -d)"
trap 'rm -r "$scratch" 2>/dev/null || true' EXIT

ENV_FILE="$scratch/preview.env"
# Not 4321: a scratch port proves the published port comes from the env file, not from the default.
WEB_PORT=4399
PREVIEW_URL=https://preview.example.ts.net

cat > "$ENV_FILE" <<EOF
PREVIEW_URL=$PREVIEW_URL
WEB_PORT=$WEB_PORT
POSTGRES_PASSWORD=scratch-postgres-password
RABBITMQ_DEFAULT_PASS=scratch-rabbit-password
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=preview@example.com
SPRING_MAIL_PASSWORD=scratch-smtp-password
RINDETUPLATA_MAIL_FROM="rindetuplata <preview@example.com>"
RINDETUPLATA_AUTH_ADMINEMAILS=preview@example.com
EOF

compose_config() {
  docker compose \
    --project-name rindetuplata-preview \
    --env-file "$ENV_FILE" \
    --file compose.yaml \
    --file deploy/compose.preview.yaml \
    --profile stack \
    config "$@"
}

echo "== rendering the preview stack (docker compose config) =="
compose_config > "$scratch/config.yaml" 2> "$scratch/config.err"
rc=$?
echo "docker compose config rc=$rc"
if [ "$rc" -ne 0 ]; then
  echo "FAIL - the preview overlay does not render:" >&2
  cat "$scratch/config.err" >&2
  exit 1
fi
compose_config --format json > "$scratch/config.json" 2> "$scratch/config.err"

echo "== asserting the rendered stack =="
WEB_PORT="$WEB_PORT" PREVIEW_URL="$PREVIEW_URL" python3 - "$scratch/config.json" <<'PY'
import json
import sys

services = json.load(open(sys.argv[1]))["services"]
expected_names = {"postgres", "rabbitmq", "api", "web", "rebuilder"}
failures = []


def check(description, condition, detail=""):
    if condition:
        print(f"ok   - {description}")
    else:
        print(f"FAIL - {description}{': ' + detail if detail else ''}", file=sys.stderr)
        failures.append(description)


check(
    "the stack is exactly postgres, rabbitmq, api, web and rebuilder (mailpit is off)",
    set(services) == expected_names,
    f"got {sorted(services)}",
)

for name in ("postgres", "rabbitmq", "rebuilder", "api"):
    check(f"{name} publishes no host port", not services[name].get("ports"), repr(services[name].get("ports")))

published = [
    (name, port.get("host_ip"), str(port.get("published")), port.get("target"))
    for name, service in services.items()
    for port in service.get("ports") or []
]
check(
    "only web publishes a port, and only on 127.0.0.1",
    published == [("web", "127.0.0.1", "4399", 80)],
    repr(published),
)

for name in expected_names:
    check(f"{name} restarts unless stopped", services[name].get("restart") == "unless-stopped")


def volume_targets(name):
    return {mount["target"]: mount for mount in services[name].get("volumes") or []}


def environment(name):
    return services[name].get("environment") or {}


postgres_volumes = volume_targets("postgres")
check(
    "postgres mounts pgdata at /var/lib/postgresql (the postgres:18 volume)",
    postgres_volumes.get("/var/lib/postgresql", {}).get("source") == "pgdata",
    repr(postgres_volumes),
)
check(
    "postgres takes POSTGRES_PASSWORD from the env file",
    environment("postgres").get("POSTGRES_PASSWORD") == "scratch-postgres-password",
    repr(environment("postgres").get("POSTGRES_PASSWORD")),
)

rabbitmq_volumes = volume_targets("rabbitmq")
check(
    "rabbitmq mounts rabbitmq_data at /var/lib/rabbitmq with a fixed hostname",
    rabbitmq_volumes.get("/var/lib/rabbitmq", {}).get("source") == "rabbitmq_data"
    and services["rabbitmq"].get("hostname") == "rindetuplata-rabbitmq",
    repr((rabbitmq_volumes, services["rabbitmq"].get("hostname"))),
)
check(
    "rabbitmq takes its password from the env file",
    environment("rabbitmq").get("RABBITMQ_DEFAULT_PASS") == "scratch-rabbit-password",
)

web_volumes = volume_targets("web")
check("web serves web/dist read-only", web_volumes.get("/srv", {}).get("read_only") is True)
caddyfile = web_volumes.get("/etc/caddy/Caddyfile", {}).get("source", "")
check(
    "web mounts exactly the preview Caddyfile",
    caddyfile.endswith("deploy/Caddyfile.preview") and len(web_volumes) == 2,
    caddyfile,
)

api = environment("api")
check(
    "the API sends real mail through the env file's SMTP account",
    api.get("SPRING_MAIL_HOST") == "smtp.example.com"
    and api.get("SPRING_MAIL_PORT") == "587"
    and api.get("SPRING_MAIL_USERNAME") == "preview@example.com"
    and api.get("SPRING_MAIL_PASSWORD") == "scratch-smtp-password"
    and api.get("SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE") == "true",
    repr({k: v for k, v in api.items() if k.startswith("SPRING_MAIL")}),
)
check(
    "the API uses the env file's sender",
    api.get("RINDETUPLATA_MAIL_FROM") == "rindetuplata <preview@example.com>",
    repr(api.get("RINDETUPLATA_MAIL_FROM")),
)
check(
    "TLS in front means secure cookies",
    api.get("RINDETUPLATA_AUTH_SECURECOOKIE") == "true",
    repr(api.get("RINDETUPLATA_AUTH_SECURECOOKIE")),
)
check(
    "the magic link, the alert page and the allowed origin come from PREVIEW_URL",
    api.get("RINDETUPLATA_AUTH_MAGICLINKURL") == "https://preview.example.ts.net/ingresar"
    and api.get("RINDETUPLATA_ALERT_MANAGEURL") == "https://preview.example.ts.net/alertas"
    and api.get("RINDETUPLATA_WEB_ALLOWEDORIGINS") == "https://preview.example.ts.net",
    repr(
        (
            api.get("RINDETUPLATA_AUTH_MAGICLINKURL"),
            api.get("RINDETUPLATA_ALERT_MANAGEURL"),
            api.get("RINDETUPLATA_WEB_ALLOWEDORIGINS"),
        )
    ),
)
check(
    "the API ingests on startup and rebuilds the site through the sidecar",
    api.get("RINDETUPLATA_INGEST_RUNONSTARTUP") == "true"
    and api.get("RINDETUPLATA_INGEST_REBUILDHOOKURL") == "http://rebuilder:8787/",
    repr((api.get("RINDETUPLATA_INGEST_RUNONSTARTUP"), api.get("RINDETUPLATA_INGEST_REBUILDHOOKURL"))),
)
check(
    "the API has the admin emails and the env file's database password",
    api.get("RINDETUPLATA_AUTH_ADMINEMAILS") == "preview@example.com"
    and api.get("SPRING_DATASOURCE_PASSWORD") == "scratch-postgres-password",
    repr((api.get("RINDETUPLATA_AUTH_ADMINEMAILS"), api.get("SPRING_DATASOURCE_PASSWORD"))),
)

rebuilder = environment("rebuilder")
check(
    "the sidecar builds against the internal Caddy and the public site URL",
    rebuilder.get("API_URL") == "http://web"
    and rebuilder.get("SITE_URL") == "https://preview.example.ts.net"
    and services["rebuilder"].get("image") == "node:24",
    repr(rebuilder),
)
rebuilder_volumes = volume_targets("rebuilder")
check(
    "the sidecar mounts the repository's web/ and its own script",
    set(rebuilder_volumes) == {"/app/web", "/app/deploy/rebuilder"},
    repr(rebuilder_volumes),
)

if failures:
    print(f"\n{len(failures)} assertion(s) failed", file=sys.stderr)
    sys.exit(1)
print("\nall assertions passed")
PY
assert_rc=$?
echo "assertions rc=$assert_rc"
if [ "$assert_rc" -ne 0 ]; then
  exit 1
fi

echo
echo "== the preview Caddyfile answers with noindex =="
if grep -q 'X-Robots-Tag "noindex, nofollow"' deploy/Caddyfile.preview; then
  echo "ok   - deploy/Caddyfile.preview sets X-Robots-Tag: noindex, nofollow"
else
  echo "FAIL - deploy/Caddyfile.preview does not set X-Robots-Tag" >&2
  exit 1
fi

echo
echo "== a missing required key fails the render =="
grep -v '^POSTGRES_PASSWORD=' "$ENV_FILE" > "$scratch/incomplete.env"
set +e
docker compose --project-name rindetuplata-preview --env-file "$scratch/incomplete.env" \
  --file compose.yaml --file deploy/compose.preview.yaml --profile stack config \
  > "$scratch/missing.out" 2>&1
missing_rc=$?
set -e
echo "docker compose config without POSTGRES_PASSWORD rc=$missing_rc"
if [ "$missing_rc" -eq 0 ]; then
  echo "FAIL - the render succeeded without POSTGRES_PASSWORD" >&2
  exit 1
fi
if grep -q 'POSTGRES_PASSWORD' "$scratch/missing.out"; then
  echo "ok   - the failure names POSTGRES_PASSWORD: $(head -n 1 "$scratch/missing.out")"
else
  echo "FAIL - the failure does not name POSTGRES_PASSWORD:" >&2
  cat "$scratch/missing.out" >&2
  exit 1
fi

echo
echo "preview config test passed"
