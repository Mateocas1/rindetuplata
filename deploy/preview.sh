#!/usr/bin/env bash
#
# Private preview (T14b, S20): the whole product on this machine, published through Tailscale Funnel.
#
#   deploy/preview.sh up          # build the API image, start the stack, build the site via the sidecar
#   deploy/preview.sh funnel-on   # serve it on the public Funnel URL (prints the URL)
#   deploy/preview.sh status      # containers, Funnel state, last site rebuild
#   deploy/preview.sh logs [svc]  # follow the stack's logs (all services by default)
#   deploy/preview.sh rebuild     # ask the sidecar for a site rebuild right now
#   deploy/preview.sh funnel-off  # stop sharing; the stack keeps serving 127.0.0.1
#   deploy/preview.sh down        # stop the stack (the data volumes survive)
#
# `funnel-off` is the one command that never reads the env file: stopping the exposure of this machine
# must not depend on a secrets file that may be missing or broken.
#
# Everything runs in its own Compose project (`PREVIEW_PROJECT`, default `rindetuplata-preview`) with
# `compose.yaml` + `deploy/compose.preview.yaml`, so this preview never touches the developer's local
# `stack` profile project or its `deploy/e2e.sh` runs, and `down` can only remove what `up` started.
#
# Secrets live in `deploy/preview.env` (gitignored; template `deploy/preview.env.example`). `up` and
# every Compose command refuse to run without a filled-in env file, naming the file and, when it
# exists but is incomplete, the exact keys that are missing.
#
# Environments (all optional; the defaults are the intended ones):
#   PREVIEW_PROJECT    Compose project name                rindetuplata-preview
#   PREVIEW_ENV_FILE   env file with the secrets           deploy/preview.env
#   PREVIEW_PROFILES   extra Compose profile(s) to enable (comma separated), e.g. `mailpit`
#   PREVIEW_BUILD_TIMEOUT  seconds `up` waits for the first sidecar build                    600
set -Eeuo pipefail

cd "$(dirname "$0")/.."

PREVIEW_PROJECT="${PREVIEW_PROJECT:-rindetuplata-preview}"
PREVIEW_ENV_FILE="${PREVIEW_ENV_FILE:-deploy/preview.env}"
PREVIEW_ENV_EXAMPLE="deploy/preview.env.example"
PREVIEW_PROFILES="${PREVIEW_PROFILES:-}"
BUILD_TIMEOUT_SECONDS="${PREVIEW_BUILD_TIMEOUT:-600}"

# The keys `deploy/compose.preview.yaml` asks for with `${VAR:?}`; `WEB_PORT` and `SPRING_MAIL_PORT`
# are optional (they have defaults).
REQUIRED_KEYS=(
  PREVIEW_URL
  POSTGRES_PASSWORD
  RABBITMQ_DEFAULT_PASS
  SPRING_MAIL_HOST
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  RINDETUPLATA_MAIL_FROM
  RINDETUPLATA_AUTH_ADMINEMAILS
)

usage() {
  cat <<'EOF'
deploy/preview.sh <command>

  up           build the API image, start the preview stack and build the site through the sidecar
  down         stop the stack and stop sharing; the named volumes keep the data
  status       containers of the preview project, Funnel state and the last site rebuild
  logs [svc]   follow the logs (all services, or the named ones)
  rebuild      ask the rebuild sidecar for a site build right now
  funnel-on    serve 127.0.0.1:${WEB_PORT} on the public Tailscale Funnel URL
  funnel-off   stop sharing (the stack keeps running on this machine; needs no env file)

Secrets and settings come from deploy/preview.env (see deploy/preview.env.example).
PREVIEW_PROFILES=mailpit tests the preview with the local mail catcher instead of the real SMTP account.
EOF
}

# The Compose invocation the whole preview shares: its own project, the base file and the preview
# overlay, the env file, and the profiles. `stack` starts postgres, rabbitmq, api, web and the rebuild
# sidecar (mailpit stays off); `PREVIEW_PROFILES` adds whatever else is wanted, for example `mailpit` to
# test the preview with the local mail catcher instead of the real SMTP account.
compose() {
  local profiles=(--profile stack)
  if [ -n "$PREVIEW_PROFILES" ]; then
    profiles+=(--profile "$PREVIEW_PROFILES")
  fi
  docker compose \
    --project-name "$PREVIEW_PROJECT" \
    --env-file "$PREVIEW_ENV_FILE" \
    --file compose.yaml \
    --file deploy/compose.preview.yaml \
    "${profiles[@]}" \
    "$@"
}

