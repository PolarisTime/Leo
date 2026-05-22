-- Core PostgreSQL extensions for production operations
-- pg_stat_statements: query performance monitoring
-- pg_repack: online table repack (no lock VACUUM FULL alternative)
-- pg_prewarm: cache warmup after restart
-- pg_buffercache: shared buffer cache diagnostics
--
-- Note: pg_cron is installed in the "postgres" database (required by its design),
--       not in the application database. See setup instructions below:
--   psql -U postgres -d postgres -c "CREATE EXTENSION IF NOT EXISTS pg_cron;"

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_repack;
CREATE EXTENSION IF NOT EXISTS pg_prewarm;
CREATE EXTENSION IF NOT EXISTS pg_buffercache;
