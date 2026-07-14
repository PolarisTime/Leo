-- Promote historical records only when every purchase item is fully covered by effective inbounds.
WITH effective_inbound_quantity AS (
    SELECT item.order_id,
           item.id AS purchase_order_item_id,
           item.quantity AS ordered_quantity,
           COALESCE(SUM(inbound_item.quantity) FILTER (
               WHERE inbound.deleted_flag = FALSE
                 AND inbound.status IN ('已审核', '完成入库')
           ), 0)::bigint AS inbound_quantity
      FROM po_purchase_order_item item
      LEFT JOIN po_purchase_inbound_item inbound_item
        ON inbound_item.source_purchase_order_item_id = item.id
      LEFT JOIN po_purchase_inbound inbound
        ON inbound.id = inbound_item.inbound_id
     GROUP BY item.order_id, item.id, item.quantity
), eligible_order AS (
    SELECT purchase_order.id
      FROM po_purchase_order purchase_order
      JOIN effective_inbound_quantity quantity
        ON quantity.order_id = purchase_order.id
     WHERE purchase_order.deleted_flag = FALSE
       AND purchase_order.status = '已审核'
     GROUP BY purchase_order.id
    HAVING BOOL_AND(quantity.ordered_quantity >= 1)
       AND BOOL_AND(quantity.inbound_quantity = quantity.ordered_quantity)
       AND NOT EXISTS (
           SELECT 1
             FROM po_purchase_order_item source_item
             JOIN po_purchase_inbound_item inbound_item
               ON inbound_item.source_purchase_order_item_id = source_item.id
             JOIN po_purchase_inbound inbound
               ON inbound.id = inbound_item.inbound_id
              AND inbound.deleted_flag = FALSE
            WHERE source_item.order_id = purchase_order.id
              AND inbound.status = '草稿'
       )
)
UPDATE po_purchase_inbound inbound
   SET status = '完成入库'
 WHERE inbound.deleted_flag = FALSE
   AND inbound.status = '已审核'
   AND EXISTS (
       SELECT 1
         FROM po_purchase_inbound_item inbound_item
         JOIN po_purchase_order_item source_item
           ON source_item.id = inbound_item.source_purchase_order_item_id
         JOIN eligible_order eligible
           ON eligible.id = source_item.order_id
        WHERE inbound_item.inbound_id = inbound.id
   );

WITH effective_inbound_quantity AS (
    SELECT item.order_id,
           item.id AS purchase_order_item_id,
           item.quantity AS ordered_quantity,
           COALESCE(SUM(inbound_item.quantity) FILTER (
               WHERE inbound.deleted_flag = FALSE
                 AND inbound.status = '完成入库'
           ), 0)::bigint AS inbound_quantity
      FROM po_purchase_order_item item
      LEFT JOIN po_purchase_inbound_item inbound_item
        ON inbound_item.source_purchase_order_item_id = item.id
      LEFT JOIN po_purchase_inbound inbound
        ON inbound.id = inbound_item.inbound_id
     GROUP BY item.order_id, item.id, item.quantity
), eligible_order AS (
    SELECT purchase_order.id
      FROM po_purchase_order purchase_order
      JOIN effective_inbound_quantity quantity
        ON quantity.order_id = purchase_order.id
     WHERE purchase_order.deleted_flag = FALSE
       AND purchase_order.status = '已审核'
     GROUP BY purchase_order.id
    HAVING BOOL_AND(quantity.ordered_quantity >= 1)
       AND BOOL_AND(quantity.inbound_quantity = quantity.ordered_quantity)
       AND NOT EXISTS (
           SELECT 1
             FROM po_purchase_order_item source_item
             JOIN po_purchase_inbound_item inbound_item
               ON inbound_item.source_purchase_order_item_id = source_item.id
             JOIN po_purchase_inbound inbound
               ON inbound.id = inbound_item.inbound_id
              AND inbound.deleted_flag = FALSE
            WHERE source_item.order_id = purchase_order.id
              AND inbound.status <> '完成入库'
       )
)
UPDATE po_purchase_order purchase_order
   SET status = '完成采购'
  FROM eligible_order eligible
 WHERE purchase_order.id = eligible.id;
