#!/usr/bin/env bash
#
# Restore one named dump into the live database (T4c, S4). Destructive: it replaces every table the dump
# contains, so it refuses to run without an explicit --confirm.
#
#   deploy/restore.sh --confirm rindetuplata-2026-11-01.dump     # a name inside BACKUP_DIR
#   deploy/restore.sh --confirm /srv/rindetuplata/backups/rindetuplata-2026-11-01.dump
#
# The restore is one transaction (`pg_restore --single-transaction --exit-on-error`): a dump that is
# unreadable or fails half-way leaves the database as it was. Stop the API first (docs/deploy.md), or its
# open connections block the DROPs. Same BACKUP_* variables as deploy/backup.sh.
#
# Exit codes: 0 ok; 1 the restore failed; 2 refused (no --confirm, no dump named, dump not found).
set -Eeuo pipefail

cd "$(dirname "$0")/.."

backup_dir="${BACKUP_DIR:-/srv/rindetuplata/backups}"
compose_files="${BACKUP_COMPOSE_FILES:-compose.yaml deploy/compose.vps.yaml}"
env_file="${BACKUP_ENV_FILE-/srv/rindetuplata/api.env}"
service="${BACKUP_SERVICE:-postgres}"
db_name="${BACKUP_DB_NAME:-rindetuplata}"
db_user="${BACKUP_DB_USER:-rindetuplata}"

usage() { echo "usage: deploy/restore.sh --confirm <dump name in \$BACKUP_DIR, or path>" >&2; }

confirmed=no
dump_arg=""
for arg in "$@"; do
  case "$arg" in
    --confirm) confirmed=yes ;;
    -*)
      echo "restore: unknown option '$arg'" >&2
      usage
      exit 2
      ;;
    *)
      if [ -n "$dump_arg" ]; then
        echo "restore: one dump at a time, got '$dump_arg' and '$arg'" >&2
        usage
        exit 2
      fi
      dump_arg="$arg"
      ;;
  esac
done

if [ -z "$dump_arg" ]; then
  echo "restore: no dump named" >&2
  usage
  exit 2
fi
if [ "$confirmed" != yes ]; then
  echo "restore: refusing to overwrite the live database with '$dump_arg' without --confirm; nothing was changed" >&2
  usage
  exit 2
fi

case "$dump_arg" in
  */*) dump="$dump_arg" ;;
  *) dump="$backup_dir/$dump_arg" ;;
esac
if [ ! -f "$dump" ] || [ ! -s "$dump" ]; then
  echo "restore: dump not found or empty: $dump; nothing was changed" >&2
  exit 2
fi

compose=(docker compose)
read -ra files <<< "$compose_files"
for file in "${files[@]}"; do compose+=(-f "$file"); done
[ -z "$env_file" ] || compose+=(--env-file "$env_file")

echo "restoring $dump into database '$db_name' through the '$service' service"
if ! "${compose[@]}" exec -T "$service" pg_restore -U "$db_user" -d "$db_name" \
  --clean --if-exists --single-transaction --exit-on-error < "$dump"; then
  echo "restore failed: pg_restore exited non-zero; the database was left as it was (single transaction)" >&2
  exit 1
fi
echo "restore done: $dump"