# Value of a key in the env file, with surrounding quotes stripped (an empty line means "not set").
env_value() {
  sed -n "s/^[[:space:]]*\(export[[:space:]]\+\)\?$1=//p" "$PREVIEW_ENV_FILE" | tail -n 1 | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"
}

# Keys that are absent or empty in the env file, one per line.
missing_keys() {
  local key
  for key in "${REQUIRED_KEYS[@]}"; do
    if ! grep -qE "^[[:space:]]*(export[[:space:]]+)?${key}=.+" "$PREVIEW_ENV_FILE"; then
      echo "$key"
    fi
  done
}

# Refuses to continue without a complete env file, naming exactly what is missing. Nothing runs before
# this: no Docker command, no Tailscale command, no build.
require_env_file() {
  if [ ! -f "$PREVIEW_ENV_FILE" ]; then
    cat >&2 <<EOF
missing $PREVIEW_ENV_FILE: there is nothing to start without the preview's secrets.

  cp $PREVIEW_ENV_EXAMPLE $PREVIEW_ENV_FILE
  chmod 600 $PREVIEW_ENV_FILE
  \$EDITOR $PREVIEW_ENV_FILE        # set at least: ${REQUIRED_KEYS[*]}

This file is gitignored on purpose: it holds the SMTP app password and the database password.
EOF
    exit 2
  fi

  local missing
  missing="$(missing_keys)"
  if [ -n "$missing" ]; then
    echo "$PREVIEW_ENV_FILE is incomplete; set these keys (see $PREVIEW_ENV_EXAMPLE):" >&2
    echo "$missing" | sed 's/^/  - /' >&2
    exit 2
  fi
}

# The exported values Compose interpolates and the script itself needs. `PREVIEW_UID`/`PREVIEW_GID`
# make the rebuild sidecar run as this user, so the files it writes into web/.site stay writable for
# the next host-side build.
load_settings() {
  export PREVIEW_UID="$(id -u)"
  export PREVIEW_GID="$(id -g)"
  export WEB_PORT="$(env_value WEB_PORT)"
  if [ -z "$WEB_PORT" ]; then
    WEB_PORT=4321
    export WEB_PORT
  fi
  PREVIEW_URL_VALUE="$(env_value PREVIEW_URL)"
  require_valid_preview_url
}

# Refuses a `PREVIEW_URL` that would silently break the one origin, naming the key. Compose
# interpolates the raw value into the magic link, the alert page and the allowed browser origin, so a
# trailing slash turns the link into `...//ingresar` and makes the allowed origin disagree with the
# page's, and a non-https value means the session and CSRF cookies are not `Secure` (fix D of the
# T14c review).
require_valid_preview_url() {
  case "$PREVIEW_URL_VALUE" in
    '') return 0 ;;                             # an absent key is `require_env_file`'s job
    https://*) ;;
    *)
      cat >&2 <<EOF
$PREVIEW_ENV_FILE has a PREVIEW_URL that is not an https:// URL:
  PREVIEW_URL=$PREVIEW_URL_VALUE

Tailscale Funnel serves the preview over HTTPS, and PREVIEW_URL is the single origin the browser,
the magic link, the alert page, the allowed CORS origin and the Secure cookies all use. Set it to
the URL 'deploy/preview.sh funnel-on' prints, for example https://node.tailnet.ts.net
EOF
      exit 2
      ;;
  esac

  case "$PREVIEW_URL_VALUE" in
    */)
      cat >&2 <<EOF
$PREVIEW_ENV_FILE has a trailing slash in PREVIEW_URL:
  PREVIEW_URL=$PREVIEW_URL_VALUE
  PREVIEW_URL=${PREVIEW_URL_VALUE%/}

Compose appends the routes to PREVIEW_URL, so a trailing slash turns the magic link into
...//ingresar and makes the allowed browser origin disagree with the page's origin (CORS).
EOF
      exit 2
      ;;
  esac
}

