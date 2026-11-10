#!/usr/bin/env bash
# Runs one release on the deploy host (M1.10), called over SSH by .github/workflows/deploy.yml:
#
#   /opt/gridreserve/release.sh <commit-sha>
#
# Pulls that commit's images from GitHub's registry, restarts what changed, and waits until every container is
# healthy. The SHA is recorded in .env (IMAGE_TAG), the one before it as PREVIOUS_IMAGE_TAG: a rollback is
# the same command with an older SHA (deploy.yml "Run workflow" with that tag). Data lives in volumes, so a
# release never touches it.
set -euo pipefail
tag="${1:?usage: release.sh <commit-sha>}"
[[ "$tag" =~ ^[0-9a-f]{40}$ ]] || { echo "not a full commit SHA: $tag" >&2; exit 1; }
cd "${GRIDRESERVE_HOME:-/opt/gridreserve}"
[ -f .env ] || { echo "no .env here: run scripts/prod/init-env.sh first" >&2; exit 1; }

current="$(grep -E '^IMAGE_TAG=' .env | cut -d= -f2- || true)"
if [ "$current" != "$tag" ]; then
  sed -i -e "s/^PREVIOUS_IMAGE_TAG=.*/PREVIOUS_IMAGE_TAG=$current/" -e "s/^IMAGE_TAG=.*/IMAGE_TAG=$tag/" .env
fi
echo "Releasing $tag (previous: ${current:-none})"

docker compose pull --quiet
# --wait: returns only when every container is healthy (or fails after the timeout, failing the deploy).
docker compose up -d --remove-orphans --wait --wait-timeout 420
docker compose ps --format 'table {{.Service}}\t{{.Status}}'
# Unused images older than a week go; recent ones stay for a fast rollback (older ones are pulled again).
docker image prune -af --filter "until=168h" > /dev/null
echo "Release $tag is up"
