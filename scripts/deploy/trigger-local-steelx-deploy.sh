#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

STEELX_ROOT="/instance/steelx"
BACKEND_PORT="57217"
CORS_ALLOWED_ORIGINS="https://in1ove.com"
DB_NAME="Master_Prod"
APP_DB_USER="steelx_app"
DB_HOST=""
DB_PORT=""
DB_ADMIN_HOST=""
DB_ADMIN_PORT=""
DB_ADMIN_USER="postgres"
DB_ADMIN_PASSWORD=""
REDIS_HOST=""
REDIS_PORT=""
REDIS_DATABASE=""
REDIS_PASSWORD=""
CONFIRM=false
SKIP_TESTS=false
FLYWAY_TARGET="${SPRING_FLYWAY_TARGET:-}"

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/trigger-local-steelx-deploy.sh [选项]

选项:
  --confirm                    确认执行本地生产部署
  --root <dir>                 部署目录，默认 /instance/steelx
  --backend-port <port>        后端端口，默认 57217
  --cors-allowed-origins <url> CORS 允许来源，默认 https://in1ove.com
  --db-name <name>             数据库名，默认 Master_Prod
  --app-db-user <name>         应用数据库用户，默认 steelx_app
  --db-host <host>             PostgreSQL 主机，默认沿用开发环境
  --db-port <port>             PostgreSQL 端口，默认沿用开发环境
  --db-admin-user <name>       数据库管理员用户，默认 postgres
  --db-admin-password <value>  数据库管理员密码；也可用 PGPASSWORD 环境变量
  --redis-host <host>          Redis 主机，默认沿用开发环境
  --redis-port <port>          Redis 端口，默认沿用开发环境
  --redis-database <db>        Redis 逻辑库，默认沿用开发环境
  --redis-password <value>     Redis 密码，默认沿用开发环境
  --flyway-target <version>    生产允许迁移到的最高 Flyway 版本（必填）
  --skip-tests                 跳过测试，仅构建部署
  -h, --help                   查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --confirm) CONFIRM=true; shift ;;
    --root) STEELX_ROOT="$2"; shift 2 ;;
    --backend-port) BACKEND_PORT="$2"; shift 2 ;;
    --cors-allowed-origins) CORS_ALLOWED_ORIGINS="$2"; shift 2 ;;
    --db-name) DB_NAME="$2"; shift 2 ;;
    --app-db-user) APP_DB_USER="$2"; shift 2 ;;
    --db-host) DB_HOST="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --db-admin-user) DB_ADMIN_USER="$2"; shift 2 ;;
    --db-admin-password) DB_ADMIN_PASSWORD="$2"; shift 2 ;;
    --redis-host) REDIS_HOST="$2"; shift 2 ;;
    --redis-port) REDIS_PORT="$2"; shift 2 ;;
    --redis-database) REDIS_DATABASE="$2"; shift 2 ;;
    --redis-password) REDIS_PASSWORD="$2"; shift 2 ;;
    --flyway-target) FLYWAY_TARGET="$2"; shift 2 ;;
    --skip-tests) SKIP_TESTS=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少命令: $command_name" >&2
    exit 1
  fi
}

require_command openssl
require_command psql
require_command redis-cli
require_command mvn
require_command tr

read_env_value() {
  local env_file="$1"
  local key="$2"
  local line
  local value=""

  [[ -f "$env_file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" == "$key="* ]]; then
      value="${line#*=}"
    fi
  done < "$env_file"

  [[ -n "$value" ]] || return 1
  printf '%s' "$value"
}

is_valid_setup_bootstrap_token() {
  local token="$1"
  [[ "$token" =~ ^[A-Za-z0-9_-]{43}=?$ ]]
}

resolve_setup_bootstrap_token() {
  local env_file="$1"
  local token

  if token="$(read_env_value "$env_file" "LEO_SETUP_BOOTSTRAP_TOKEN")"; then
    if ! is_valid_setup_bootstrap_token "$token"; then
      echo "既有 LEO_SETUP_BOOTSTRAP_TOKEN 格式无效，拒绝覆盖。" >&2
      return 1
    fi
  else
    token="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')"
    if ! is_valid_setup_bootstrap_token "$token"; then
      echo "无法生成有效的 LEO_SETUP_BOOTSTRAP_TOKEN。" >&2
      return 1
    fi
  fi

  printf '%s' "$token"
}

