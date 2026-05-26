-- V154: Autovacuum tuning recommendations (documentation migration)
-- NOTE: ALTER SYSTEM requires superuser. Applied manually via postgres user.
-- To verify current settings:
--   SELECT name, setting, unit FROM pg_settings WHERE name LIKE 'autovacuum%' ORDER BY name;

-- Recommended production settings (requires superuser):
--   ALTER SYSTEM SET autovacuum_vacuum_scale_factor = '0.05';   -- vacuum at 5% dead tuples (default 20%)
--   ALTER SYSTEM SET autovacuum_max_workers = '5';              -- more concurrent vacuum workers
--   ALTER SYSTEM SET autovacuum_naptime = '30s';                -- check more frequently
--   ALTER SYSTEM SET autovacuum_vacuum_cost_limit = '2000';     -- faster vacuum (default 200)
--   ALTER SYSTEM SET autovacuum_work_mem = '256MB';             -- more memory per worker
--   SELECT pg_reload_conf();

-- Verify current values (informational only):
DO $$
DECLARE
    rec RECORD;
BEGIN
    RAISE NOTICE '=== Current Autovacuum Settings ===';
    FOR rec IN
        SELECT name, setting, unit
        FROM pg_settings
        WHERE name IN (
            'autovacuum_vacuum_scale_factor', 'autovacuum_max_workers',
            'autovacuum_naptime', 'autovacuum_vacuum_cost_limit', 'autovacuum_work_mem'
        )
        ORDER BY name
    LOOP
        RAISE NOTICE '% = % %', rec.name, rec.setting, COALESCE(rec.unit, '');
    END LOOP;
END $$;