funnel_is_on() {
  command -v tailscale >/dev/null 2>&1 && tailscale funnel status 2>/dev/null | grep -q 'Funnel on'
}

# The public URL of the running Funnel, when Tailscale reports one.
funnel_url() {
  tailscale funnel status 2>/dev/null | grep -oE 'https://[A-Za-z0-9._-]+\.ts\.net' | head -n 1
}

# The sidecar's state (`GET /`), through the container that runs it.
rebuilder_state() {
  compose exec -T rebuilder node -e \
    "fetch('http://127.0.0.1:8787/').then((r) => r.text()).then((t) => process.stdout.write(t)).catch((e) => { process.stderr.write(e.message + '\n'); process.exit(1); })"
}

# Asks the sidecar for one build and waits for *that* build to finish, printing the final state. The
# loop runs inside the sidecar container (nothing else on this host can reach it) and exits non-zero when
# the wait times out or the build failed. Waiting for the run this POST started (and not for every later
# rebuild, e.g. the one the startup ingestion triggers) keeps `up` from blocking for minutes, and `up`
# never runs the site build itself: one builder, one output directory, no race over the published site.
rebuilder_build_and_wait() { # timeout-seconds
  compose exec -T rebuilder node -e '
const timeoutSeconds = Number(process.argv[1]);
const deadline = Date.now() + timeoutSeconds * 1000;
const url = "http://127.0.0.1:8787/";
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
(async () => {
  const posted = await (await fetch(url, { method: "POST" })).json();
  // A POST that arrived while another build was running is covered by that queued rerun, the next run.
  const target = posted.status === "queued" ? posted.runs + 1 : posted.runs;
  let state = posted;
  while ((!state.last || state.last.run < target) && Date.now() < deadline) {
    await sleep(1000);
    state = await (await fetch(url)).json();
  }
  process.stdout.write(JSON.stringify(state) + "\n");
  if (!state.last || state.last.run < target) {
    process.stderr.write("the site build did not finish within " + timeoutSeconds + "s\n");
    process.exit(1);
  }
  if (!state.last.ok) {
    process.stderr.write("the site build failed" + (state.last.failure ? ": " + state.last.failure : "") + "\n");
    process.exit(1);
  }
  process.stderr.write("published " + state.last.build + "\n");
})();
' "$1"
}

cmd_up() {
  require_env_file
  load_settings

  if [ ! -d web/node_modules ]; then
    echo "web/node_modules is missing, and the rebuild sidecar's site build needs it: run 'cd web && npm ci' first." >&2
    exit 2
  fi

  # Caddy mounts web/.site read-only on /srv and serves /srv/current, and the sidecar publishes the
  # builds inside it: the directory has to exist before Compose binds it (Docker would create it owned
  # by root). Nothing is published in it yet, so Caddy answers 404 until the first build is published.
  mkdir -p web/.site

  echo "== building the API image (project '$PREVIEW_PROJECT') =="
  compose build api

  echo "== starting the preview stack =="
  compose up -d --wait

  # The site is prerendered, so it does not exist until a build runs. The sidecar is the only builder:
  # `up` asks it for the first build and waits, so the Funnel never serves a half-written directory, and
  # the startup ingestion of T14a can trigger its own rebuild without racing this one.
  echo "== building the site through the rebuild sidecar (SITE_URL=$PREVIEW_URL_VALUE) =="
  if ! rebuilder_build_and_wait "$BUILD_TIMEOUT_SECONDS"; then
    echo >&2
    echo "the first site build did not succeed, so the preview would serve nothing yet." >&2
    echo "The stack is up: read the build log with 'deploy/preview.sh logs rebuilder', and ask for a new" >&2
    echo "build with 'deploy/preview.sh rebuild' once the cause is fixed." >&2
    exit 1
  fi

  echo
  echo "preview stack is up. Next:"
  echo "  deploy/preview.sh funnel-on     # publish it on the Funnel URL"
  echo "  deploy/preview.sh status        # containers, Funnel state, last rebuild"
}

