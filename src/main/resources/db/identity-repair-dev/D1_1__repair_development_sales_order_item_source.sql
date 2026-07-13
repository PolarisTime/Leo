SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.so_sales_order_item
    IN SHARE ROW EXCLUSIVE MODE;

LOCK TABLE public.po_purchase_inbound_item,
           public.po_purchase_order_item
    IN SHARE MODE;

DO $$
BEGIN
    IF current_database() <> 'leo' THEN
        RAISE EXCEPTION 'D1.1 dev repair: target database must be leo';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D1.1 dev repair: cannot run on a standby';
    END IF;

    IF (SELECT MAX(version::integer)
        FROM public.flyway_schema_history
        WHERE success = true) <> 32
       OR NOT EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = true
       )
       OR EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = false
       ) THEN
        RAISE EXCEPTION 'D1.1 dev repair: main Flyway line must stop exactly at V32';
    END IF;

    IF (SELECT MAX(version::numeric)
        FROM public.flyway_dev_identity_repair_history
        WHERE success = true) <> 1
       OR EXISTS (
            SELECT 1
            FROM public.flyway_dev_identity_repair_history
            WHERE success = false
       ) THEN
        RAISE EXCEPTION 'D1.1 dev repair: repair Flyway line must stop exactly at D1';
    END IF;
END $$;

CREATE TEMP TABLE approved_sales_order_item_source (
    sales_order_item_id bigint PRIMARY KEY,
    sales_order_id bigint NOT NULL,
    source_inbound_item_id bigint NOT NULL,
    source_purchase_order_item_id bigint NOT NULL
) ON COMMIT DROP;

INSERT INTO approved_sales_order_item_source
VALUES (
    900000000000000302,
    900000000000000301,
    900000000000000202,
    900000000000000102
);

DO $$
BEGIN
    IF (SELECT COUNT(*)
        FROM public.so_sales_order_item item
        LEFT JOIN public.po_purchase_inbound_item inbound
          ON inbound.id = item.source_inbound_item_id
        LEFT JOIN public.po_purchase_order_item purchase_item
          ON purchase_item.id = item.source_purchase_order_item_id
        WHERE (item.source_inbound_item_id IS NOT NULL AND inbound.id IS NULL)
           OR (item.source_purchase_order_item_id IS NOT NULL AND purchase_item.id IS NULL)
           OR num_nonnulls(
                item.source_inbound_item_id,
                item.source_purchase_order_item_id
           ) > 1) <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM approved_sales_order_item_source approved
            JOIN public.so_sales_order_item item
              ON item.id = approved.sales_order_item_id
             AND item.order_id = approved.sales_order_id
             AND item.source_inbound_item_id = approved.source_inbound_item_id
             AND item.source_purchase_order_item_id =
                 approved.source_purchase_order_item_id
            JOIN public.po_purchase_inbound_item inbound
              ON inbound.id = approved.source_inbound_item_id
             AND inbound.source_purchase_order_item_id =
                 approved.source_purchase_order_item_id
            JOIN public.po_purchase_order_item purchase_item
              ON purchase_item.id = approved.source_purchase_order_item_id
            WHERE item.material_id = inbound.material_id
              AND inbound.material_id = purchase_item.material_id
              AND item.material_code = inbound.material_code
              AND inbound.material_code = purchase_item.material_code
              AND item.warehouse_id IS NOT DISTINCT FROM inbound.warehouse_id
              AND item.quantity <= inbound.quantity
       ) THEN
        RAISE EXCEPTION 'D1.1 dev repair: sales order item source snapshot drifted';
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.so_sales_order_item target
    SET source_purchase_order_item_id = NULL
    FROM approved_sales_order_item_source approved
    WHERE target.id = approved.sales_order_item_id
      AND target.order_id = approved.sales_order_id
      AND target.source_inbound_item_id = approved.source_inbound_item_id
      AND target.source_purchase_order_item_id =
          approved.source_purchase_order_item_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION
            'D1.1 dev repair: expected to update 1 sales order item, updated %',
            affected_rows;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM approved_sales_order_item_source approved
        JOIN public.so_sales_order_item target
          ON target.id = approved.sales_order_item_id
        JOIN public.po_purchase_inbound_item inbound
          ON inbound.id = target.source_inbound_item_id
        WHERE target.order_id = approved.sales_order_id
          AND target.source_inbound_item_id = approved.source_inbound_item_id
          AND target.source_purchase_order_item_id IS NULL
          AND inbound.source_purchase_order_item_id =
              approved.source_purchase_order_item_id
    )
       OR EXISTS (
            SELECT 1
            FROM public.so_sales_order_item item
            LEFT JOIN public.po_purchase_inbound_item inbound
              ON inbound.id = item.source_inbound_item_id
            LEFT JOIN public.po_purchase_order_item purchase_item
              ON purchase_item.id = item.source_purchase_order_item_id
            WHERE (item.source_inbound_item_id IS NOT NULL AND inbound.id IS NULL)
               OR (item.source_purchase_order_item_id IS NOT NULL
                   AND purchase_item.id IS NULL)
               OR num_nonnulls(
                    item.source_inbound_item_id,
                    item.source_purchase_order_item_id
               ) > 1
       ) THEN
        RAISE EXCEPTION 'D1.1 dev repair: repaired sales source failed post-validation';
    END IF;
END $$;
