#!/usr/bin/env bash
#
# Tests of `deploy/preview.sh` itself (T14b, S20).
#
#   deploy/test/preview-script-test.sh
#
# The preview's whole point is that it cannot start half-configured, so the checks here are about the
# refusals: every command that would touch Docker or Tailscale must stop before doing anything when
# `deploy/preview.env` is missing or incomplete, and must say exactly what is missing. Nothing here
# starts a container, a Funnel or a build, and no `docker` or `tailscale` process may ever run: both
# are replaced by a fake on PATH that fails loudly if the script reaches them.
#
# Also checked: `funnel-off` resets the Funnel configuration whether or not a `Funnel on` line is
# reported, `down` stops sharing *before* it validates the env file, a `PREVIEW_URL` that would break
# the one origin (trailing slash, plain http) is refused naming the key, and the scripts parse
# (`bash -n`).
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

check_contains() { # description file needle
  if grep -qF -- "$3" "$2"; then
    echo "ok   - $1"
    passed=$((passed + 1))
  else
    echo "FAIL - $1: '$3' not found in $2" >&2
    failed=$((failed + 1))
  fi
}

echo "== the scripts parse =="
for script in deploy/preview.sh deploy/e2e.sh deploy/test/*.sh; do
  if bash -n "$script" 2> "$scratch/syntax.err"; then
    echo "ok   - bash -n $script"
    passed=$((passed + 1))
  else
    echo "FAIL - bash -n $script:" >&2
    cat "$scratch/syntax.err" >&2
    failed=$((failed + 1))
  fi
done
if node --check deploy/rebuilder/server.mjs 2> "$scratch/mjs.err"; then
  echo "ok   - node --check deploy/rebuilder/server.mjs"
  passed=$((passed + 1))
else
  echo "FAIL - node --check deploy/rebuilder/server.mjs:" >&2
  cat "$scratch/mjs.err" >&2
  failed=$((failed + 1))
fi

# `docker` and `tailscale` are faked: reaching the real tools is a bug, and exit code 99 makes that
# unmistakable (the refusals exit 2). The fake `tailscale` records what it was asked to do and can
# report a running Funnel, so `funnel-off` can be tested without touching the real one.
mkdir -p "$scratch/bin"
cat > "$scratch/bin/tailscale" <<EOF
#!/usr/bin/env bash
# Fake Tailscale: answers \`funnel status\`, accepts \`funnel reset\`, refuses everything else.
echo "tailscale \$*" >> "$scratch/tailscale.log"
if [ "\${1:-}" = funnel ]; then
  case "\${2:-}" in
    status)
      if [ -n "\${FAKE_FUNNEL_ON:-}" ]; then
        printf '# Funnel on:\nhttps://ubuntu-dev.tail3b8c9d.ts.net\n'
      fi
      exit 0
      ;;
    reset) exit 0 ;;
  esac
fi
echo "FAIL: preview.sh reached the real tailscale (\$*)" >&2
exit 99
EOF
chmod +x "$scratch/bin/tailscale"
cat > "$scratch/bin/docker" <<'EOF'
#!/usr/bin/env bash
# Fake Docker. With FAKE_DOCKER=compose it answers the handful of subcommands `preview.sh` uses and
# records every call; anything else, and every call when the mode is unset, fails loudly.
echo "docker $*" >> "${FAKE_DOCKER_LOG:-/dev/null}"
if [ "${FAKE_DOCKER:-refuse}" != "compose" ]; then
  echo "FAIL: preview.sh reached the real docker ($*)" >&2
  exit 99
fi
case "$*" in
  *"exec -T rebuilder"*)
    # The sidecar's POST/GET helper answers with the idle, successful state of one finished build.
    printf '%s\n' '{"building":false,"queued":false,"runs":1,"current":"build-1","last":{"run":1,"exitCode":0,"ok":true}}'
    ;;
  *"build api"* | *"up -d --wait"* | *"down"* | *"ps"* | *"logs"*) ;;
  *)
    echo "FAIL: unexpected docker call ($*)" >&2
    exit 99
    ;;
  esac
EOF
chmod +x "$scratch/bin/docker"
# The site build must never run on the host: `up` asks the sidecar instead (fix B of the T14c review).
cat > "$scratch/bin/npm" <<'EOF'
#!/usr/bin/env bash
echo "FAIL: preview.sh built the site on the host (npm $*)" >&2
exit 99
EOF
chmod +x "$scratch/bin/npm"

# Runs preview.sh with the fake tools first on PATH.
run_preview() { # env-file command...
  local env_file="$1"
  shift
  set +e
  PATH="$scratch/bin:$PATH" PREVIEW_ENV_FILE="$env_file" deploy/preview.sh "$@" \
    > "$scratch/out.txt" 2> "$scratch/err.txt"
  last_rc=$?
  set -e
}

# A complete env file for the checks that need the script to get past the validation.
write_env() { # file preview-url
  cat > "$1" <<EOF
PREVIEW_URL=$2
POSTGRES_PASSWORD=scratch-postgres-password
RABBITMQ_DEFAULT_PASS=scratch-rabbit-password
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=preview@example.com
SPRING_MAIL_PASSWORD=scratch-smtp-password
RINDETUPLATA_MAIL_FROM="rindetuplata <preview@example.com>"
RINDETUPLATA_AUTH_ADMINEMAILS=preview@example.com
EOF
}

echo
echo "== every command refuses without deploy/preview.env =="
for command in up down status logs rebuild funnel-on; do
  run_preview "$scratch/absent.env" "$command"
  check "$command without the env file exits 2" "2" "$last_rc"
  check_contains "$command names the missing file" "$scratch/err.txt" "$scratch/absent.env"
  if grep -q 'reached the real' "$scratch/err.txt" "$scratch/out.txt"; then
    echo "FAIL - $command reached a real tool before validating the env file" >&2
    failed=$((failed + 1))
  fi
done

echo
echo "== funnel-off needs no env file and always resets, so sharing can always be stopped =="
: > "$scratch/tailscale.log"
run_preview "$scratch/absent.env" funnel-off
check "funnel-off without the env file exits 0" "0" "$last_rc"
check_contains "funnel-off resets even when no Funnel line is reported" "$scratch/tailscale.log" "tailscale funnel reset"
: > "$scratch/tailscale.log"
FAKE_FUNNEL_ON=1 run_preview "$scratch/absent.env" funnel-off
check "funnel-off with a running Funnel exits 0" "0" "$last_rc"
check_contains "funnel-off resets the Funnel configuration" "$scratch/tailscale.log" "tailscale funnel reset"
check_contains "funnel-off reports that the public URL is gone" "$scratch/out.txt" "the public URL no longer reaches this machine"

echo
echo "== down stops sharing before it needs the env file =="
: > "$scratch/tailscale.log"
run_preview "$scratch/absent.env" down
check "down without the env file still exits 2" "2" "$last_rc"
check_contains "down still names the missing file" "$scratch/err.txt" "$scratch/absent.env"
check_contains "down turned the Funnel off first" "$scratch/tailscale.log" "tailscale funnel reset"

echo
echo "== a PREVIEW_URL that would break the one origin is refused, naming the key =="
write_env "$scratch/trailing-slash.env" "https://preview.example.ts.net/"
run_preview "$scratch/trailing-slash.env" up
check "a trailing slash in PREVIEW_URL exits 2" "2" "$last_rc"
check_contains "it names the key PREVIEW_URL" "$scratch/err.txt" "PREVIEW_URL"
check_contains "it says the trailing slash is the problem" "$scratch/err.txt" "trailing slash"
check_contains "it shows the value without the slash" "$scratch/err.txt" "https://preview.example.ts.net"

write_env "$scratch/http.env" "http://preview.example.ts.net"
run_preview "$scratch/http.env" status
check "a non-https PREVIEW_URL exits 2" "2" "$last_rc"
check_contains "it names the key PREVIEW_URL" "$scratch/err.txt" "PREVIEW_URL"
check_contains "it requires https" "$scratch/err.txt" "https://"
if grep -qE 'reached the real|unexpected docker' "$scratch/err.txt" "$scratch/out.txt"; then
  echo "FAIL - a bad PREVIEW_URL reached Docker or Tailscale" >&2
  failed=$((failed + 1))
else
  echo "ok   - a bad PREVIEW_URL stops before Docker and Tailscale"
  passed=$((passed + 1))
fi

write_env "$scratch/good.env" "https://preview.example.ts.net"
FAKE_DOCKER=compose FAKE_DOCKER_LOG="$scratch/docker-status.log" run_preview "$scratch/good.env" status
check "a valid PREVIEW_URL is not refused" "0" "$last_rc"

echo
echo "== up builds the site through the sidecar, never on the host =="
write_env "$scratch/up.env" "https://preview.example.ts.net"
: > "$scratch/docker-up.log"
FAKE_DOCKER=compose FAKE_DOCKER_LOG="$scratch/docker-up.log" run_preview "$scratch/up.env" up
check "up exits 0" "0" "$last_rc"
check_contains "up builds the API image" "$scratch/docker-up.log" "build api"
check_contains "up starts the stack with --wait" "$scratch/docker-up.log" "up -d --wait"
check_contains "up asks the sidecar for the first build and waits for it" "$scratch/docker-up.log" "exec -T rebuilder"
if grep -q 'built the site on the host' "$scratch/err.txt" "$scratch/out.txt"; then
  echo "FAIL - up built the site on the host" >&2
  failed=$((failed + 1))
else
  echo "ok   - up never runs the site build on the host"
  passed=$((passed + 1))
fi
check_contains "up points at funnel-on as the next step" "$scratch/out.txt" "funnel-on"

echo
echo "== PREVIEW_PROFILES adds a Compose profile (the mailpit escape hatch) =="
: > "$scratch/docker-profiles.log"
FAKE_DOCKER=compose FAKE_DOCKER_LOG="$scratch/docker-profiles.log" PREVIEW_PROFILES=mailpit \
  run_preview "$scratch/up.env" up
check "up with PREVIEW_PROFILES exits 0" "0" "$last_rc"
check_contains "the Compose call enables that profile" "$scratch/docker-profiles.log" "--profile mailpit"
check_contains "the stack profile is still enabled" "$scratch/docker-profiles.log" "--profile stack"
: > "$scratch/docker-noprofiles.log"
FAKE_DOCKER=compose FAKE_DOCKER_LOG="$scratch/docker-noprofiles.log" run_preview "$scratch/up.env" status
if grep -q -- '--profile mailpit' "$scratch/docker-noprofiles.log"; then
  echo "FAIL - mailpit is enabled without PREVIEW_PROFILES" >&2
  failed=$((failed + 1))
else
  echo "ok   - no extra profile without PREVIEW_PROFILES"
  passed=$((passed + 1))
fi

echo
echo "== an incomplete env file names exactly the missing keys =="
cat > "$scratch/incomplete.env" <<'EOF'
PREVIEW_URL=https://preview.example.ts.net
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_USERNAME=preview@example.com
SPRING_MAIL_PASSWORD=scratch
RINDETUPLATA_MAIL_FROM="rindetuplata <preview@example.com>"
RINDETUPLATA_AUTH_ADMINEMAILS=preview@example.com
POSTGRES_PASSWORD=
EOF
run_preview "$scratch/incomplete.env" up
check "an incomplete env file exits 2" "2" "$last_rc"
check_contains "it names POSTGRES_PASSWORD" "$scratch/err.txt" "POSTGRES_PASSWORD"
check_contains "it names RABBITMQ_DEFAULT_PASS" "$scratch/err.txt" "RABBITMQ_DEFAULT_PASS"
if grep -q -- '- PREVIEW_URL' "$scratch/err.txt"; then
  echo "FAIL - a key that is present was reported as missing" >&2
  failed=$((failed + 1))
else
  echo "ok   - it does not report keys that are present"
  passed=$((passed + 1))
fi

echo
echo "== usage =="
run_preview "$scratch/absent.env" nonsense
check "an unknown command exits 2" "2" "$last_rc"
check_contains "an unknown command prints the usage" "$scratch/err.txt" "deploy/preview.sh <command>"
run_preview "$scratch/absent.env" help
check "help exits 0" "0" "$last_rc"
for line in up down status logs rebuild funnel-on funnel-off; do
  check_contains "help mentions $line" "$scratch/out.txt" "$line"
done

echo
echo "== the committed template matches the required keys =="
for key in PREVIEW_URL POSTGRES_PASSWORD RABBITMQ_DEFAULT_PASS SPRING_MAIL_HOST SPRING_MAIL_USERNAME \
  SPRING_MAIL_PASSWORD RINDETUPLATA_MAIL_FROM RINDETUPLATA_AUTH_ADMINEMAILS; do
  if grep -qE "^${key}=.+" deploy/preview.env.example; then
    echo "ok   - deploy/preview.env.example sets $key"
    passed=$((passed + 1))
  else
    echo "FAIL - deploy/preview.env.example has no value for $key" >&2
    failed=$((failed + 1))
  fi
done
if grep -qE '^PREVIEW_URL=.*example|^PREVIEW_URL=.*ts\.net' deploy/preview.env.example; then
  echo "ok   - the template's PREVIEW_URL is a placeholder, not a secret"
  passed=$((passed + 1))
else
  echo "FAIL - the template's PREVIEW_URL does not look like a placeholder" >&2
  failed=$((failed + 1))
fi

echo
echo "== the real secrets file stays untracked =="
if git check-ignore -q deploy/preview.env; then
  echo "ok   - deploy/preview.env is gitignored"
  passed=$((passed + 1))
else
  echo "FAIL - deploy/preview.env is not gitignored" >&2
  failed=$((failed + 1))
fi

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
