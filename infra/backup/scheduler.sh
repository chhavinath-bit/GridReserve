#!/bin/sh
# Runs backup.sh once a day at BACKUP_SCHEDULE (HH:MM, UTC). A plain loop instead of cron: cron jobs don't
# see the container's environment (where the settings are) and cron needs root.
set -eu
at="${BACKUP_SCHEDULE:-02:30}"
case "$at" in
  [0-2][0-9]:[0-5][0-9]) ;;
  *) echo "BACKUP_SCHEDULE must be HH:MM (UTC), got '$at'" >&2; exit 1 ;;
esac

run() {
  if backup.sh; then rm -f /backups/last-failure
  else touch /backups/last-failure; echo "Backup failed; see the lines above" >&2
  fi
}

# Catch up first, like the hold-expiry job: a host that was down (or new) gets a backup now, not tomorrow.
if [ -z "$(find /backups/last-success -mmin -1440 2>/dev/null)" ]; then
  echo "No backup in the last 24 hours: backing up now"
  run
fi

while true; do
  now=$(date -u +%s)
  next=$(date -u -d "$(date -u +%Y-%m-%d) $at" +%s)
  [ "$next" -gt "$now" ] || next=$((next + 86400))
  echo "Next backup at $(date -u -d "@$next" '+%Y-%m-%d %H:%M') UTC"
  sleep $((next - now))
  # A failed night is logged and shows as unhealthy (healthcheck.sh); the loop keeps going.
  run
done
