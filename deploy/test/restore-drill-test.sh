#!/usr/bin/env bash
#
# The documented VPS Compose commands load the whole project (finding F2 of a review).
#
#   deploy/test/restore-drill-test.sh        (needs the docker CLI; nothing is started, no daemon needed for config)
#
# `compose.yaml` puts `api` in the `stack` profile and `deploy/compose.vps.yaml` makes `caddy` depend on it, so a
# VPS command that does not load that profile fails with `service "caddy" depends on undefined service "api":
# invalid compose project`. The restore drill, the timer's backup and `restore.sh` all run Compose with only
# `--env-file`, so the profile has to come from the env file (`COMPOSE_PROFILES=stack`, the template
# `deploy/vps.env.example`). This test copies that template, then:
#   - runs every `"${compose[@]}" ...` command of the restore drill in docs/deploy.md with `--dry-run`;
#   - runs `deploy/backup.sh` and `deploy/restore.sh` with their default Compose files against a `docker` shim that
#     renders the project (`config --quiet`) with the exact flags the script built, instead of running pg_dump.
# A command fails the test on any "invalid compose project", and on any non-zero exit except the two runtime
# messages a dry run cannot avoid when no container exists (`start` and `exec` of a stopped project).
set -Eeuo pipefail

cd "$(dirname "$0")/../.."
repo="$PWD"

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

template=deploy/vps.env.example
env_file="$scratch/api.env"
if [ ! -f "$template" ]; then
  echo "FAIL - $template is missing: the VPS env file has no documented template" >&2
  echo "passed: $passed, failed: $((failed + 1))"
  exit 1
fi
cp "$template" "$env_file"

echo "== the template =="
check "the template turns the stack profile on" "1" "$(grep -c '^COMPOSE_PROFILES=stack$' "$template")"
missing=""
while IFS='=' read -r key _; do
  grep -q -- "^$key=" docs/deploy.md || missing="$missing $key"
done < <(grep -E '^[A-Z][A-Z0-9_]*=' "$template")
check "docs/deploy.md lists every key of the template" "" "${missing# }"

echo
echo "== the restore drill of docs/deploy.md, with --dry-run =="
compose_line="$(grep -m1 '^compose=(docker compose ' docs/deploy.md || true)"
if [ -z "$compose_line" ]; then
  echo "FAIL - docs/deploy.md has no 'compose=(docker compose ...)' line in the restore drill" >&2
  failed=$((failed + 1))
else
  # The documented array, pointed at the scratch env file, plus the global --dry-run.
  eval "${compose_line//\/srv\/rindetuplata\/api.env/$env_file}"
  compose=("${compose[@]:0:2}" --dry-run "${compose[@]:2}")
  commands=0
  while IFS= read -r line; do
    commands=$((commands + 1))
    set +e
    eval "$line" > "$scratch/out.txt" 2>&1 < /dev/null # the documented line, its "# comment" column included
    rc=$?
    set -e
    tolerated=no
    if [ "$rc" -ne 0 ] && grep -Eq 'has no container to start|is not running|no container found' "$scratch/out.txt"; then
      tolerated=yes
    fi
    verdict=ok
    if grep -q 'invalid compose project' "$scratch/out.txt"; then verdict=invalid
    elif [ "$rc" -ne 0 ] && [ "$tolerated" = no ]; then verdict="rc=$rc"; fi
    check "documented command loads the project: ${line%%  *}" "ok" "$verdict"
    [ "$verdict" = ok ] || sed 's/^/         /' "$scratch/out.txt" >&2
  done < <(grep '^"\${compose\[@\]}" ' docs/deploy.md)
  check "the drill documents at least stop, start, up and two checks" "yes" "$([ "$commands" -ge 5 ] && echo yes || echo no)"
fi

echo
echo "== backup.sh and restore.sh build a Compose command that loads the project =="
real_docker="$(command -v docker)"
mkdir -p "$scratch/bin"
cat > "$scratch/bin/docker" <<SHIM
#!/usr/bin/env bash
# Stand-in for the docker CLI: renders the project with the flags the script built, in place of the exec.
prefix=()
while [ "\$#" -gt 0 ] && [ "\$1" != exec ]; do prefix+=("\$1"); shift; done
if [ "\$#" -eq 0 ]; then exec "$real_docker" "\${prefix[@]}"; fi
"$real_docker" "\${prefix[@]}" config --quiet || exit "\$?"
case "\$*" in
  *pg_dump*) echo "fake dump" ;;
  *) cat > /dev/null ;;
esac
SHIM
chmod +x "$scratch/bin/docker"

export BACKUP_DIR="$scratch/backups" BACKUP_ENV_FILE="$env_file"
set +e
PATH="$scratch/bin:$PATH" deploy/backup.sh > "$scratch/backup.out" 2>&1
check "backup.sh with the default Compose files and the template env file exits 0" "0" "$?"
dump="$(find "$BACKUP_DIR" -maxdepth 1 -name 'rindetuplata-*.dump' | head -n1)"
PATH="$scratch/bin:$PATH" deploy/restore.sh --confirm "${dump:-$BACKUP_DIR/missing.dump}" > "$scratch/restore.out" 2>&1
check "restore.sh with the default Compose files and the template env file exits 0" "0" "$?"
set -e
# The drill names a dump without a path: restore.sh must look for it where backup.sh writes (F4).
set +e
env -u BACKUP_DIR deploy/restore.sh --confirm rindetuplata-1999-01-01.dump > /dev/null 2> "$scratch/bare.err"
set -e
check "a bare dump name resolves in the default backup directory" "1" \
  "$(grep -cF 'dump not found or empty: /var/backups/rindetuplata/rindetuplata-1999-01-01.dump' "$scratch/bare.err")"
grep -q 'invalid compose project' "$scratch/backup.out" "$scratch/restore.out" && cat "$scratch/backup.out" "$scratch/restore.out" >&2
cd "$repo"

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
