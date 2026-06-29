#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKSPACE_DIR="$(cd "$LEO_DIR/.." && pwd)"

STEELX_ROOT="/instance/steelx"
BACKEND_PORT="57217"
FRONTEND_PORT="443"
HTTP_REDIRECT_PORT="80"
SERVER_NAME="in1ove.com"
SSL_CERTIFICATE="/instance/ssl/fullchain.crt"
SSL_CERTIFICATE_KEY="/instance/ssl/privkey.key"
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

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/trigger-local-steelx-deploy.sh [选项]

选项:
  --confirm                    确认执行本地生产部署
  --root <dir>                 部署目录，默认 /instance/steelx
  --backend-port <port>        后端端口，默认 57217
  --frontend-port <port>       Nginx HTTPS 前端端口，默认 443
  --http-redirect-port <port>  HTTP 跳转端口，默认 80
  --server-name <name>         服务域名，默认 in1ove.com
  --ssl-certificate <path>     TLS 证书链，默认 /instance/ssl/fullchain.crt
  --ssl-certificate-key <path> TLS 私钥，默认 /instance/ssl/privkey.key
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
  --skip-tests                 跳过测试，仅构建部署
  -h, --help                   查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --confirm) CONFIRM=true; shift ;;
    --root) STEELX_ROOT="$2"; shift 2 ;;
    --backend-port) BACKEND_PORT="$2"; shift 2 ;;
    --frontend-port) FRONTEND_PORT="$2"; shift 2 ;;
    --http-redirect-port) HTTP_REDIRECT_PORT="$2"; shift 2 ;;
    --server-name) SERVER_NAME="$2"; shift 2 ;;
    --ssl-certificate) SSL_CERTIFICATE="$2"; shift 2 ;;
    --ssl-certificate-key) SSL_CERTIFICATE_KEY="$2"; shift 2 ;;
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
require_command ss
require_command nginx

run_as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
    return
  fi
  if ! command -v sudo >/dev/null 2>&1; then
    echo "当前用户不是 root，且缺少 sudo，无法安装 Nginx 配置。" >&2
    exit 1
  fi
  if [[ -n "${STEELX_SUDO_PASSWORD:-}" ]]; then
    printf '%s\n' "$STEELX_SUDO_PASSWORD" | sudo -S "$@"
    return
  fi
  sudo -n "$@"
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
if [[ "$FRONTEND_PORT" == "443" ]]; then
  FRONTEND_PUBLIC_ORIGIN="https://$SERVER_NAME"
else
  FRONTEND_PUBLIC_ORIGIN="https://$SERVER_NAME:$FRONTEND_PORT"
fi

if [[ "$CONFIRM" != "true" ]]; then
  cat >&2 <<EOF
拒绝执行本地生产部署。

该操作会：
- 创建或修改 PostgreSQL 数据库 $DB_NAME 与应用用户 $APP_DB_USER
- 写入部署目录 $STEELX_ROOT
- 安装 /etc/nginx/conf.d/steelx.conf 并 reload Nginx
- 使用 $SSL_CERTIFICATE 与 $SSL_CERTIFICATE_KEY 为 $FRONTEND_PUBLIC_ORIGIN 启用 HTTPS
- 将 $HTTP_REDIRECT_PORT 端口 HTTP 请求 301 跳转到 HTTPS
- 构建前后端 release 包
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

if [[ ! -f "$SSL_CERTIFICATE" ]]; then
  echo "TLS 证书链不存在: $SSL_CERTIFICATE" >&2
  exit 1
fi
if [[ ! -f "$SSL_CERTIFICATE_KEY" ]]; then
  echo "TLS 私钥不存在: $SSL_CERTIFICATE_KEY" >&2
  exit 1
fi

if ss -ltnpH "( sport = :$FRONTEND_PORT )" 2>/dev/null | grep -v 'nginx' | grep -q .; then
  echo "HTTPS 端口 $FRONTEND_PORT 已被非 Nginx 进程占用" >&2
  exit 1
fi
if ss -ltnpH "( sport = :$HTTP_REDIRECT_PORT )" 2>/dev/null | grep -v 'nginx' | grep -q .; then
  echo "HTTP 跳转端口 $HTTP_REDIRECT_PORT 已被非 Nginx 进程占用" >&2
  exit 1
