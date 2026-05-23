#!/usr/bin/env bash
# Leo ERP 数据库维护脚本
# 用法: maintenance.sh {repack|prewarm|vacuum|stats|all}
# 依赖: pg_repack, pg_prewarm, pg_stat_statements, pg_buffercache
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env-local.sh"

# 连接参数
PG_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
PG_PORT="${SPRING_DATASOURCE_PORT:-5432}"
PG_DB="${SPRING_DATASOURCE_DB:-leo}"
PG_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
PG_PASS="${SPRING_DATASOURCE_PASSWORD:-}"
PG_CONN="host=$PG_HOST port=$PG_PORT dbname=$PG_DB user=$PG_USER"
[[ -n "$PG_PASS" ]] && PG_CONN="$PG_CONN password=$PG_PASS"

psql_cmd() { psql "$PG_CONN" -c "$1" 2>/dev/null; }

# ---- V139 Fillfactor 表清单 ----
TABLES_FILLFACTOR=(
    po_purchase_order so_sales_order po_purchase_inbound so_sales_outbound
    lg_freight_bill st_customer_statement st_supplier_statement st_freight_statement
    fm_payment fm_receipt fm_invoice_issue fm_invoice_receipt
    ct_sales_contract ct_purchase_contract sys_user
)

# ============================================================
# repack: 在线重建表（应用 V139 fillfactor 到已有数据，无锁）
# ============================================================
do_repack() {
    echo "=== pg_repack 在线表重建 ==="
    echo "注意: pg_repack 需要临时磁盘空间（约表大小）"
    echo ""

    for table in "${TABLES_FILLFACTOR[@]}"; do
        echo -n "  $table ... "
        if pg_repack --no-order --table "$table" "$PG_CONN" > /dev/null 2>&1; then
            echo "OK"
        else
            echo "SKIPPED (可能不在本DB)"
        fi
    done
    echo ""
    echo "完成。V139 fillfactor 已应用到已有数据。"
}

# ============================================================
# prewarm: 预热缓存（重启/部署后执行）
# ============================================================
do_prewarm() {
    echo "=== 缓存预热 ==="
    psql_cmd "SELECT fn_cache_warmup();"
}

# ============================================================
# vacuum: 分析所有表（更新统计信息）
# ============================================================
do_vacuum() {
    echo "=== ANALYZE 关键表 ==="
    for table in "${TABLES_FILLFACTOR[@]}"; do
        echo -n "  $table ... "
        psql_cmd "ANALYZE $table;" > /dev/null && echo "OK" || echo "SKIP"
    done
    echo ""
    echo "完成。查询计划统计信息已更新。"
}

# ============================================================
# stats: 显示关键监控指标
# ============================================================
do_stats() {
    echo "=== TOP 慢查询 (pg_stat_statements) ==="
    psql_cmd "SELECT query_preview, calls, avg_ms, pct_total, cache_hit_pct FROM v_top_slow_queries LIMIT 10;"
    echo ""

    echo "=== 表膨胀率 (死元组) ==="
    psql_cmd "SELECT table_name, live_rows, dead_rows, dead_pct, last_autovacuum FROM v_table_bloat LIMIT 10;"
    echo ""

    echo "=== 缓存效率 ==="
    psql_cmd "SELECT table_name, heap_cache_pct, idx_cache_pct, hot_update_pct FROM v_cache_efficiency LIMIT 10;"
    echo ""

    echo "=== 未使用索引 ==="
    psql_cmd "SELECT index_name, table_name, size, scans FROM v_unused_indexes LIMIT 10;"
}

# ---- 入口 ----
case "${1:-}" in
    repack)  do_repack ;;
    prewarm) do_prewarm ;;
    vacuum)  do_vacuum ;;
    stats)   do_stats ;;
    all)     do_vacuum; echo; do_prewarm; echo; do_stats ;;
    *)
        echo "用法: maintenance.sh {repack|prewarm|vacuum|stats|all}"
        echo "  repack   在线重建表（应用 V139 fillfactor + 消除膨胀）"
        echo "  prewarm  缓存预热（重启/部署后执行）"
        echo "  vacuum   更新统计信息"
        echo "  stats    查看监控指标（慢查询/膨胀/缓存/索引）"
        echo "  all      一键执行全部"
        exit 1 ;;
esac
