# syntax=docker/dockerfile:1
# The frontend image (M1.10): the static React build inside Caddy, which also proxies /api to the gateway.
#   docker build -t gridreserve/web frontend/
# The build stage runs on the builder's own CPU ($BUILDPLATFORM): the output is plain files, the same everywhere.

# ---- build stage: exact locked packages, then the production build ----
FROM --platform=$BUILDPLATFORM node:24.22.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- runtime stage: Caddy serves the files, non-root ----
FROM caddy:2.11.7-alpine
# /data holds the TLS certificates and keys (a volume in prod); /config is Caddy's autosave.
RUN addgroup -S web && adduser -S -G web -H web && chown -R web:web /data /config
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
# Ports 80/443 work without root: Docker lets containers bind low ports (ip_unprivileged_port_start=0).
USER web
