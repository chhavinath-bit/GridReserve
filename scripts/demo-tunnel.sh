#!/usr/bin/env bash
# The laptop demo (M1.10, D-15 fallback): the whole app from local images, shared at a temporary public
# https:// address through a Cloudflare quick tunnel (free, no account). Live only while this script runs.
#
#   scripts/demo-tunnel.sh          # Ctrl-C stops the tunnel; the stack keeps running (docker compose down)
#
# Needs cloudflared (brew install cloudflared) and infra/.env. The tunnel gives HTTPS; behind it Caddy serves
# the app on http://localhost:8000 (compose profile "full"), and identity-service is restarted to accept the
# tunnel's address as the Origin for refresh and logout (FR-AUTH-12).
set -euo pipefail
command -v cloudflared >/dev/null || { echo "cloudflared is missing: brew install cloudflared" >&2; exit 1; }
infra="$(cd "$(dirname "$0")/../infra" && pwd)"
log="$(mktemp)"

cloudflared tunnel --no-autoupdate --url http://localhost:8000 > "$log" 2>&1 &
tunnel=$!
trap 'kill $tunnel 2>/dev/null || true; rm -f "$log"; echo; echo "Tunnel closed"' EXIT

url=""
for _ in $(seq 1 30); do
  url="$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$log" | head -1 || true)"
  [ -n "$url" ] && break
  kill -0 $tunnel 2>/dev/null || { cat "$log" >&2; exit 1; }
  sleep 1
done
[ -n "$url" ] || { echo "cloudflared printed no address" >&2; cat "$log" >&2; exit 1; }

echo "Starting the stack for $url (first build takes a few minutes)"
(cd "$infra" && FRONTEND_ORIGIN="$url" docker compose --profile full up -d --build --wait)
echo
echo "GridReserve is live at $url  (demo accounts: README)"
echo "Ctrl-C to close the tunnel."
wait $tunnel
