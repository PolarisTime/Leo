#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LEO_DIR="$ROOT_DIR/leo"
ARIES_DIR="$ROOT_DIR/aries"

echo "[oobe] 加载后端环境变量"
# shellcheck disable=SC1090
source "$LEO_DIR/scripts/env/dev.sh"

echo "[oobe] 生成前端 .env.local"
bash "$ARIES_DIR/scripts/env/dev.sh"

echo "[oobe] 准备本地附件目录"
mkdir -p "${LEO_ATTACHMENT_LOCAL_PATH}"

if ! command -v java >/dev/null 2>&1; then
  echo "[oobe] 未找到 java，请先安装 Java 21。" >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "[oobe] 未找到 mvn，请先安装 Maven。" >&2
  exit 1
fi

if ! command -v pnpm >/dev/null 2>&1; then
  echo "[oobe] 未找到 pnpm，请先安装 pnpm。" >&2
  exit 1
fi

if [[ ! -d "$ARIES_DIR/node_modules" ]]; then
  echo "[oobe] 安装前端依赖"
  (cd "$ARIES_DIR" && pnpm install)
fi

if command -v pg_isready >/dev/null 2>&1; then
  echo "[oobe] 检查 PostgreSQL 可用性"
  pg_isready -h "${SPRING_DATASOURCE_HOST}" -p "${SPRING_DATASOURCE_PORT}" -d "${SPRING_DATASOURCE_DB}"
fi

if command -v psql >/dev/null 2>&1; then
  echo "[oobe] 检查数据库认证"
  PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" \
    psql -h "${SPRING_DATASOURCE_HOST}" -p "${SPRING_DATASOURCE_PORT}" \
    -U "${SPRING_DATASOURCE_USERNAME}" -d "${SPRING_DATASOURCE_DB}" \
    -Atqc 'select 1'
fi

if command -v redis-cli >/dev/null 2>&1; then
  echo "[oobe] 检查 Redis 认证"
  redis_args=(-h "${SPRING_DATA_REDIS_HOST}" -p "${SPRING_DATA_REDIS_PORT}")
  if [[ -n "${SPRING_DATA_REDIS_PASSWORD:-}" ]]; then
    redis_args+=(-a "${SPRING_DATA_REDIS_PASSWORD}")
  fi
  redis-cli "${redis_args[@]}" ping
fi

echo "[oobe] 当前关键配置"
printf '  JAVA_HOME=%s\n' "${JAVA_HOME}"
printf '  DB=%s@%s:%s/%s\n' "${SPRING_DATASOURCE_USERNAME}" "${SPRING_DATASOURCE_HOST}" "${SPRING_DATASOURCE_PORT}" "${SPRING_DATASOURCE_DB}"
printf '  REDIS=%s:%s/%s\n' "${SPRING_DATA_REDIS_HOST}" "${SPRING_DATA_REDIS_PORT}" "${SPRING_DATA_REDIS_DATABASE}"
printf '  UPLOAD_DIR=%s\n' "${LEO_ATTACHMENT_LOCAL_PATH}"
printf '  DOCS_PUBLIC=%s\n' "${LEO_DOCS_PUBLIC_ACCESS_ENABLED}"
printf '  HEALTH_PUBLIC=%s\n' "${LEO_HEALTH_PAGE_PUBLIC_ACCESS_ENABLED}"
printf '  FRONTEND_ENV=%s\n' "$ARIES_DIR/.env.local"

echo "[oobe] 完成。启动命令：bash leo/scripts/dev.sh start"
echo "[oobe] 停止命令：bash leo/scripts/dev.sh stop"
echo "[oobe] 首次启动后，请在浏览器打开前端地址并进入 /setup 页面完成管理员账号和公司主体初始化。"
