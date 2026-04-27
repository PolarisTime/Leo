DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'so_sales_order'
          AND column_name = 'order_date'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'so_sales_order'
          AND column_name = 'delivery_date'
    ) THEN
        ALTER TABLE so_sales_order RENAME COLUMN order_date TO delivery_date;
    END IF;
END $$;
