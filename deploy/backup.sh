#!/usr/bin/env bash
#
# Daily database backup (T4c, S4): `pg_dump -Fc` through the Compose postgres service into a backup
# directory, pruned by age. Installed as a systemd timer (deploy/systemd/), see "Backups" in docs/deploy.md.
#
#   deploy/backup.sh
#
# A dump is written to a temporary file in the backup directory and renamed to
# `rindetuplata-YYYY-MM-DD.dump` only once pg_dump succeeded and produced something. On any failure the
# temporary file is removed, the script exits non-zero and nothing is pruned: a failed day can never
# delete the older dumps that are still good (finding R4 of `review-d018a62efca39d30`).
#
# Configuration, all optional (defaults are the VPS layout of docs/deploy.md):
#   BACKUP_DIR             where dumps go                                  /srv/rindetuplata/backups
#   BACKUP_RETENTION_DAYS  dumps older than this many days are deleted      30
#   BACKUP_COMPOSE_FILES   space-separated Compose files (-f, from the repo root)
#                                                                          "compose.yaml deploy/compose.vps.yaml"
#   BACKUP_ENV_FILE        --env-file for Compose, empty for none           /srv/rindetuplata/api.env
#   BACKUP_SERVICE         the Compose service running Postgres             postgres
#   BACKUP_DB_NAME / BACKUP_DB_USER                                         rindetuplata / rindetuplata
#   BACKUP_COPY_COMMAND    optional shell command run after a good dump to copy it off the box; the dump's
#                          path is "$1" (for example: rclone copyto "$1" remote:rindetuplata/"$(basename "$1")").
#                          If it fails the script exits non-zero, but the local dump is kept.
#
# Exit codes: 0 ok; 1 the dump or the copy hook failed; 2 bad configuration.
set -Eeuo pipefail

cd "$(dirname "$0")/.."

backup_dir="${BACKUP_DIR:-/srv/rindetuplata/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"
compose_files="${BACKUP_COMPOSE_FILES:-compose.yaml deploy/compose.vps.yaml}"
env_file="${BACKUP_ENV_FILE-/srv/rindetuplata/api.env}"
service="${BACKUP_SERVICE:-postgres}"
db_name="${BACKUP_DB_NAME:-rindetuplata}"
db_user="${BACKUP_DB_USER:-rindetuplata}"

if ! [[ "$retention_days" =~ ^[1-9][0-9]*$ ]]; then
  echo "backup: BACKUP_RETENTION_DAYS must be a positive whole number of days, got '$retention_days'" >&2
  exit 2
fi

compose=(docker compose)
read -ra files <<< "$compose_files"
for file in "${files[@]}"; do compose+=(-f "$file"); done
[ -z "$env_file" ] || compose+=(--env-file "$env_file")

umask 077 # a dump holds the users' emails and session data
mkdir -p "$backup_dir"
dump="$backup_dir/rindetuplata-$(date +%F).dump"
tmp="$(mktemp "$backup_dir/.rindetuplata-XXXXXX.tmp")"
trap 'rm -f "$tmp"' EXIT

if ! "${compose[@]}" exec -T "$service" pg_dump -U "$db_user" -Fc "$db_name" > "$tmp"; then
  echo "backup failed: pg_dump through the '$service' service exited non-zero; no dump written, nothing pruned" >&2
  exit 1
fi
if [ ! -s "$tmp" ]; then
  echo "backup failed: pg_dump produced an empty file; no dump written, nothing pruned" >&2
  exit 1
fi

mv "$tmp" "$dump"
echo "backup written: $dump"

# Only the daily dumps, only regular files, only strictly older than the retention window.
find "$backup_dir" -maxdepth 1 -type f -name 'rindetuplata-*.dump' -mmin "+$((retention_days * 1440))" -print -delete \
  | sed 's/^/backup pruned: /'

if [ -n "${BACKUP_COPY_COMMAND:-}" ]; then
  if ! bash -c "$BACKUP_COPY_COMMAND" backup-copy "$dump"; then
    echo "backup failed: BACKUP_COPY_COMMAND exited non-zero; the local dump $dump is kept" >&2
    exit 1
  fi
  echo "backup copied off the box by BACKUP_COPY_COMMAND"
fi