cmd_down() {
  # Stop sharing first and *before* the env file is read: a stack that is stopped under a live Funnel
  # answers errors to the internet, and an unreachable or broken secrets file must never be able to
  # keep this machine exposed (fix C of the T14c review).
  if ! cmd_funnel_off; then
    echo "warning: could not turn the Funnel off; check 'tailscale funnel status'." >&2
  fi

  require_env_file
  load_settings

  compose down
  echo
  echo "preview stack stopped. The volumes ${PREVIEW_PROJECT}_pgdata and ${PREVIEW_PROJECT}_rabbitmq_data keep"
  echo "the accounts, the alert rules and the queued events; 'deploy/preview.sh up' resumes with them."
  echo "Only 'docker compose --project-name $PREVIEW_PROJECT down --volumes' would delete them."
}

cmd_status() {
  require_env_file
  load_settings

  echo "== containers (project '$PREVIEW_PROJECT') =="
  compose ps

  echo
  echo "== funnel =="
  if funnel_is_on; then
    echo "on: $(funnel_url)"
    tailscale funnel status 2>/dev/null | sed 's/^/    /'
  else
    echo "off (nothing on this machine is published; 'deploy/preview.sh funnel-on' turns it on)"
  fi

  echo
  echo "== last site rebuild (sidecar) =="
  if rebuilder_state; then
    :
  else
    echo "the rebuild sidecar is not answering; is the stack up? ('deploy/preview.sh up')" >&2
  fi
}

cmd_logs() {
  require_env_file
  load_settings
  compose logs --follow --tail 100 "$@"
}

cmd_rebuild() {
  require_env_file
  load_settings
  echo "== asking the sidecar for a site rebuild =="
  compose exec -T rebuilder node -e \
    "fetch('http://127.0.0.1:8787/', { method: 'POST' }).then((r) => r.text()).then((t) => process.stdout.write(t)).catch((e) => { process.stderr.write(e.message + '\n'); process.exit(1); })"
  echo "the build runs in the background and the published site only changes when it exits 0;"
  echo "watch it with 'deploy/preview.sh logs rebuilder'."
}

cmd_funnel_on() {
  require_env_file
  load_settings

  if ! command -v tailscale >/dev/null 2>&1; then
    echo "tailscale is not installed (S20 assumes it is logged in on this machine)." >&2
    exit 2
  fi

  # Funnel proxies 127.0.0.1:${WEB_PORT} on this machine to the tailnet HTTPS URL. It needs two things
  # in the Tailscale admin console, done once by the owner: HTTPS certificates enabled, and the
  # Funnel node attribute allowed (docs/deploy.md).
  tailscale funnel --bg "$WEB_PORT"

  local url
  url="$(funnel_url)"
  echo
  if [ -n "$url" ]; then
    echo "public URL: $url"
    echo "PREVIEW_URL in $PREVIEW_ENV_FILE must be exactly that URL (no trailing slash)."
  else
    echo "funnel started on port $WEB_PORT; run 'tailscale funnel status' for the public URL."
  fi
  echo "the stack keeps the noindex header and is unlisted: share the URL only with the people testing it."
}

cmd_funnel_off() {
  if ! command -v tailscale >/dev/null 2>&1; then
    echo "tailscale is not installed: nothing to turn off." >&2
    return 0
  fi

  # `tailscale funnel reset` drops the Funnel configuration (the docs pair it with `serve reset` for
  # tailnet-only sharing, which the preview never uses). It runs unconditionally: `funnel status` is
  # not a reliable gate, and resetting what is already off is harmless, while failing to reset a live
  # Funnel leaves the machine exposed (fix C of the T14c review).
  tailscale funnel reset
  echo "funnel off: the public URL no longer reaches this machine."
}

case "${1:-}" in
  up)
    shift
    cmd_up "$@"
    ;;
  down)
    shift
    cmd_down "$@"
    ;;
  status)
    shift
    cmd_status "$@"
    ;;
  logs)
    shift
    cmd_logs "$@"
    ;;
  rebuild)
    shift
    cmd_rebuild "$@"
    ;;
  funnel-on)
    shift
    cmd_funnel_on "$@"
    ;;
  funnel-off)
    shift
    cmd_funnel_off "$@"
    ;;
  '' | help | -h | --help)
    usage
    ;;
  *)
    echo "unknown command: $1" >&2
    echo >&2
    usage >&2
    exit 2
    ;;
esac
