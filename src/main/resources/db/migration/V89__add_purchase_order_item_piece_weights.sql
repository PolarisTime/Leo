CREATE TABLE IF NOT EXISTS po_purchase_order_item_piece_weight (
    id BIGINT PRIMARY KEY,
    purchase_order_item_id BIGINT NOT NULL,
    piece_no INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    sales_order_item_id BIGINT,
    CONSTRAINT uq_po_item_piece_weight_item_piece UNIQUE (purchase_order_item_id, piece_no),
    CONSTRAINT fk_po_item_piece_weight_purchase_item FOREIGN KEY (purchase_order_item_id)
        REFERENCES po_purchase_order_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_item_piece_weight_sales_item FOREIGN KEY (sales_order_item_id)
        REFERENCES so_sales_order_item (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_po_item_piece_weight_purchase_item
    ON po_purchase_order_item_piece_weight (purchase_order_item_id);

CREATE INDEX IF NOT EXISTS idx_po_item_piece_weight_sales_item
    ON po_purchase_order_item_piece_weight (sales_order_item_id);

WITH expanded AS (
    SELECT
        item.id AS purchase_order_item_id,
        item.quantity,
        item.weight_ton,
        ROUND(item.weight_ton / NULLIF(item.quantity, 0), 3) AS average_weight_ton,
        generate_series(1, item.quantity) AS piece_no
    FROM po_purchase_order_item item
    WHERE item.quantity > 0
),
weighted AS (
    SELECT
        expanded.*,
        LEAST(ABS(ROUND((expanded.weight_ton - expanded.average_weight_ton * expanded.quantity) * 1000)::INTEGER), expanded.quantity) AS residual_count,
        CASE
            WHEN ROUND((expanded.weight_ton - expanded.average_weight_ton * expanded.quantity) * 1000)::INTEGER > 0 THEN 0.001
            WHEN ROUND((expanded.weight_ton - expanded.average_weight_ton * expanded.quantity) * 1000)::INTEGER < 0 THEN -0.001
            ELSE 0
        END AS residual_adjustment
    FROM expanded
),
pieces AS (
    SELECT
        -ROW_NUMBER() OVER (ORDER BY purchase_order_item_id, piece_no) AS id,
        purchase_order_item_id,
        piece_no,
        ROUND(
            average_weight_ton
                + CASE
                    WHEN residual_adjustment <> 0 AND piece_no > quantity - residual_count THEN residual_adjustment
                    ELSE 0
                END,
            3
        ) AS weight_ton
    FROM weighted
)
INSERT INTO po_purchase_order_item_piece_weight (id, purchase_order_item_id, piece_no, weight_ton)
SELECT id, purchase_order_item_id, piece_no, weight_ton
FROM pieces
ON CONFLICT (purchase_order_item_id, piece_no) DO NOTHING;

WITH sales_piece_slots AS (
    SELECT
        item.id AS sales_order_item_id,
        item.source_purchase_order_item_id AS purchase_order_item_id,
        ROW_NUMBER() OVER (
            PARTITION BY item.source_purchase_order_item_id
            ORDER BY sales_order.created_at NULLS LAST, sales_order.id, item.line_no, piece_slot
        ) AS allocation_no
    FROM so_sales_order_item item
    JOIN so_sales_order sales_order ON sales_order.id = item.order_id
    CROSS JOIN LATERAL generate_series(1, item.quantity) AS piece_slot
    WHERE sales_order.deleted_flag = FALSE
      AND item.source_purchase_order_item_id IS NOT NULL
      AND item.quantity > 0
),
purchase_piece_slots AS (
    SELECT
        piece.id,
        piece.purchase_order_item_id,
        ROW_NUMBER() OVER (
            PARTITION BY piece.purchase_order_item_id
            ORDER BY piece.piece_no
        ) AS allocation_no
    FROM po_purchase_order_item_piece_weight piece
)
UPDATE po_purchase_order_item_piece_weight piece
SET sales_order_item_id = sales_piece.sales_order_item_id
FROM purchase_piece_slots purchase_piece
JOIN sales_piece_slots sales_piece
    ON sales_piece.purchase_order_item_id = purchase_piece.purchase_order_item_id
   AND sales_piece.allocation_no = purchase_piece.allocation_no
WHERE piece.id = purchase_piece.id;
