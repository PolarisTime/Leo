#!/usr/bin/env bash
# Leo ERP 数据库只读诊断脚本
# 用法: maintenance.sh {stats|activity|tables|indexes|queries|operation-log-partitions|all}
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env/dev.sh"

PG_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
PG_PORT="${SPRING_DATASOURCE_PORT:-5432}"
PG_DB="${SPRING_DATASOURCE_DB:-leo}"
PG_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
PG_PASS="${SPRING_DATASOURCE_PASSWORD:-}"
PG_CONN="host=$PG_HOST port=$PG_PORT dbname=$PG_DB user=$PG_USER"
[[ -n "$PG_PASS" ]] && PG_CONN="$PG_CONN password=$PG_PASS"

psql_cmd() { psql "$PG_CONN" -c "$1" 2>/dev/null; }

do_stats() {
    echo "=== 基础状态 ==="
    psql_cmd "
        SELECT
            d.datname,
            pg_size_pretty(pg_database_size(d.datname)) AS size,
            s.numbackends AS connections,
            s.xact_commit,
            s.xact_rollback,
            s.deadlocks,
            s.temp_files,
            pg_size_pretty(s.temp_bytes) AS temp_bytes,
            ROUND((100.0 * s.blks_hit / NULLIF(s.blks_hit + s.blks_read, 0))::numeric, 2) AS cache_hit_pct
        FROM pg_database d
        JOIN pg_stat_database s ON s.datid = d.oid
        WHERE d.datname = current_database();
    "
}

do_activity() {
    echo "=== 当前活动 ==="
    psql_cmd "
        SELECT
            state,
            wait_event_type,
            count(*) AS sessions,
            max(now() - xact_start) AS longest_xact,
            max(now() - query_start) FILTER (WHERE state = 'active') AS longest_query
        FROM pg_stat_activity
        WHERE datname = current_database()
        GROUP BY state, wait_event_type
        ORDER BY sessions DESC;
    "
}

do_tables() {
    echo "=== 表健康 ==="
    psql_cmd "
        SELECT
            t.schemaname || '.' || t.relname AS table_name,
            t.n_live_tup,
            t.n_dead_tup,
            ROUND((100.0 * t.n_dead_tup / NULLIF(t.n_live_tup + t.n_dead_tup, 0))::numeric, 2) AS dead_pct,
            t.seq_scan,
            t.idx_scan,
            t.n_mod_since_analyze,
            t.last_autovacuum,
            t.last_autoanalyze
        FROM pg_stat_user_tables t
        ORDER BY dead_pct DESC NULLS LAST, t.n_mod_since_analyze DESC, t.seq_scan DESC
        LIMIT 20;
    "
}

do_indexes() {
    echo "=== 索引健康 ==="
    psql_cmd "
        SELECT
            s.schemaname || '.' || s.indexrelname AS index_name,
            s.relname AS table_name,
            pg_size_pretty(pg_relation_size(s.indexrelid)) AS size,
            s.idx_scan,
            i.indisvalid,
            i.indisunique,
            i.indisprimary
        FROM pg_stat_user_indexes s
        JOIN pg_index i ON i.indexrelid = s.indexrelid
        WHERE s.schemaname = 'public'
        ORDER BY i.indisvalid ASC, (CASE WHEN s.idx_scan = 0 THEN pg_relation_size(s.indexrelid) ELSE 0 END) DESC
        LIMIT 20;
    "
}

do_queries() {
    echo "=== 慢 SQL 摘要 (可选 pg_stat_statements) ==="
    local available
    available="$(psql "$PG_CONN" -Atc "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')" 2>/dev/null || true)"
    if [[ "$available" != "t" ]]; then
        echo "未启用 pg_stat_statements"
        return 0
    fi

    psql_cmd "
        SELECT
            queryid,
            LEFT(regexp_replace(query, '\\s+', ' ', 'g'), 180) AS query_preview,
            calls,
            ROUND(total_exec_time::numeric, 2) AS total_ms,
            ROUND(mean_exec_time::numeric, 2) AS avg_ms,
            rows
        FROM pg_stat_statements
        WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
        ORDER BY total_exec_time DESC
        LIMIT 10;
    "
}

do_operation_log_partitions() {
    local retention_months="${LEO_OPERATION_LOG_RETENTION_MONTHS:-12}"
    if [[ ! "$retention_months" =~ ^[1-9][0-9]*$ ]]; then
        echo "LEO_OPERATION_LOG_RETENTION_MONTHS 必须是正整数" >&2
        return 1
    fi

    echo "=== 操作日志分区保留状态 ==="
    psql_cmd "
        WITH policy AS (
            SELECT date_trunc('month', CURRENT_DATE)
                       - make_interval(months => $retention_months) AS cutoff
        )
        SELECT
            $retention_months AS retention_months,
            cutoff::date AS complete_partition_cutoff,
            (SELECT count(*) FROM public.sys_operation_log_default) AS default_partition_rows,
            (
                SELECT count(*)
                FROM public.sys_operation_log
                WHERE operation_time < cutoff
            ) AS expired_active_rows,
            (
                SELECT count(*)
                FROM pg_inherits
                WHERE inhparent = 'public.sys_operation_log'::regclass
            ) AS attached_partitions
        FROM policy;
    "
}

case "${1:-}" in
    stats)    do_stats ;;
    activity) do_activity ;;
    tables)   do_tables ;;
    indexes)  do_indexes ;;
    queries)  do_queries ;;
    operation-log-partitions) do_operation_log_partitions ;;
    all)      do_stats; echo; do_activity; echo; do_tables; echo; do_indexes; echo; do_queries; echo; do_operation_log_partitions ;;
    *)
        echo "用法: maintenance.sh {stats|activity|tables|indexes|queries|operation-log-partitions|all}"
        echo "  stats     基础状态"
        echo "  activity  会话/锁/长事务"
        echo "  tables    表健康"
        echo "  indexes   索引健康"
        echo "  queries   慢 SQL 摘要（可选 pg_stat_statements）"
        echo "  operation-log-partitions  操作日志保留期与分区状态"
        echo "  all       查看全部只读诊断"
        exit 1 ;;
esac
