-- Optional PostgreSQL diagnostics extension.
-- The ERP monitoring page must not depend on maintenance extensions. It reads
-- built-in pg_stat_* views by default and enables the slow SQL block only when
-- pg_stat_statements is already available.

DO $$
BEGIN
    BEGIN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS pg_stat_statements';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'pg_stat_statements is optional and was not installed: %', SQLERRM;
    END;
END
$$;
