#!/usr/bin/env bash
# Prepares a throwaway environment for the Compose system tests on a CI runner (M1.8, CONC-10).
#
# Expects the app repo and the config repo checked out side by side, as on a laptop:
#   <workspace>/GridReserve   (this repo)
#   <workspace>/gridreserve-config
#
# 1. Writes infra/.env from .env.example with random database passwords and a random service-client
#    secret for booking-service (with its SHA-256 for identity-service).
# 2. Generates a new RSA key pair: the private key goes into infra/.env (JWT_PRIVATE_KEY), the public key
#    replaces the dev public key in the runner's copy of gridreserve-config/application.yml.
# Nothing is committed or pushed; the runner is discarded after the job. Values are never printed.
set -euo pipefail

app_dir="$(cd "$(dirname "$0")/../.." && pwd)"
config_dir="$(cd "$app_dir/../gridreserve-config" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/private.pem" 2>/dev/null
openssl pkey -in "$work/private.pem" -pubout -out "$work/public.pem"
private_one_line="$(awk 'NF {printf "%s\\n", $0}' "$work/private.pem")"

python3 - "$app_dir/infra/.env.example" "$app_dir/infra/.env" "$private_one_line" <<'PY'
import hashlib, secrets, sys
example, target, private_key = sys.argv[1], sys.argv[2], sys.argv[3]
client_secret = secrets.token_hex(32)
lines = []
for line in open(example):
    if line.startswith("JWT_PRIVATE_KEY="):
        line = "JWT_PRIVATE_KEY=" + private_key + "\n"
    elif line.startswith("BOOKING_SERVICE_CLIENT_SECRET="):
        line = "BOOKING_SERVICE_CLIENT_SECRET=" + client_secret + "\n"
    elif line.startswith("BOOKING_SERVICE_CLIENT_SECRET_SHA256="):
        line = "BOOKING_SERVICE_CLIENT_SECRET_SHA256=" + hashlib.sha256(client_secret.encode()).hexdigest() + "\n"
    elif "=change-me" in line:
        line = line.replace("change-me", secrets.token_hex(16))
    lines.append(line)
open(target, "w").writelines(lines)
PY

python3 - "$config_dir/application.yml" "$work/public.pem" <<'PY'
import re, sys
path, public_pem = sys.argv[1], open(sys.argv[2]).read().strip().splitlines()
text = open(path).read()
indent = "        "
block = "\n".join(indent + line for line in public_pem)
new, count = re.subn(r"(      dev-2026-11: \|\n)(        -----BEGIN PUBLIC KEY-----\n(?:        .*\n)*?        -----END PUBLIC KEY-----)",
                     lambda m: m.group(1) + block, text)
if count != 1:
    sys.exit("could not find the dev public key block in application.yml")
open(path, "w").write(new)
PY

echo "System-test environment ready: infra/.env written, config copy uses a fresh key pair."
