#!/usr/bin/env bash
#
# Tests of the preview rebuild sidecar (`deploy/rebuilder/server.mjs`, T14b/T14c).
#
#   deploy/test/preview-rebuilder-test.sh
#
# The sidecar is what answers the T4d rebuild hook, so what it must never do is run two builds at once,
# silently swallow a failure, or let a visitor see a half-written site. These tests run it for real on a
# loopback port with a fake `web/` directory (no Compose, no network beyond 127.0.0.1, no Astro build)
# and check:
#   - SITE_URL is mandatory (a build must never publish a localhost canonical);
#   - a POST answers 202 immediately with `started`;
#   - POSTs during a build collapse into exactly one queued rerun (two further POSTs, one rerun);
#   - every build writes `web/.site/build-<n>` through `astro build --outDir`, inheriting API_URL and
#     SITE_URL, and the project's own `build:api` script is never used (fix B of the T14c review);
#   - `web/.site/current` is repointed only when a build exited 0, so a build in flight and a failed
#     build both leave the previous site serving atomically;
#   - only the two newest builds are kept;
#   - the last run of a failing build reports its exit code and `ok: false`.
set -Eeuo pipefail

cd "$(dirname "$0")/../.."

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

json_last() { # file field
  python3 -c 'import json,sys; last=json.load(open(sys.argv[1]))["last"]; print(last[sys.argv[2]] if last else "None")' "$1" "$2"
}

json_is_idle() { # file -> 0 when no build is running and none is queued
  python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); sys.exit(0 if not s["building"] and not s["queued"] else 1)' "$1"
}

