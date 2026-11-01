#!/usr/bin/env bash
#
# Tests of `deploy/backup.sh` and `deploy/restore.sh` (T4c, S4).
#
#   deploy/test/backup-test.sh        (needs a running Docker daemon and the postgres:18-alpine image)
#
# A backup nobody restored is a hope, not a backup, so the main check is the round trip on a real
# database: a throwaway `postgres:18-alpine` container (unique name and Compose project, scratch
# directory from `mktemp -d`, both always removed by the trap) is seeded, dumped through `backup.sh`,
# damaged, and brought back through `restore.sh`. Around it: retention deletes only old dumps, a failing
# dump leaves no partial file and keeps every older dump, restore changes nothing without `--confirm`
# or with a dump it cannot read, and the optional off-box hook runs only after a good dump.
set -Eeuo pipefail

cd "$(dirname "$0")/../.."

scratch="$(mktemp -d)"
unique="rtp-backup-test-$$-${RANDOM}"
container="$unique-postgres"
compose_file="$scratch/compose.yaml"

cleanup() {
  docker compose -p "$unique" -f "$compose_file" down -v --timeout 5 > /dev/null 2>&1 || true
  docker rm -f "$container" > /dev/null 2>&1 || true
  rm -r "$scratch" 2> /dev/null || true
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

check_contains() { # description file needle
  if grep -qF -- "$3" "$2"; then
    echo "ok   - $1"
    passed=$((passed + 1))
  else
    echo "FAIL - $1: '$3' not found in $2:" >&2
    sed 's/^/         /' "$2" >&2
    failed=$((failed + 1))
  fi
}

echo "== the scripts parse =="
for script in deploy/backup.sh deploy/restore.sh; do
  if [ -x "$script" ] && bash -n "$script" 2> "$scratch/syntax.err"; then
    echo "ok   - $script is executable and parses"
    passed=$((passed + 1))
  else
    echo "FAIL - $script is missing, not executable or does not parse" >&2
    cat "$scratch/syntax.err" 2> /dev/null >&2 || true
    failed=$((failed + 1))
  fi
done

echo
echo "== the default backup directory is outside the checkout (F4) =="
# Inside the checkout `git clean -fdx` deletes the dumps and every docker build sends them as context.
default_dir=/var/backups/rindetuplata
mkdir -p "$scratch/shim"
cat > "$scratch/shim/mkdir" <<SHIM
#!/usr/bin/env bash
# Records the directory the script asks for instead of creating it (the default is not writable here).
echo "\${!#}" > "$scratch/mkdir.arg"
SHIM
chmod +x "$scratch/shim/mkdir"
set +e
env -u BACKUP_DIR PATH="$scratch/shim:$PATH" deploy/backup.sh > /dev/null 2>&1
set -e
check "backup.sh creates the default directory $default_dir" "$default_dir" "$(cat "$scratch/mkdir.arg" 2> /dev/null || true)"
set +e
env -u BACKUP_DIR deploy/restore.sh --confirm rindetuplata-1999-01-01.dump > /dev/null 2> "$scratch/restore-default.err"
rc=$?
set -e
check "a bare dump name resolves in the default directory (restore.sh exits 2)" "2" "$rc"
check_contains "restore.sh looks for the bare name in $default_dir" "$scratch/restore-default.err" "$default_dir/rindetuplata-1999-01-01.dump"
for file in deploy/backup.sh deploy/restore.sh deploy/systemd/rindetuplata-backup.service docs/deploy.md; do
  check "$file does not default into the checkout (/srv/rindetuplata/backups)" "0" "$(grep -c '/srv/rindetuplata/backups' "$file" || true)"
done
check "backups/ is git-ignored for anyone who overrides BACKUP_DIR inside the checkout" "yes" \
  "$(git check-ignore -q backups/rindetuplata-2026-11-01.dump && echo yes || echo no)"
check ".dockerignore keeps backups/ out of the build context" "yes" \
  "$(grep -qxE '(\*\*/)?backups/?' .dockerignore && echo yes || echo no)"

if ! docker info > /dev/null 2>&1; then
  echo "FAIL - no Docker daemon: the round trip needs a real postgres:18-alpine" >&2
  echo "passed: $passed, failed: $((failed + 1))"
  exit 1
fi

cat > "$compose_file" <<YAML
name: $unique
services:
  postgres:
    image: postgres:18-alpine
    container_name: $container
    environment:
      POSTGRES_DB: rindetuplata
      POSTGRES_USER: rindetuplata
      POSTGRES_PASSWORD: scratch-password
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "rindetuplata", "-d", "rindetuplata"]
      interval: 1s
      timeout: 3s
      retries: 30
YAML
docker compose -p "$unique" -f "$compose_file" up -d --wait > "$scratch/up.log" 2>&1 || {
  echo "FAIL - the throwaway postgres did not start:" >&2
  cat "$scratch/up.log" >&2
  exit 1
}

sql() { docker exec "$container" psql -U rindetuplata -d rindetuplata -Atq -v ON_ERROR_STOP=1 -c "$1"; }
rows() { sql "select id || ':' || label from account order by id" | paste -sd, -; }

