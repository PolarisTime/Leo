#!/usr/bin/env bash
# PostgreSQL tuning readiness checker for Leo ERP.
# Default mode is read-only. It prints the current state and the exact changes
# an administrator should apply when production tuning is approved.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
RUNTIME_ENV="dev"
ENV_FILE=""
APPLY_EXTENSION=false

usage() {
  cat <<'EOF'
用法:
  scripts/postgres-tuning-check.sh [--env dev|prod] [--env-file PATH] [--apply-extension]

选项:
  --env dev|prod       载入 scripts/env/<env>.sh，默认 dev
  --env-file PATH      额外载入指定环境变量文件，例如 /instance/steelx/shared/steelx.env
  --apply-extension    执行 CREATE EXTENSION IF NOT EXISTS pg_stat_statements

默认仅读取数据库状态并输出建议，不修改数据库或系统配置。
--apply-extension 只安装 pg_stat_statements；postgresql.conf 参数仍只输出建议，不自动修改。
EOF
}

log() {
  printf '[%s] %s\n' "$(date +'%Y-%m-%d %H:%M:%S')" "$*" >&2
}

die() {
  log "ERROR: $*"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      RUNTIME_ENV="${2:-}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --apply-extension)
      APPLY_EXTENSION=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "未知参数: $1"
      ;;
  esac
done

case "$RUNTIME_ENV" in
  dev|prod) ;;
  *) die "--env 必须是 dev 或 prod" ;;
esac

# shellcheck disable=SC1090
source "$SCRIPT_DIR/env/$RUNTIME_ENV.sh"

if [[ -n "$ENV_FILE" ]]; then
  [[ -f "$ENV_FILE" ]] || die "环境变量文件不存在: $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

PSQL_BIN="${LEO_DATABASE_BACKUP_PSQL_COMMAND:-psql}"
command -v "$PSQL_BIN" >/dev/null 2>&1 || die "缺少 psql: $PSQL_BIN"

PG_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
PG_PORT="${SPRING_DATASOURCE_PORT:-5432}"
PG_DB="${SPRING_DATASOURCE_DB:-leo}"
PG_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
PG_PASS="${SPRING_DATASOURCE_PASSWORD:-}"

PSQL=("$PSQL_BIN" -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -X -v ON_ERROR_STOP=1)

run_sql() {
  local sql="$1"
  PGPASSWORD="$PG_PASS" "${PSQL[@]}" -c "$sql"
}

run_sql_tuples() {
  local sql="$1"
  PGPASSWORD="$PG_PASS" "${PSQL[@]}" -A -F $'\t' -t -c "$sql"
}

setting_value() {
  local name="$1"
  run_sql_tuples "SELECT setting FROM pg_settings WHERE name = '$name';" 2>/dev/null || true
}

setting_line() {
  local name="$1"
  run_sql_tuples "SELECT name || '=' || setting || COALESCE(unit, '') || ' source=' || source FROM pg_settings WHERE name = '$name';" 2>/dev/null || true
}

echo "=== PostgreSQL 调优检查 ($RUNTIME_ENV) ==="
echo "database=$PG_DB"
echo "user=$PG_USER"
echo "host=$PG_HOST"
echo

echo "=== 连接与版本 ==="
run_sql "SELECT current_database() AS database_name, current_user AS user_name, version() AS postgres_version;"

echo
echo "=== 关键参数 ==="
for name in \
  shared_buffers \
  effective_cache_size \
  work_mem \
  maintenance_work_mem \
  max_connections \
  random_page_cost \
  effective_io_concurrency \
  checkpoint_timeout \
  max_wal_size \
  autovacuum \
  autovacuum_naptime \
  autovacuum_vacuum_scale_factor \
  autovacuum_analyze_scale_factor \
  track_io_timing; do
  line="$(setting_line "$name")"
  if [[ -n "$line" ]]; then
    echo "$line"
  else
    echo "$name=<permission denied or unavailable>"
  fi
done

echo
echo "=== 扩展状态 ==="
run_sql "
SELECT
  available.name,
  installed.extversion AS installed_version,
  available.default_version,
  installed.extname IS NOT NULL AS installed
FROM pg_available_extensions available
LEFT JOIN pg_extension installed ON installed.extname = available.name
WHERE available.name IN ('pg_stat_statements', 'pg_trgm')
ORDER BY available.name;
"

pg_stat_installed="$(run_sql_tuples "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements');" 2>/dev/null || true)"

if [[ "$APPLY_EXTENSION" == "true" ]]; then
  echo
  echo "=== 安装 pg_stat_statements 扩展 ==="
  run_sql "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
  pg_stat_installed="t"
fi

echo
echo "=== 数据库统计 ==="
run_sql "
SELECT
  datname,
  blks_read,
  blks_hit,
  ROUND(100.0 * blks_hit / GREATEST(blks_hit + blks_read, 1), 2) AS cache_hit_pct,
  temp_files,
  pg_size_pretty(temp_bytes) AS temp_bytes,
  deadlocks
FROM pg_stat_database
WHERE datname = current_database();
"

echo
echo "=== 表维护 Top 15 ==="
run_sql "
SELECT
  relname,
  n_live_tup,
  n_dead_tup,
  ROUND(100.0 * n_dead_tup / GREATEST(n_live_tup + n_dead_tup, 1), 2) AS dead_pct,
  last_autovacuum,
  last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC
LIMIT 15;
"

echo
echo "=== 建议 ==="

shared_buffers="$(setting_value "shared_buffers")"
track_io_timing="$(setting_value "track_io_timing")"
random_page_cost="$(setting_value "random_page_cost")"

if [[ "$pg_stat_installed" != "t" ]]; then
  cat <<'EOF'
- pg_stat_statements 未安装。建议由数据库管理员执行：
    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EOF
else
  echo "- pg_stat_statements 已安装。"
fi

if [[ "$track_io_timing" != "on" ]]; then
  cat <<'EOF'
- track_io_timing 当前未开启。建议写入 postgresql.conf 或 ALTER SYSTEM：
    ALTER SYSTEM SET track_io_timing = 'on';
EOF
else
  echo "- track_io_timing 已开启。"
fi

if [[ "$shared_buffers" =~ ^[0-9]+$ && "$shared_buffers" -lt 65536 ]]; then
  cat <<'EOF'
- shared_buffers 低于 512MB。生产建议按机器内存评估，常见起点：
    ALTER SYSTEM SET shared_buffers = '1GB';
    ALTER SYSTEM SET effective_cache_size = '3GB';
EOF
else
  echo "- shared_buffers 未发现明显过低，仍需按机器内存复核。"
fi

if [[ "$random_page_cost" == "4" ]]; then
  cat <<'EOF'
- random_page_cost 仍为默认 4。若数据库在 SSD/NVMe 上，建议评估：
    ALTER SYSTEM SET random_page_cost = '1.1';
EOF
else
  echo "- random_page_cost 已非默认 4。"
fi

cat <<'EOF'
- 上述 ALTER SYSTEM 属于实例配置变更，需要管理员确认并 reload/restart：
    SELECT pg_reload_conf();
  shared_buffers 变更需要重启 PostgreSQL 才生效。
- 索引或表结构优化必须新增 Flyway，不要直接在生产库执行 DDL。
EOF