read_maven_version() {
  local version
  version="$(cd "$LEO_DIR" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
  if [[ -z "$version" ]]; then
    echo "无法读取 Maven 项目版本" >&2
    exit 1
  fi
  printf '%s\n' "$version"
}

# shellcheck disable=SC1091
source "$LEO_DIR/scripts/env/dev.sh"

DB_HOST="${DB_HOST:-${SPRING_DATASOURCE_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${SPRING_DATASOURCE_PORT:-5432}}"
DB_ADMIN_HOST="${DB_ADMIN_HOST:-${LEO_DB_ADMIN_HOST:-$DB_HOST}}"
DB_ADMIN_PORT="${DB_ADMIN_PORT:-${LEO_DB_ADMIN_PORT:-$DB_PORT}}"
REDIS_HOST="${REDIS_HOST:-${SPRING_DATA_REDIS_HOST:-127.0.0.1}}"
REDIS_PORT="${REDIS_PORT:-${SPRING_DATA_REDIS_PORT:-16379}}"
REDIS_DATABASE="${REDIS_DATABASE:-${SPRING_DATA_REDIS_DATABASE:-3}}"
REDIS_PASSWORD="${REDIS_PASSWORD:-${SPRING_DATA_REDIS_PASSWORD:-}}"
if [[ -z "$FLYWAY_TARGET" ]]; then
  echo "缺少 --flyway-target，拒绝生产部署。" >&2
  exit 1
fi
if [[ ! "$FLYWAY_TARGET" =~ ^[1-9][0-9]*$ ]]; then
  echo "FLYWAY_TARGET must be a positive integer: $FLYWAY_TARGET" >&2
  exit 1
fi
LEO_VERSION="$(read_maven_version)"

if [[ "$CONFIRM" != "true" ]]; then
  cat >&2 <<EOF
拒绝执行本地生产部署。

该操作会：
- 创建或修改 PostgreSQL 数据库 $DB_NAME 与应用用户 $APP_DB_USER
- 写入部署目录 $STEELX_ROOT
- 构建后端 release 包
- 停止并启动 steelx 后端进程

确认执行请追加 --confirm。
EOF
  exit 1
fi

DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-${PGPASSWORD:-${LEO_DB_ADMIN_PASSWORD:-}}}"
if [[ -z "$DB_ADMIN_PASSWORD" ]]; then
  echo "缺少数据库管理员密码，请传 --db-admin-password 或设置 PGPASSWORD。" >&2
  exit 1
fi

mkdir -p "$STEELX_ROOT/shared" "$STEELX_ROOT/logs" "$STEELX_ROOT/run" "$STEELX_ROOT/backend"
chmod 700 "$STEELX_ROOT/shared"

APP_DB_PASSWORD_FILE="$STEELX_ROOT/shared/.db-password"
if [[ ! -f "$APP_DB_PASSWORD_FILE" ]]; then
  openssl rand -base64 32 > "$APP_DB_PASSWORD_FILE"
  chmod 600 "$APP_DB_PASSWORD_FILE"
fi
APP_DB_PASSWORD="$(cat "$APP_DB_PASSWORD_FILE")"

JWT_SECRET_FILE="$STEELX_ROOT/shared/.jwt-secret"
if [[ ! -f "$JWT_SECRET_FILE" ]]; then
  openssl rand -base64 48 > "$JWT_SECRET_FILE"
  chmod 600 "$JWT_SECRET_FILE"
fi
JWT_SECRET="$(cat "$JWT_SECRET_FILE")"

TOTP_ENCRYPTION_KEY_FILE="$STEELX_ROOT/shared/.totp-encryption-key"
if [[ ! -f "$TOTP_ENCRYPTION_KEY_FILE" ]]; then
  openssl rand -base64 32 > "$TOTP_ENCRYPTION_KEY_FILE"
  chmod 600 "$TOTP_ENCRYPTION_KEY_FILE"
fi
TOTP_ENCRYPTION_KEY="$(cat "$TOTP_ENCRYPTION_KEY_FILE")"

STEELX_ENV_FILE="$STEELX_ROOT/shared/steelx.env"
SETUP_BOOTSTRAP_TOKEN="$(resolve_setup_bootstrap_token "$STEELX_ENV_FILE")"