# The scripts under test, pointed at the throwaway Compose project instead of the VPS one.
export BACKUP_COMPOSE_FILES="$compose_file"
export BACKUP_ENV_FILE=""
export BACKUP_DIR="$scratch/backups"
unset BACKUP_COPY_COMMAND BACKUP_RETENTION_DAYS BACKUP_SERVICE BACKUP_DB_NAME BACKUP_DB_USER

run() { # command... ; sets last_rc, writes $scratch/out.txt and $scratch/err.txt
  set +e
  "$@" > "$scratch/out.txt" 2> "$scratch/err.txt"
  last_rc=$?
  set -e
}

today_dump="$BACKUP_DIR/rindetuplata-$(date +%F).dump"
original="1:ana,2:bruno,3:cecilia ñandú"
sql "create table account (id int primary key, label text not null)"
sql "insert into account values (1,'ana'),(2,'bruno'),(3,'cecilia ñandú')"
check "the seed rows are there" "$original" "$(rows)"

echo
echo "== backup =="
run deploy/backup.sh
check "backup exits 0" "0" "$last_rc"
check "backup writes today's dump" "yes" "$([ -s "$today_dump" ] && echo yes || echo no)"
check "backup leaves nothing but the dump in the directory" "$(basename "$today_dump")" "$(ls -A "$BACKUP_DIR")"
check "the dump is a pg_dump custom-format archive" "PGDMP" "$(head -c 5 "$today_dump")"
check_contains "backup names the dump on stdout" "$scratch/out.txt" "$today_dump"

echo
echo "== restore refuses without confirmation =="
sql "delete from account where id = 2; insert into account values (4,'dario')"
damaged="1:ana,3:cecilia ñandú,4:dario"
check "the database is damaged on purpose" "$damaged" "$(rows)"
run deploy/restore.sh "$(basename "$today_dump")"
check "restore without --confirm exits 2" "2" "$last_rc"
check_contains "restore without --confirm explains the flag on stderr" "$scratch/err.txt" "--confirm"
check "restore without --confirm prints nothing on stdout" "" "$(cat "$scratch/out.txt")"
check "restore without --confirm changes nothing" "$damaged" "$(rows)"
run deploy/restore.sh
check "restore without arguments exits 2" "2" "$last_rc"
check_contains "restore without arguments prints the usage on stderr" "$scratch/err.txt" "usage:"

echo
echo "== restore refuses what it cannot read =="
run deploy/restore.sh --confirm "rindetuplata-1999-01-01.dump"
check "a missing dump exits 2" "2" "$last_rc"
check_contains "a missing dump is named on stderr" "$scratch/err.txt" "rindetuplata-1999-01-01.dump"
check "a missing dump changes nothing" "$damaged" "$(rows)"
printf 'this is not a dump\n' > "$scratch/garbage.dump"
run deploy/restore.sh --confirm "$scratch/garbage.dump"
check "a file that is not a dump exits non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check_contains "a file that is not a dump is reported on stderr" "$scratch/err.txt" "restore failed"
check "a file that is not a dump changes nothing" "$damaged" "$(rows)"

echo
echo "== restore round trip =="
run deploy/restore.sh --confirm "$(basename "$today_dump")"
check "restore --confirm by dump name exits 0" "0" "$last_rc"
check "the original rows are back, the damage is gone" "$original" "$(rows)"
sql "update account set label = 'changed' where id = 1"
run deploy/restore.sh --confirm "$today_dump"
check "restore --confirm by path exits 0" "0" "$last_rc"
check "restoring twice over a changed database gives the original rows" "$original" "$(rows)"

echo
echo "== retention =="
mkdir -p "$scratch/retention"
export BACKUP_DIR="$scratch/retention"
touch -d '40 days ago' "$BACKUP_DIR/rindetuplata-old-40d.dump"
touch -d '31 days ago' "$BACKUP_DIR/rindetuplata-old-31d.dump"
touch -d '29 days ago' "$BACKUP_DIR/rindetuplata-recent-29d.dump"
touch -d '40 days ago' "$BACKUP_DIR/unrelated-notes.txt"
export BACKUP_RETENTION_DAYS=30
run deploy/backup.sh
check "backup with old dumps around exits 0" "0" "$last_rc"
check "retention deletes the dumps older than 30 days and nothing else" \
  "rindetuplata-$(date +%F).dump rindetuplata-recent-29d.dump unrelated-notes.txt" \
  "$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"
export BACKUP_RETENTION_DAYS=1
run deploy/backup.sh
check "a shorter retention (1 day) keeps the fresh dump" "yes" \
  "$([ -s "$BACKUP_DIR/rindetuplata-$(date +%F).dump" ] && echo yes || echo no)"
check "a shorter retention (1 day) deletes the 29-day-old dump" "no" \
  "$([ -e "$BACKUP_DIR/rindetuplata-recent-29d.dump" ] && echo yes || echo no)"
check "a shorter retention (1 day) still leaves a file that is not a dump" "yes" \
  "$([ -e "$BACKUP_DIR/unrelated-notes.txt" ] && echo yes || echo no)"
