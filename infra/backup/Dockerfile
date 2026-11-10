# syntax=docker/dockerfile:1
# Nightly database backups (M1.10): pg_dump of every service database, encrypted copy off the host with rclone.
#   docker build -t gridreserve/backup infra/backup/
# pg_dump must match the server's major version (PostgreSQL 16). rclone is one static binary from its
# official image; its "crypt" remote encrypts before anything leaves the host.
FROM rclone/rclone:1.75.2 AS rclone

FROM postgres:16.15-alpine
COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/rclone
COPY --chmod=755 backup.sh restore.sh scheduler.sh healthcheck.sh /usr/local/bin/
RUN mkdir -p /backups && chown postgres:postgres /backups
USER postgres
# Remotes come from RCLONE_CONFIG_* variables; this empty, throwaway file is only where rclone may write a
# refreshed Google token for the current run (the long-lived refresh token stays in .env).
ENV RCLONE_CONFIG=/tmp/rclone.conf
RUN touch /tmp/rclone.conf
WORKDIR /backups
# See healthcheck.sh. The start period covers the catch-up backup the scheduler runs when it starts.
HEALTHCHECK --interval=30s --timeout=5s --start-period=3m CMD healthcheck.sh
ENTRYPOINT []
CMD ["scheduler.sh"]
