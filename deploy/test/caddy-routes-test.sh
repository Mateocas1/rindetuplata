#!/usr/bin/env bash
# Caddy routes of every stack (T6e, S6, L32c): the paths the API publishes to the outside world reach it.
#
# llms.txt names /api/*, /v3/api-docs and /mcp, and the uptime monitor and the post-restore check (docs/deploy.md)
# read /actuator/health and /actuator/health/ingestion (T4c, F1), so each Caddyfile must proxy exactly those to
# the API container, and not the rest of /actuator/*:
# a path Caddy does not proxy answers 404 (VPS) or a static page (local, preview) instead of the API. The check
# adapts each Caddyfile with the same caddy:2-alpine image the stacks run, so it reads Caddy's own route tree
# instead of grepping the text, and asserts which paths the reverse-proxy route matches.
#
# Needs docker (the caddy image) and python3. No network beyond pulling the image once.
set -euo pipefail

cd "$(dirname "$0")/../.."

image=caddy:2-alpine
expected='["/actuator/health", "/actuator/health/ingestion", "/api/*", "/mcp", "/v3/api-docs"]'
failures=0
scratch=$(mktemp -d)
trap 'rm -r "$scratch"' EXIT

for caddyfile in deploy/Caddyfile.vps deploy/Caddyfile.local deploy/Caddyfile.preview; do
  docker run --rm -v "$PWD/$caddyfile:/etc/caddy/Caddyfile:ro" "$image" \
    caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile > "$scratch/config.json" 2>/dev/null
  if python3 -I - "$caddyfile" "$expected" "$scratch/config.json" <<'PY'
import json
import sys

name, expected = sys.argv[1], sorted(json.loads(sys.argv[2]))
with open(sys.argv[3]) as handle:
    config = json.load(handle)


def walk(routes, inherited):
    """Yield the path matchers of every route that ends in a reverse_proxy handler."""
    for route in routes:
        paths = [p for m in route.get("match", []) for p in m.get("path", [])] or inherited
        for handler in route.get("handle", []):
            if handler.get("handler") == "reverse_proxy":
                yield from paths
            yield from walk(handler.get("routes", []), paths)


proxied = set()
for server in config["apps"]["http"]["servers"].values():
    proxied.update(walk(server.get("routes", []), []))

if sorted(proxied) == expected:
    print(f"ok   - {name} proxies exactly {expected}")
else:
    print(f"FAIL - {name} proxies {sorted(proxied)}, expected {expected}", file=sys.stderr)
    sys.exit(1)
PY
  then :; else failures=$((failures + 1)); fi
done

exit "$failures"
