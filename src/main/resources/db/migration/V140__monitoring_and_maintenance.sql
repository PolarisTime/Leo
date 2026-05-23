-- V140: PostgreSQL extension-based monitoring and maintenance helpers
-- Leverages pg_stat_statements, pg_buffercache, pg_prewarm

-- ============================================================
-- 1. Top-N 慢查询视图 (基于 pg_stat_statements)
-- ============================================================

CREATE OR REPLACE VIEW v_top_slow_queries AS
SELECT
    queryid,
    LEFT(query, 200) AS query_preview,
    calls,
    ROUND(mean_exec_time::numeric, 2) AS avg_ms,
    ROUND(total_exec_time::numeric, 2) AS total_ms,
    ROUND((100.0 * total_exec_time / SUM(total_exec_time) OVER())::numeric, 1) AS pct_total,
    rows,
    shared_blks_hit,
    shared_blks_read,
    ROUND((100.0 * shared_blks_hit / NULLIF(shared_blks_hit + shared_blks_read, 0))::numeric, 1) AS cache_hit_pct
FROM pg_stat_statements
WHERE calls > 10
ORDER BY total_exec_time DESC
LIMIT 20;

-- ============================================================
-- 2. 缓存命中率视图 (基于 pg_buffercache + pg_stat_statements)
-- ============================================================

CREATE OR REPLACE VIEW v_cache_efficiency AS
SELECT
    s.relname AS table_name,
    t.n_tup_ins AS inserts,
    t.n_tup_upd AS updates,
    t.n_tup_del AS deletes,
    ROUND(100.0 * t.n_tup_hot_upd / NULLIF(t.n_tup_upd, 0), 1) AS hot_update_pct,
    ROUND(100.0 * s.heap_blks_hit / NULLIF(s.heap_blks_read + s.heap_blks_hit, 0), 1) AS heap_cache_pct,
    ROUND(100.0 * s.idx_blks_hit / NULLIF(s.idx_blks_read + s.idx_blks_hit, 0), 1) AS idx_cache_pct
FROM pg_stat_user_tables t
JOIN pg_statio_user_tables s ON t.relid = s.relid
WHERE t.n_tup_ins + t.n_tup_upd + t.n_tup_del > 100
ORDER BY (s.heap_blks_read + s.idx_blks_read) DESC
LIMIT 20;

-- ============================================================
-- 3. 表膨胀率视图 (死元组占比)
-- ============================================================

CREATE OR REPLACE VIEW v_table_bloat AS
SELECT
    schemaname || '.' || relname AS table_name,
    n_live_tup AS live_rows,
    n_dead_tup AS dead_rows,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 1) AS dead_pct,
    n_tup_upd AS updates,
    last_vacuum,
    last_autovacuum,
    autovacuum_count,
    n_tup_hot_upd,
    ROUND(100.0 * n_tup_hot_upd / NULLIF(n_tup_upd, 0), 1) AS hot_pct
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY dead_pct DESC
LIMIT 20;

-- ============================================================
-- 4. 索引使用率视图
-- ============================================================

CREATE OR REPLACE VIEW v_unused_indexes AS
SELECT
    schemaname || '.' || indexrelname AS index_name,
    relname AS table_name,
    idx_scan AS scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE idx_scan < 10
  AND schemaname = 'public'
  AND indexrelname NOT LIKE '%pkey%'
  AND indexrelname NOT LIKE '%unique%'
ORDER BY pg_relation_size(indexrelid) DESC;

-- ============================================================
-- 5. cache_warmup 函数 (应用重启后预热缓存)
-- ============================================================

CREATE OR REPLACE FUNCTION fn_cache_warmup() RETURNS text AS $$
DECLARE
    rec record;
    count int := 0;
BEGIN
    FOR rec IN
        SELECT relname FROM pg_stat_user_tables
        WHERE seq_scan > 0 OR idx_scan > 0
        ORDER BY (heap_blks_hit + idx_blks_hit) DESC
        LIMIT 30
    LOOP
        PERFORM pg_prewarm(rec.relname);
        count := count + 1;
    END LOOP;
    RETURN 'Prewarmed ' || count || ' tables';
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 6. 重置 pg_stat_statements（部署后基线重置）
-- ============================================================

CREATE OR REPLACE FUNCTION fn_reset_query_stats() RETURNS text AS $$
BEGIN
    PERFORM pg_stat_statements_reset();
    RETURN 'Query stats reset OK';
END;
$$ LANGUAGE plpgsql;
