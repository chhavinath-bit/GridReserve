#!/bin/bash
# Runs once, when the PostgreSQL data volume is first created (docker-entrypoint-initdb.d).
# Creates one database and one owner per service (PLAT-08: database per service).
# Credentials come from infra/.env; psql variables (:"name" / :'value') quote them safely.

# ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" means use this superuser to create the database and owner.
set -euo pipefail

create_service_database() {
  local database="$1" user="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
       -v db="$database" -v usr="$user" -v pwd="$password" <<'EOSQL'
CREATE USER :"usr" WITH PASSWORD :'pwd';
CREATE DATABASE :"db" OWNER :"usr";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
  echo "Created database ${database} owned by ${user}"
}

create_service_database identity_db "$IDENTITY_DB_USER" "$IDENTITY_DB_PASSWORD"
create_service_database booking_db  "$BOOKING_DB_USER"  "$BOOKING_DB_PASSWORD"