json_builds() { # file -> the kept build names, space separated
  python3 -c 'import json,sys; print(" ".join(json.load(open(sys.argv[1]))["builds"]))' "$1"
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
echo "== API_URL is mandatory too: the build must read the running API, never the fixtures =="
set +e
missing_api_url="$(API_URL= SITE_URL=https://preview.example.ts.net REBUILDER_PORT=0 timeout 10 node deploy/rebuilder/server.mjs 2>&1)"
rc=$?
set -e
check "no API_URL exits 1" "1" "$rc"
if printf '%s' "$missing_api_url" | grep -q 'API_URL is required'; then
  echo "ok   - no API_URL names the missing variable"
  passed=$((passed + 1))
else
  echo "FAIL - no API_URL did not name the variable: $missing_api_url" >&2
  failed=$((failed + 1))
fi

echo
echo "== a build: started, then exactly one queued rerun =="

# A fake `web/`: the sidecar runs `node_modules/.bin/astro build --outDir .site/build-<n>`, which this
# script stands in for (it records the environment and the output directory, writes one file there and
# takes 400ms, so a second POST is sure to arrive while the first build is still running). The
# `build:api` script the sidecar used to run is a regression tripwire: reaching it is a failure.
mkdir -p "$scratch/web/node_modules/.bin"
cat > "$scratch/web/package.json" <<'EOF'
{
  "name": "preview-rebuilder-fake-web",
  "private": true,
  "scripts": { "build:api": "node wrong-command.mjs" }
}
EOF
cat > "$scratch/web/wrong-command.mjs" <<'EOF'
import { appendFileSync } from 'node:fs';
appendFileSync('wrong-command.log', 'the sidecar ran npm run build:api\n');
setTimeout(() => process.exit(0), 400);
EOF
cat > "$scratch/web/node_modules/.bin/astro" <<'EOF'
#!/usr/bin/env node
// Fake `astro build --outDir <dir>`.
const { appendFileSync, existsSync, mkdirSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');
const args = process.argv.slice(2);
const outDir = args[args.indexOf('--outDir') + 1];
appendFileSync(
  join(process.cwd(), 'runs.log'),
  JSON.stringify({
    args,
    outDir,
    apiUrl: process.env.API_URL,
    siteUrl: process.env.SITE_URL,
    webFixtures: process.env.WEB_FIXTURES ?? null,
  }) + '\n',
);
mkdirSync(join(process.cwd(), outDir), { recursive: true });
writeFileSync(join(process.cwd(), outDir, 'index.html'), `serving ${outDir}\n`);
setTimeout(() => process.exit(existsSync(join(process.cwd(), 'fail-flag')) ? 1 : 0), 400);
EOF
chmod +x "$scratch/web/node_modules/.bin/astro"

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

SITE="$scratch/web/.site"
post() { # body-file
  curl -sS -o "$1" -w '%{http_code}' -X POST "http://127.0.0.1:$port/"
}

state() { # name -> writes $scratch/state-<name>.json
  curl -sS -o "$scratch/state-$1.json" "http://127.0.0.1:$port/"
}

wait_idle() { # name
  for _ in $(seq 1 300); do
    state "$1"
    if json_is_idle "$scratch/state-$1.json"; then
      return 0
    fi
    sleep 0.1
  done
  echo "FAIL - the sidecar never went idle" >&2
  cat "$scratch/server.log" >&2
  return 1
}

current_build() { # -> the name `current` points at, or a marker
  readlink "$SITE/current" 2>/dev/null || echo "(no current)"
}

current_html() { # -> what `current` serves (a marker when there is nothing)
  cat "$SITE/current/index.html" 2>/dev/null || echo "(no current)"
}

kept_builds() { # -> the build directories on disk, oldest first
  ls -d "$SITE"/build-* 2>/dev/null | sed "s|$SITE/||" | sort -V | tr '\n' ' ' | sed 's/ $//'
}

check "nothing is published before the first build" "(no current)" "$(current_build)"

http_code="$(post "$scratch/post1.json")"
check "first POST answers 202" "202" "$http_code"
check "first POST starts the build" "started" "$(json_field "$scratch/post1.json" status)"

http_code="$(post "$scratch/post2.json")"
check "POST during a build answers 202" "202" "$http_code"
check "POST during a build is queued" "queued" "$(json_field "$scratch/post2.json" status)"

http_code="$(post "$scratch/post3.json")"
check "third POST is absorbed by the same rerun" "queued" "$(json_field "$scratch/post3.json" status)"

wait_idle builds || exit 1
check "three POSTs left exactly two runs" "2" "$(json_field "$scratch/state-builds.json" runs)"
check "the queue is empty again" "False" "$(json_field "$scratch/state-builds.json" queued)"
check "the last run is ok" "True" "$(json_last "$scratch/state-builds.json" ok)"
check "the last run exited 0" "0" "$(json_last "$scratch/state-builds.json" exitCode)"
check "the state names the current build" "build-2" "$(json_field "$scratch/state-builds.json" current)"

if [ -f "$scratch/web/runs.log" ]; then
  runs="$(wc -l < "$scratch/web/runs.log" | tr -d ' ')"
else
  runs=0
fi
check "the build ran twice (runs.log)" "2" "$runs"
check "the project's build:api script was never used" "missing" \
  "$([ -e "$scratch/web/wrong-command.log" ] && echo present || echo missing)"

echo
echo "== every build went to .site/build-<n> through astro --outDir, and current follows the last good one =="
python3 - "$scratch/web" <<'PY'
import json
import os
import sys

web = sys.argv[1]
site = os.path.join(web, ".site")
runs_log = os.path.join(web, "runs.log")
runs = [json.loads(line) for line in open(runs_log)] if os.path.exists(runs_log) else []
current_html = os.path.join(site, "current", "index.html")
failures = []


def check(description, condition, detail=""):
    if condition:
        print(f"ok   - {description}")
    else:
        print(f"FAIL - {description}{': ' + detail if detail else ''}", file=sys.stderr)
        failures.append(description)


check("the fake build ran twice", len(runs) == 2, repr(runs))
check(
    "each build ran `astro build --outDir .site/build-<n>`",
    len(runs) == 2
    and [run["args"][:2] for run in runs] == [["build", "--outDir"], ["build", "--outDir"]]
    and [run["outDir"] for run in runs] == [".site/build-1", ".site/build-2"],
    repr([run["args"] for run in runs]),
)
check(
    "each build got API_URL and SITE_URL and never WEB_FIXTURES",
    len(runs) == 2
    and all(
        run["apiUrl"] == "http://web"
        and run["siteUrl"] == "https://preview.example.ts.net"
        and run["webFixtures"] is None
        for run in runs
    ),
    repr(runs),
)
check(
    "both builds are on disk",
    os.path.isdir(os.path.join(site, "build-1")) and os.path.isdir(os.path.join(site, "build-2")),
    repr(sorted(os.listdir(site)) if os.path.isdir(site) else "no .site directory"),
)
check(
    "current is a symlink to the newest successful build",
    os.path.islink(os.path.join(site, "current")) and os.readlink(os.path.join(site, "current")) == "build-2",
    repr(os.path.islink(os.path.join(site, "current"))),
)
check(
    "current serves the newest build",
    os.path.exists(current_html) and open(current_html).read() == "serving .site/build-2\n",
)

if failures:
    print(f"\n{len(failures)} assertion(s) failed", file=sys.stderr)
    sys.exit(1)
PY
assert_rc=$?
if [ "$assert_rc" -ne 0 ]; then
  echo "FAIL - the build output assertions failed" >&2
  exit 1
fi

echo
echo "== a build in flight leaves the previous site serving =="
post "$scratch/post4.json" > /dev/null
check "current still serves the previous build while the next one runs" "serving .site/build-2" "$(current_html)"
wait_idle inflight || exit 1
check "the new build is published once it exits 0" "serving .site/build-3" "$(current_html)"
check "current points at the new build" "build-3" "$(current_build)"
check "only the last two builds are kept" "build-2 build-3" "$(kept_builds)"
check "the oldest build directory is gone" "missing" \
  "$([ -d "$SITE/build-1" ] && echo present || echo missing)"

echo
echo "== a failing build keeps the previous site and is reported =="
touch "$scratch/web/fail-flag"
post "$scratch/post5.json" > /dev/null
wait_idle failure || exit 1
check "the failing run exited 1" "1" "$(json_last "$scratch/state-failure.json" exitCode)"
check "the failing run is not ok" "False" "$(json_last "$scratch/state-failure.json" ok)"
check "runs counts the failure too" "4" "$(json_field "$scratch/state-failure.json" runs)"
check "current still serves the previous good build" "serving .site/build-3" "$(current_html)"
check "the failed build is not published" "build-3" "$(current_build)"
check "the failed build directory is not kept" "missing" \
  "$([ -d "$SITE/build-4" ] && echo present || echo missing)"
check "the kept builds are unchanged" "build-2 build-3" "$(json_builds "$scratch/state-failure.json")"

echo
echo "== a build that cannot even start is reported =="
mv "$scratch/web/node_modules/.bin/astro" "$scratch/web/node_modules/.bin/astro-gone"
post "$scratch/post6.json" > /dev/null
wait_idle broken || exit 1
check "a missing astro binary is not ok" "False" "$(json_last "$scratch/state-broken.json" ok)"
check "the failure is reported in the state" "True" \
  "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["last"]["failure"] is not None)' "$scratch/state-broken.json")"
check "current survives the failure" "serving .site/build-3" "$(current_html)"

echo
echo "== a prune failure after the swap never takes the published build down =="
mv "$scratch/web/node_modules/.bin/astro-gone" "$scratch/web/node_modules/.bin/astro"
rm -f "$scratch/web/fail-flag"
# build-2 is the next obsolete build: a read-only subdirectory makes removing it fail with EACCES.
mkdir -p "$SITE/build-2/locked" && touch "$SITE/build-2/locked/file" && chmod 555 "$SITE/build-2/locked"
post "$scratch/post7.json" > /dev/null
wait_idle prune || exit 1
chmod 755 "$SITE/build-2/locked"
check "the build is published even though pruning failed" "build-4" "$(current_build)"
check "current serves the new build" "serving .site/build-4" "$(current_html)"
check "the run is reported ok" "True" "$(json_last "$scratch/state-prune.json" ok)"

echo
echo "== unknown route =="
check "GET /nope answers 404" "404" "$(curl -sS -o "$scratch/notfound.json" -w '%{http_code}' "http://127.0.0.1:$port/nope")"

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
