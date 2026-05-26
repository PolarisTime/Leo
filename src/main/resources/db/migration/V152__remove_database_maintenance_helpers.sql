-- Keep the embedded ERP database page read-only.
-- Monitoring now reads PostgreSQL built-in pg_stat_* views directly and treats
-- pg_stat_statements as an optional read-only block.

DROP FUNCTION IF EXISTS fn_cache_warmup();
DROP FUNCTION IF EXISTS fn_reset_query_stats();

DROP VIEW IF EXISTS v_top_slow_queries;
DROP VIEW IF EXISTS v_cache_efficiency;
DROP VIEW IF EXISTS v_table_bloat;
DROP VIEW IF EXISTS v_unused_indexes;

DROP EXTENSION IF EXISTS pg_buffercache;
DROP EXTENSION IF EXISTS pg_prewarm;
DROP EXTENSION IF EXISTS pg_repack;
