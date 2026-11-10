#!/usr/bin/env bash
# Restore drill (M1.10): proves that a backup can be restored, without touching the live databases.
# Run it in the folder of a running stack (on the host: /opt/gridreserve; on a laptop: infra/):
#
#   restore-drill.sh            # the newest backup, fetched from the off-site copy and decrypted
#   restore-drill.sh 20261110T023000Z
#
# Starts a throwaway PostgreSQL next to the stack, creates the same roles and empty databases there,
# restores the backup into it with the backup image's restore.sh, compares row counts with the live
# databases, and removes the throwaway database again. Counts can differ by what changed since the backup.
set -euo pipefail
drill=gridreserve-restore-drill
network="${COMPOSE_PROJECT_NAME:-gridreserve}_default"
stamp="${1:-}"
init="$(pwd)/postgres/init-databases.sh"
[ -f "$init" ] || { echo "run this in the stack's folder (postgres/init-databases.sh not found)" >&2; exit 1; }
trap 'docker rm -f "$drill" >/dev/null 2>&1 || true' EXIT

if [ -z "$stamp" ]; then
  stamp="$(docker compose run --rm -T backup sh -c 'rclone lsf "$BACKUP_REMOTE" --dirs-only' </dev/null | tr -d / | sort | tail -1)"
  [ -n "$stamp" ] || { echo "no off-site backup found" >&2; exit 1; }
fi
echo "Drill with backup $stamp"

throwaway="$(openssl rand -hex 16)"
docker run -d --name "$drill" --network "$network" -e POSTGRES_PASSWORD="$throwaway" \
  -e IDENTITY_DB_USER=identity -e IDENTITY_DB_PASSWORD="$throwaway" \
  -e BOOKING_DB_USER=booking -e BOOKING_DB_PASSWORD="$throwaway" \
  -v "$init:/docker-entrypoint-initdb.d/10-init-databases.sh:ro" postgres:16.15 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$drill" psql -U postgres -d booking_db -tAc 'select 1' >/dev/null 2>&1 && break
  sleep 1
done

docker compose run --rm -T -e PGHOST="$drill" -e PGUSER=postgres -e PGPASSWORD="$throwaway" -e RESTORE_FROM_REMOTE=1 \
  backup restore.sh "$stamp" </dev/null

count() { docker exec "$1" psql -U postgres -d "$2" -tAc "$3"; }
live="$(docker compose ps -q postgres)"
printf '%-34s %8s %8s\n' table live restored
for row in "identity_db app_user" "identity_db flyway_schema_history" "booking_db booking" "booking_db bookable" "booking_db flyway_schema_history"; do
  set -- $row
  printf '%-34s %8s %8s\n' "$1.$2" "$(count "$live" "$1" "select count(*) from $2")" "$(count "$drill" "$1" "select count(*) from $2")"
done
echo "Drill done; the throwaway database is removed"
