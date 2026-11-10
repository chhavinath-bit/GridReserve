#!/bin/sh
# Restores one backup into the databases it came from, replacing their contents.
#   docker compose stop identity-service booking-service
#   docker compose run --rm backup restore.sh                 # list the backups here and off-site
#   docker compose run --rm backup restore.sh 20261110T023000Z
#   docker compose start identity-service booking-service
# A backup missing on this host (a new host after losing the old one) is fetched from BACKUP_REMOTE and
# decrypted first. The checksums are verified before anything is restored.
set -eu
remote="${BACKUP_REMOTE-}"
stamp="${1:-}"

if [ -z "$stamp" ]; then
  echo "On this host:"; ls -1 /backups | grep '^20' || echo "  (none)"
  if [ -n "$remote" ]; then echo "Off-site ($remote):"; rclone lsf "$remote" --dirs-only | tr -d / || true; fi
  exit 0
fi
case "$stamp" in
  20[0-9][0-9][01][0-9][0-3][0-9]T[0-9][0-9][0-9][0-9][0-9][0-9]Z) ;;
  *) echo "Not a backup name: $stamp" >&2; exit 1 ;;
esac

dir="/backups/$stamp"
if [ "${RESTORE_FROM_REMOTE:-}" = 1 ]; then
  # Prove the off-site copy, not the local one (scripts/prod/restore-drill.sh).
  dir="$(mktemp -d)/$stamp"
fi
if [ ! -d "$dir" ]; then
  [ -n "$remote" ] || { echo "No backup $stamp on this host and BACKUP_REMOTE is empty" >&2; exit 1; }
  echo "Fetching $stamp from $remote"
  rclone copy "$remote$stamp" "$dir" --quiet
fi
(cd "$dir" && sha256sum -c SHA256SUMS)

for dump in "$dir"/*.dump; do
  db=$(basename "$dump" .dump)
  # --clean --if-exists: drop what is there, then recreate it from the dump, in one transaction.
  pg_restore --clean --if-exists --single-transaction --exit-on-error --dbname="$db" "$dump"
  echo "Restored $db"
done
echo "Restore of $stamp done; start the services again"
