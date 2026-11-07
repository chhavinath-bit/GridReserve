#!/usr/bin/env bash
# Applies configuration changes without restarts (PLAT-05, US-13): finds every running instance in Eureka
# and calls POST /actuator/refresh on each, with an admin token.
#
#   GRIDRESERVE_ADMIN_EMAIL=… GRIDRESERVE_ADMIN_PASSWORD=… scripts/refresh-config.sh [service-id]
#
# service-id: e.g. booking-service; default all registered services. Commit (or save) the change in the
# config repo first: in dev the Config Server reads the local clone, in prod the Git repository.
#
# Instances are reached at their Eureka address, which only works inside the service network. In dev
# (REFRESH_VIA=compose, the default) the calls are made from the discovery-server container; on a host
# inside the network use REFRESH_VIA=direct. The token is never printed.
set -euo pipefail

service="${1:-}"
gateway="${GATEWAY_URL:-http://localhost:8080}"
eureka="${EUREKA_URL:-http://localhost:8761}"
via="${REFRESH_VIA:-compose}"
compose_dir="${COMPOSE_DIR:-$(cd "$(dirname "$0")/../infra" && pwd)}"
: "${GRIDRESERVE_ADMIN_EMAIL:?set GRIDRESERVE_ADMIN_EMAIL (an ADMIN account)}"
: "${GRIDRESERVE_ADMIN_PASSWORD:?set GRIDRESERVE_ADMIN_PASSWORD}"

login_body=$(python3 -c 'import json,os;print(json.dumps({"email":os.environ["GRIDRESERVE_ADMIN_EMAIL"],"password":os.environ["GRIDRESERVE_ADMIN_PASSWORD"]}))')
token=$(curl -sf -H 'Content-Type: application/json' -d "$login_body" "$gateway/api/v1/auth/login" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])') \
  || { echo "Admin login through $gateway failed" >&2; exit 1; }

# "service-id host:port" for every UP instance (one service or all).
instances=$(curl -sf -H 'Accept: application/json' "$eureka/eureka/apps" | python3 -c '
import json, sys
wanted = sys.argv[1].upper()
apps = json.load(sys.stdin)["applications"]["application"]
for app in apps if isinstance(apps, list) else [apps]:
    if wanted and app["name"] != wanted:
        continue
    found = app["instance"] if isinstance(app["instance"], list) else [app["instance"]]
    for instance in found:
        if instance["status"] == "UP":
            print(app["name"].lower(), instance["ipAddr"] + ":" + str(instance["port"]["$"]))
' "$service")
[ -n "$instances" ] || { echo "No UP instances found in Eureka${service:+ for $service}" >&2; exit 1; }

failed=0
while read -r name address; do
  url="http://$address/actuator/refresh"
  if [ "$via" = compose ]; then
    # The token goes in through the environment, not the command line, so it doesn't show in process lists.
    # </dev/null: otherwise docker would read the rest of the instance list from stdin and end the loop.
    result=$(cd "$compose_dir" && TOKEN="$token" docker compose exec -T -e TOKEN discovery-server \
      sh -c "curl -s -o /tmp/refresh.out -w '%{http_code}' -X POST -H \"Authorization: Bearer \$TOKEN\" $url; echo; cat /tmp/refresh.out" </dev/null)
  else
    result=$(curl -s -o /tmp/refresh.out -w '%{http_code}' -X POST -H "Authorization: Bearer $token" "$url"; echo; cat /tmp/refresh.out)
  fi
  status=$(printf '%s\n' "$result" | head -1)
  changed=$(printf '%s\n' "$result" | tail -n +2)
  echo "$name $address → $status, changed keys: ${changed:-[]}"
  [ "$status" = 200 ] || failed=1
done <<< "$instances"
exit $failed