fi

mkdir -p "$STEELX_ROOT/shared" "$STEELX_ROOT/logs" "$STEELX_ROOT/run"
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

cat > "$STEELX_ROOT/shared/steelx.env" <<EOF
SERVER_PORT=$BACKEND_PORT
SPRING_PROFILES_ACTIVE=prod
FRONTEND_PORT=$FRONTEND_PORT
LEO_CORS_ALLOWED_ORIGINS=$FRONTEND_PUBLIC_ORIGIN
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
LEO_VERSION=1.0.0
LEO_MACHINE_ID=2
LEO_ATTACHMENT_LOCAL_PATH=$STEELX_ROOT/shared/uploads
LEO_AUTH_REFRESH_COOKIE_SECURE=true
LEO_AUTH_REFRESH_COOKIE_SAME_SITE=Strict
LEO_HEALTH_PAGE_ENABLED=false
LEO_HEALTH_PAGE_PUBLIC_ACCESS_ENABLED=false
LEO_DOCS_PUBLIC_ACCESS_ENABLED=false
SPRING_AI_MCP_SERVER_ENABLED=false
EOF
chmod 600 "$STEELX_ROOT/shared/steelx.env"

build_args=(
  --output-dir "$LEO_DIR/target/steelx-release"
  --release-name "steelx-1.0.0"
  --api-base-url "/api"
)
if [[ "$SKIP_TESTS" == "true" ]]; then
  build_args+=(--skip-tests)
fi

bash "$SCRIPT_DIR/build-local-release.sh" "${build_args[@]}"

archive="$LEO_DIR/target/steelx-release/steelx-1.0.0.tar.gz"
sha_file="$archive.sha256"

cp "$SCRIPT_DIR/steelx-process.sh" "$STEELX_ROOT/shared/steelx-process.sh"
chmod +x "$STEELX_ROOT/shared/steelx-process.sh"

start_command="STEELX_ROOT=$STEELX_ROOT bash $STEELX_ROOT/shared/steelx-process.sh start"
stop_command="STEELX_ROOT=$STEELX_ROOT bash $STEELX_ROOT/shared/steelx-process.sh stop"

bash "$SCRIPT_DIR/install-production-release.sh" \
  --archive "$archive" \
  --sha256-file "$sha_file" \
  --release-root "$STEELX_ROOT" \
  --frontend-root "$STEELX_ROOT/frontend" \
  --backend-service "steelx-local" \
  --healthcheck-url "http://127.0.0.1:$BACKEND_PORT/api/health" \
  --keep-releases 5 \
  --start-command "$start_command" \
  --stop-command "$stop_command"

nginx_conf="$STEELX_ROOT/shared/nginx-steelx.conf"
cat > "$nginx_conf" <<EOF
server {
    listen $HTTP_REDIRECT_PORT;
    server_name $SERVER_NAME;

    return 301 https://$SERVER_NAME\$request_uri;
}

server {
    listen $FRONTEND_PORT ssl;
    http2 on;
    server_name $SERVER_NAME;

    ssl_certificate $SSL_CERTIFICATE;
    ssl_certificate_key $SSL_CERTIFICATE_KEY;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:STEELXSSL:10m;
    ssl_session_timeout 10m;

    root $STEELX_ROOT/frontend/current;
    index index.html;

    client_max_body_size 25m;

    location /assets/ {
        try_files \$uri =404;
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:$BACKEND_PORT/api/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Port \$server_port;
        proxy_read_timeout 120s;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

if [[ -d "/etc/nginx/conf.d" ]]; then
  install_path="/etc/nginx/conf.d/steelx.conf"
  run_as_root install -m 0644 "$nginx_conf" "$install_path"
  run_as_root nginx -t
  run_as_root nginx -s reload
else
  echo "未找到 /etc/nginx/conf.d，请手动安装 Nginx 配置: $nginx_conf" >&2
fi

echo "steelx 已部署:"
echo "  前端目录: $STEELX_ROOT/frontend/current"
echo "  前端地址: $FRONTEND_PUBLIC_ORIGIN"
echo "  后端地址: http://127.0.0.1:$BACKEND_PORT/api"