echo "[db] 创建数据库和专用用户"
PGPASSWORD="$DB_ADMIN_PASSWORD" psql -h "$DB_ADMIN_HOST" -p "$DB_ADMIN_PORT" -U "$DB_ADMIN_USER" -d "postgres" -v ON_ERROR_STOP=1 \
  -v db_name="$DB_NAME" \
  -v app_user="$APP_DB_USER" \
  -v app_password="$APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'app_user', :'app_password')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')\gexec

ALTER DATABASE :"db_name" OWNER TO :"app_user";
GRANT ALL PRIVILEGES ON DATABASE :"db_name" TO :"app_user";
SQL

PGPASSWORD="$DB_ADMIN_PASSWORD" psql -h "$DB_ADMIN_HOST" -p "$DB_ADMIN_PORT" -U "$DB_ADMIN_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
  -v app_user="$APP_DB_USER" <<'SQL'
ALTER SCHEMA public OWNER TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
SQL

cat > "$STEELX_ENV_FILE" <<EOF
SERVER_PORT=$BACKEND_PORT
SPRING_PROFILES_ACTIVE=prod
SPRING_FLYWAY_TARGET=$FLYWAY_TARGET
LEO_CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
SPRING_DATASOURCE_HOST=$DB_HOST
SPRING_DATASOURCE_PORT=$DB_PORT
SPRING_DATASOURCE_DB=$DB_NAME
SPRING_DATASOURCE_USERNAME=$APP_DB_USER
SPRING_DATASOURCE_PASSWORD=$APP_DB_PASSWORD
SPRING_DATA_REDIS_HOST=$REDIS_HOST
SPRING_DATA_REDIS_PORT=$REDIS_PORT
SPRING_DATA_REDIS_DATABASE=$REDIS_DATABASE
SPRING_DATA_REDIS_PASSWORD=$REDIS_PASSWORD
LEO_JWT_SECRET=$JWT_SECRET
TOTP_ENCRYPTION_KEY=$TOTP_ENCRYPTION_KEY
LEO_SETUP_BOOTSTRAP_TOKEN=$SETUP_BOOTSTRAP_TOKEN
LEO_MACHINE_ID=2
LEO_ATTACHMENT_LOCAL_PATH=$STEELX_ROOT/shared/uploads
LEO_AUTH_REFRESH_COOKIE_SECURE=true
LEO_AUTH_REFRESH_COOKIE_SAME_SITE=Strict
LEO_HEALTH_PAGE_ENABLED=false
LEO_HEALTH_PAGE_PUBLIC_ACCESS_ENABLED=false
LEO_DOCS_PUBLIC_ACCESS_ENABLED=false
SPRING_AI_MCP_SERVER_ENABLED=false
EOF
chmod 600 "$STEELX_ENV_FILE"

build_args=(
  --output-dir "$LEO_DIR/target/steelx-release"
  --release-name "steelx-$LEO_VERSION"
)
if [[ "$SKIP_TESTS" == "true" ]]; then
  build_args+=(--skip-tests)
fi

bash "$SCRIPT_DIR/build-local-release.sh" "${build_args[@]}"

archive="$LEO_DIR/target/steelx-release/steelx-$LEO_VERSION.tar.gz"
sha_file="$archive.sha256"

cp "$SCRIPT_DIR/steelx-process.sh" "$STEELX_ROOT/shared/steelx-process.sh"
chmod +x "$STEELX_ROOT/shared/steelx-process.sh"

start_command="STEELX_ROOT=$STEELX_ROOT bash $STEELX_ROOT/shared/steelx-process.sh start"
stop_command="STEELX_ROOT=$STEELX_ROOT bash $STEELX_ROOT/shared/steelx-process.sh stop"

bash "$SCRIPT_DIR/install-production-release.sh" \
  --archive "$archive" \
  --sha256-file "$sha_file" \
  --release-root "$STEELX_ROOT/backend" \
  --shared-dir "$STEELX_ROOT/shared" \
  --backend-service "steelx-local" \
  --healthcheck-url "http://127.0.0.1:$BACKEND_PORT/api/health" \
  --keep-releases 5 \
  --start-command "$start_command" \
  --stop-command "$stop_command"

echo "steelx 已部署:"
echo "  后端地址: http://127.0.0.1:$BACKEND_PORT/api"
