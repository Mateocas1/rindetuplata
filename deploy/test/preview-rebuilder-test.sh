#!/usr/bin/env bash
#
# Tests of the preview rebuild sidecar (`deploy/rebuilder/server.mjs`, T14b).
#
#   deploy/test/preview-rebuilder-test.sh
#
# The sidecar is what answers the T4d rebuild hook, so what it must never do is run two builds at once
# or silently swallow a failure. These tests run it for real on a loopback port with a fake `web/`
# directory (no Compose, no network beyond 127.0.0.1, no Astro build) and check:
#   - SITE_URL is mandatory (a build must never publish a localhost canonical);
#   - a POST answers 202 immediately with `started`;
#   - POSTs during a build collapse into exactly one queued rerun (two further POSTs, one rerun);
#   - the build inherits API_URL and SITE_URL from the sidecar;
#   - the last run of a failing build reports its exit code and `ok: false`.
set -Eeuo pipefail

cd "$(dirname "$0")/../.."
REPO_ROOT="$PWD"

scratch="$(mktemp -d)"
server_pid=""
cleanup() {
  if [ -n "$server_pid" ] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -r "$scratch" 2>/dev/null || true
}
trap cleanup EXIT

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

json_field() { # file field
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get(sys.argv[2]))' "$1" "$2"
}

json_is_idle() { # file -> 0 when no build is running and none is queued
  python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); sys.exit(0 if not s["building"] and not s["queued"] else 1)' "$1"
}

echo "== SITE_URL is mandatory =="
set +e
missing_site_url="$(SITE_URL= REBUILDER_PORT=0 timeout 10 node deploy/rebuilder/server.mjs 2>&1)"
rc=$?
set -e
check "no SITE_URL exits 1" "1" "$rc"
if printf '%s' "$missing_site_url" | grep -q 'SITE_URL is required'; then
  echo "ok   - no SITE_URL names the missing variable"
  passed=$((passed + 1))
else
  echo "FAIL - no SITE_URL did not name the variable: $missing_site_url" >&2
  failed=$((failed + 1))
fi

echo
echo "== a build: started, then exactly one queued rerun =="

# A fake `web/`: `npm run build:api` runs this script, which records the environment the sidecar passes
# down and takes 400ms, so a second POST is sure to arrive while the first build is still running.
mkdir -p "$scratch/web"
cat > "$scratch/web/package.json" <<'EOF'
{
  "name": "preview-rebuilder-fake-web",
  "private": true,
  "scripts": { "build:api": "node build.mjs" }
}
EOF
cat > "$scratch/web/build.mjs" <<'EOF'
import { appendFileSync, existsSync } from 'node:fs';
appendFileSync('runs.log', `${process.env.API_URL} ${process.env.SITE_URL}\n`);
setTimeout(() => process.exit(existsSync('fail-flag') ? 1 : 0), 400);
EOF

# `REBUILDER_PORT=0`: the kernel picks a free port and the server logs it, so this test never races for
# a fixed one with anything else on the machine.
WEB_DIR="$scratch/web" REBUILDER_PORT=0 API_URL=http://web SITE_URL=https://preview.example.ts.net \
  node deploy/rebuilder/server.mjs > "$scratch/server.log" 2>&1 &
server_pid=$!

port=""
for _ in $(seq 1 100); do
  port="$(sed -n 's/.*rebuilder listening on 0.0.0.0:\([0-9][0-9]*\).*/\1/p' "$scratch/server.log" | head -n 1)"
  [ -n "$port" ] && break
  sleep 0.1
done
if [ -z "$port" ]; then
  echo "FAIL - the sidecar did not report a listening port; log:" >&2
  cat "$scratch/server.log" >&2
  exit 1
fi
echo "ok   - sidecar listening on 127.0.0.1:$port"
passed=$((passed + 1))

post() { # body-file
  curl -sS -o "$1" -w '%{http_code}' -X POST "http://127.0.0.1:$port/"
}

http_code="$(post "$scratch/post1.json")"
check "first POST answers 202" "202" "$http_code"
check "first POST starts the build" "started" "$(json_field "$scratch/post1.json" status)"

http_code="$(post "$scratch/post2.json")"
check "POST during a build answers 202" "202" "$http_code"
check "POST during a build is queued" "queued" "$(json_field "$scratch/post2.json" status)"

http_code="$(post "$scratch/post3.json")"
check "third POST is absorbed by the same rerun" "queued" "$(json_field "$scratch/post3.json" status)"

for _ in $(seq 1 200); do
  curl -sS -o "$scratch/state.json" "http://127.0.0.1:$port/"
  if json_is_idle "$scratch/state.json"; then
    break
  fi
  sleep 0.1
done
check "three POSTs left exactly two runs" "2" "$(json_field "$scratch/state.json" runs)"
check "the queue is empty again" "False" "$(json_field "$scratch/state.json" queued)"
check "the last run is ok" "True" "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["last"]["ok"])' "$scratch/state.json")"
check "the last run exited 0" "0" "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["last"]["exitCode"])' "$scratch/state.json")"

runs="$(wc -l < "$scratch/web/runs.log" | tr -d ' ')"
check "the build ran twice (runs.log)" "2" "$runs"
check "the build got API_URL and SITE_URL" "2" "$(grep -c '^http://web https://preview.example.ts.net$' "$scratch/web/runs.log")"

echo
echo "== a failing build reports its exit code =="
touch "$scratch/web/fail-flag"
post "$scratch/post4.json" > /dev/null
for _ in $(seq 1 200); do
  curl -sS -o "$scratch/state-fail.json" "http://127.0.0.1:$port/"
  if json_is_idle "$scratch/state-fail.json"; then
    break
  fi
  sleep 0.1
done
check "the failing run exited 1" "1" "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["last"]["exitCode"])' "$scratch/state-fail.json")"
check "the failing run is not ok" "False" "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["last"]["ok"])' "$scratch/state-fail.json")"
check "runs counts the failure too" "3" "$(json_field "$scratch/state-fail.json" runs)"

echo
echo "== unknown route =="
check "GET /nope answers 404" "404" "$(curl -sS -o "$scratch/notfound.json" -w '%{http_code}' "http://127.0.0.1:$port/nope")"

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
