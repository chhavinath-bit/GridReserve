#!/bin/sh
# One backup now: every database in BACKUP_DATABASES to /backups/<UTC time>/, then an encrypted copy to
# BACKUP_REMOTE (an rclone remote; empty = keep it on this host only). Old copies are pruned on both sides.
#   docker compose run --rm backup backup.sh
# Connection settings come from PGHOST / PGUSER / PGPASSWORD. Nothing secret is printed.
set -eu
: "${BACKUP_DATABASES:?list the databases to back up}"
keep_local="${BACKUP_KEEP_LOCAL_DAYS:-7}"
keep_remote="${BACKUP_KEEP_REMOTE_DAYS:-30}"
remote="${BACKUP_REMOTE-}"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
work="/backups/.in-progress-$stamp"
rm -rf /backups/.in-progress-*
mkdir -p "$work"

for db in $BACKUP_DATABASES; do
  # Custom format: compressed, and pg_restore can restore it into an empty database later.
  pg_dump --format=custom --file="$work/$db.dump" "$db"
  echo "Dumped $db ($(du -k "$work/$db.dump" | cut -f1) KB)"
done
(cd "$work" && sha256sum ./*.dump > SHA256SUMS)
# Only a complete backup gets its final name, so a half-written one is never mistaken for a good one.
mv "$work" "/backups/$stamp"

find /backups -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$keep_local" -exec rm -rf {} +

if [ -n "$remote" ]; then
  # The crypt remote encrypts file contents and names before upload; the provider only sees ciphertext.
  rclone copy "/backups/$stamp" "$remote$stamp" --quiet
  rclone delete "$remote" --min-age "${keep_remote}d" --quiet
  rclone rmdirs "$remote" --leave-root --quiet
  echo "Copied $stamp to $remote (kept there for $keep_remote days)"
else
  echo "BACKUP_REMOTE is empty: $stamp stays on this host only"
fi

touch /backups/last-success
echo "Backup $stamp done"
