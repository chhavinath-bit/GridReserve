#!/bin/sh
# Healthy unless the last backup attempt failed, or the last good backup is older than 26 hours.
# `docker compose ps` then shows a missed or broken night, and a release (up --wait) refuses to call it fine.
if [ -e /backups/last-failure ]; then
  [ -e /backups/last-success ] && [ /backups/last-success -nt /backups/last-failure ] || exit 1
fi
if [ -e /backups/last-success ]; then
  [ -n "$(find /backups/last-success -mmin -1560)" ] || exit 1
fi
exit 0
