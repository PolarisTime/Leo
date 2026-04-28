#!/usr/bin/env bash

# 本地开发环境变量脚本。
# 用法：
#   source leo/scripts/env-local.sh
# 或由 leo/scripts/start-local.sh 自动加载。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "请使用 'source leo/scripts/env-local.sh' 加载环境变量，不要直接执行。" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

load_dotenv_file() {
  local dotenv_file="$1"
  if [[ ! -f "$dotenv_file" ]]; then
    return
  fi

  set -a
  # shellcheck disable=SC1090
  source "$dotenv_file"
  set +a
}

# 优先加载仓库根目录的本地环境文件，便于本地 secrets 和容器配置复用。
load_dotenv_file "$REPO_ROOT/.env"
load_dotenv_file "$REPO_ROOT/.env.local"

# Java 运行时
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-21.0.10.0.7-1.el9.x86_64}"

# PostgreSQL 连接信息
export SPRING_DATASOURCE_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
export SPRING_DATASOURCE_PORT="${SPRING_DATASOURCE_PORT:-5432}"
export SPRING_DATASOURCE_DB="${SPRING_DATASOURCE_DB:-leo}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-leo}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"

# PostgreSQL 管理连接信息，仅 leo/scripts/init-database.sh 使用。
export LEO_DB_ADMIN_HOST="${LEO_DB_ADMIN_HOST:-$SPRING_DATASOURCE_HOST}"
export LEO_DB_ADMIN_PORT="${LEO_DB_ADMIN_PORT:-$SPRING_DATASOURCE_PORT}"
export LEO_DB_ADMIN_DATABASE="${LEO_DB_ADMIN_DATABASE:-postgres}"
export LEO_DB_ADMIN_USER="${LEO_DB_ADMIN_USER:-postgres}"
export LEO_DB_ADMIN_PASSWORD="${LEO_DB_ADMIN_PASSWORD:-}"

# Redis 连接信息
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-16379}"
export SPRING_DATA_REDIS_DATABASE="${SPRING_DATA_REDIS_DATABASE:-3}"
export SPRING_DATA_REDIS_PASSWORD="${SPRING_DATA_REDIS_PASSWORD:-}"

# 本地附件存储
export LEO_ATTACHMENT_STORAGE_TYPE="${LEO_ATTACHMENT_STORAGE_TYPE:-local}"
export LEO_ATTACHMENT_KEY_PREFIX="${LEO_ATTACHMENT_KEY_PREFIX:-attachments}"
export LEO_ATTACHMENT_LOCAL_PATH="${LEO_ATTACHMENT_LOCAL_PATH:-/tmp/leo/uploads}"

# 数据库备份命令
export LEO_DATABASE_BACKUP_PG_DUMP_COMMAND="${LEO_DATABASE_BACKUP_PG_DUMP_COMMAND:-/usr/pgsql-18/bin/pg_dump}"
export LEO_DATABASE_BACKUP_PSQL_COMMAND="${LEO_DATABASE_BACKUP_PSQL_COMMAND:-/usr/pgsql-18/bin/psql}"

# 旧版启动期灌库开关保留为兼容项；当前首次初始化统一走网页 OOBE (/setup)。
export LEO_COMPANY_BOOTSTRAP_ENABLED="${LEO_COMPANY_BOOTSTRAP_ENABLED:-false}"
export LEO_COMPANY_BOOTSTRAP_COMPANY_NAME="${LEO_COMPANY_BOOTSTRAP_COMPANY_NAME:-}"
export LEO_COMPANY_BOOTSTRAP_TAX_NO="${LEO_COMPANY_BOOTSTRAP_TAX_NO:-}"
export LEO_COMPANY_BOOTSTRAP_BANK_NAME="${LEO_COMPANY_BOOTSTRAP_BANK_NAME:-}"
export LEO_COMPANY_BOOTSTRAP_BANK_ACCOUNT="${LEO_COMPANY_BOOTSTRAP_BANK_ACCOUNT:-}"
export LEO_COMPANY_BOOTSTRAP_TAX_RATE="${LEO_COMPANY_BOOTSTRAP_TAX_RATE:-0.1300}"
export LEO_COMPANY_BOOTSTRAP_STATUS="${LEO_COMPANY_BOOTSTRAP_STATUS:-正常}"
export LEO_COMPANY_BOOTSTRAP_REMARK="${LEO_COMPANY_BOOTSTRAP_REMARK:-}"

# 认证与安全密钥
# - 首次启动、数据库尚未托管密钥时，需要在本地提供。
# - 如果系统已完成 JWT/2FA 密钥轮转并托管到数据库，可留空并直接使用数据库中的活动密钥。
export LEO_AUTH_DEFAULT_PASSWORD="${LEO_AUTH_DEFAULT_PASSWORD:-}"
export LEO_JWT_SECRET="${LEO_JWT_SECRET:-}"
export TOTP_ENCRYPTION_KEY="${TOTP_ENCRYPTION_KEY:-}"
export LEO_MACHINE_ID="${LEO_MACHINE_ID:-0}"

active_profiles=",${SPRING_PROFILES_ACTIVE:-},"
if [[ "$active_profiles" == *",prod,"* ]]; then
  # 显式跑 prod profile 时，不注入本地开发默认值，交给 application-prod.yml 决定。
  :
else
  # 本地开发默认值
  export LEO_AUTH_REFRESH_COOKIE_SECURE="${LEO_AUTH_REFRESH_COOKIE_SECURE:-false}"
  export LEO_AUTH_REFRESH_COOKIE_SAME_SITE="${LEO_AUTH_REFRESH_COOKIE_SAME_SITE:-Lax}"
  export LEO_CORS_ALLOWED_ORIGINS="${LEO_CORS_ALLOWED_ORIGINS:-http://localhost:3100,http://127.0.0.1:3100}"
  export LEO_CORS_ALLOW_CREDENTIALS="${LEO_CORS_ALLOW_CREDENTIALS:-true}"
  export LEO_CORS_MAX_AGE_SECONDS="${LEO_CORS_MAX_AGE_SECONDS:-3600}"
  export LEO_SECURITY_HSTS_ENABLED="${LEO_SECURITY_HSTS_ENABLED:-false}"
  export SPRINGDOC_API_DOCS_ENABLED="${SPRINGDOC_API_DOCS_ENABLED:-true}"
  export SPRINGDOC_SWAGGER_UI_ENABLED="${SPRINGDOC_SWAGGER_UI_ENABLED:-true}"
  export LEO_DOCS_PUBLIC_ACCESS_ENABLED="${LEO_DOCS_PUBLIC_ACCESS_ENABLED:-false}"
  export LEO_HEALTH_PAGE_ENABLED="${LEO_HEALTH_PAGE_ENABLED:-true}"
  export LEO_HEALTH_PAGE_PUBLIC_ACCESS_ENABLED="${LEO_HEALTH_PAGE_PUBLIC_ACCESS_ENABLED:-true}"
fi