export BACKUP_RETENTION_DAYS=forever
run deploy/backup.sh
check "a non-numeric retention exits 2" "2" "$last_rc"
check_contains "a non-numeric retention is named on stderr" "$scratch/err.txt" "BACKUP_RETENTION_DAYS"
unset BACKUP_RETENTION_DAYS

echo
echo "== a failing dump leaves no partial file and deletes nothing =="
mkdir -p "$scratch/failure"
export BACKUP_DIR="$scratch/failure"
touch -d '40 days ago' "$BACKUP_DIR/rindetuplata-old-40d.dump"
before="$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"
BACKUP_DB_NAME=no_such_database run deploy/backup.sh
check "pg_dump of a missing database exits non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check_contains "the failure is reported on stderr" "$scratch/err.txt" "backup failed"
check "pg_dump of a missing database leaves the directory as it was (no dump, no .tmp, no pruning)" \
  "$before" "$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"
BACKUP_SERVICE=no_such_service run deploy/backup.sh
check "an unknown Compose service exits non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check "an unknown Compose service leaves the directory as it was" \
  "$before" "$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"

# A dump that dies half-way: a fake `docker` writes some bytes and then fails, the way a dropped
# connection or a full disk would. Those bytes must never end up under the dump's name.
mkdir -p "$scratch/bin"
cat > "$scratch/bin/docker" <<'FAKE'
#!/usr/bin/env bash
printf 'PGDMP-half-a-dump'
echo "pg_dump: error: connection to server was lost" >&2
exit 1
FAKE
chmod +x "$scratch/bin/docker"
PATH="$scratch/bin:$PATH" run deploy/backup.sh
check "a dump that dies half-way exits non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check "a dump that dies half-way leaves the directory as it was (no partial file)" \
  "$before" "$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"
cat > "$scratch/bin/docker" <<'FAKE'
#!/usr/bin/env bash
exit 0
FAKE
PATH="$scratch/bin:$PATH" run deploy/backup.sh
check "a dump that comes out empty exits non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check "a dump that comes out empty leaves the directory as it was" \
  "$before" "$(ls -A "$BACKUP_DIR" | paste -sd' ' -)"

echo
echo "== the off-box copy hook =="
mkdir -p "$scratch/hook-backups" "$scratch/offbox"
export BACKUP_DIR="$scratch/hook-backups"
hook_dump="$BACKUP_DIR/rindetuplata-$(date +%F).dump"
BACKUP_COPY_COMMAND="cp \"\$1\" \"$scratch/offbox/\"" run deploy/backup.sh
check "backup with a copy hook exits 0" "0" "$last_rc"
check "the hook received the finished dump" "$(basename "$hook_dump")" "$(ls -A "$scratch/offbox")"
check "the hook's copy is the dump" "yes" "$(cmp -s "$hook_dump" "$scratch/offbox/$(basename "$hook_dump")" && echo yes || echo no)"
BACKUP_COPY_COMMAND="echo broken-remote >&2; exit 3" run deploy/backup.sh
check "a failing copy hook makes backup exit non-zero" "yes" "$([ "$last_rc" -ne 0 ] && echo yes || echo no)"
check_contains "a failing copy hook is reported on stderr" "$scratch/err.txt" "BACKUP_COPY_COMMAND"
check "a failing copy hook keeps the local dump" "yes" "$([ -s "$hook_dump" ] && echo yes || echo no)"
rm -r "$scratch/hook-backups"
mkdir -p "$scratch/hook-backups"
BACKUP_COPY_COMMAND="touch \"$scratch/hook-ran\"" BACKUP_DB_NAME=no_such_database run deploy/backup.sh
check "the hook does not run when the dump failed" "no" "$([ -e "$scratch/hook-ran" ] && echo yes || echo no)"

echo
echo "== the systemd units =="
service=deploy/systemd/rindetuplata-backup.service
timer=deploy/systemd/rindetuplata-backup.timer
if [ -f "$service" ] && [ -f "$timer" ]; then
  check_contains "the service is a oneshot" "$service" "Type=oneshot"
  check_contains "the service runs backup.sh" "$service" "deploy/backup.sh"
  check_contains "the timer fires daily at 03:30 local time" "$timer" "OnCalendar=*-*-* 03:30:00"
  check_contains "the timer catches up after downtime" "$timer" "Persistent=true"
  check_contains "the timer is wanted by timers.target" "$timer" "WantedBy=timers.target"
else
  echo "FAIL - $service or $timer is missing" >&2
  failed=$((failed + 1))
fi
if command -v systemd-analyze > /dev/null 2>&1; then
  # Verification resolves ExecStart= on disk, so check the timer alone (its unit reference is the service).
  if systemd-analyze calendar '*-*-* 03:30:00' > /dev/null 2>&1; then
    echo "ok   - systemd accepts the OnCalendar expression"
    passed=$((passed + 1))
  else
    echo "FAIL - systemd rejects the OnCalendar expression" >&2
    failed=$((failed + 1))
  fi
fi

echo
echo "passed: $passed, failed: $failed"
[ "$failed" -eq 0 ]
