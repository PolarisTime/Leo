#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_SCRIPT="$SCRIPT_DIR/env-local.sh"

declare -A CALLER_ENV=()
for env_name in \
  LEO_DB_ADMIN_HOST \
  LEO_DB_ADMIN_PORT \
  LEO_DB_ADMIN_DATABASE \
  LEO_DB_ADMIN_USER \
  LEO_DB_ADMIN_PASSWORD \
  SPRING_DATASOURCE_HOST \
  SPRING_DATASOURCE_PORT \
  SPRING_DATASOURCE_DB \
  SPRING_DATASOURCE_USERNAME \
  SPRING_DATASOURCE_PASSWORD
do
  if [[ -v "$env_name" ]]; then
    CALLER_ENV["$env_name"]="${!env_name}"
  fi
done

if [[ -f "$ENV_SCRIPT" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_SCRIPT"
fi

for env_name in "${!CALLER_ENV[@]}"; do
  export "$env_name=${CALLER_ENV[$env_name]}"
done

DB_HOST="${LEO_DB_ADMIN_HOST:-${SPRING_DATASOURCE_HOST:-localhost}}"
DB_PORT="${LEO_DB_ADMIN_PORT:-${SPRING_DATASOURCE_PORT:-5432}}"
DB_ADMIN_DATABASE="${LEO_DB_ADMIN_DATABASE:-postgres}"
DB_ADMIN_USER="${LEO_DB_ADMIN_USER:-postgres}"
APP_DATABASE="${SPRING_DATASOURCE_DB:-leo}"
APP_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
APP_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"

export PGPASSWORD="${LEO_DB_ADMIN_PASSWORD:-${PGPASSWORD:-}}"

if ! command -v psql >/dev/null 2>&1; then
  echo "[leo-db] psql not found. Install PostgreSQL client first." >&2
  exit 1
fi

if [[ "$APP_DATABASE" == "postgres" || "$APP_DATABASE" == "template0" || "$APP_DATABASE" == "template1" ]]; then
  echo "[leo-db] refusing to initialize reserved database: $APP_DATABASE" >&2
  exit 1
fi

echo "[leo-db] initializing database '$APP_DATABASE' and user '$APP_USER' on $DB_HOST:$DB_PORT"

psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_ADMIN_USER" \
  -d "$DB_ADMIN_DATABASE" \
  -v ON_ERROR_STOP=1 \
  -v app_database="$APP_DATABASE" \
  -v app_user="$APP_USER" \
  -v app_password="$APP_PASSWORD" <<'SQL'
SELECT
    CASE
        WHEN :'app_password' = ''
            THEN format('CREATE ROLE %I LOGIN', :'app_user')
        ELSE format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
    END
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'app_user'
)
\gexec

SELECT
    CASE
        WHEN :'app_password' = ''
            THEN format('ALTER ROLE %I PASSWORD NULL', :'app_user')
        ELSE format('ALTER ROLE %I PASSWORD %L', :'app_user', :'app_password')
    END
WHERE EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'app_user'
)
\gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING %L', :'app_database', :'app_user', 'UTF8')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = :'app_database'
)
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'app_database', :'app_user')
\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'app_database', :'app_user')
\gexec

\connect :app_database

SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user')
\gexec

SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'app_user')
\gexec

SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I', :'app_user')
\gexec
SQL

cat <<EOF
[leo-db] database initialization complete.
[leo-db] Next:
[leo-db]   1. Start Leo so Flyway creates tables and system metadata:
[leo-db]      $ROOT_DIR/scripts/start-local.sh
[leo-db]   2. Open the Aries setup page or login page:
[leo-db]      http://localhost:3100/setup
[leo-db] OOBE creates the first admin account and company profile; this script intentionally does not seed them.
EOF
