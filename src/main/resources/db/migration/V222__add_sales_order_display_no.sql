-- 销售订单 8 位纯数字展示编号。
-- 业务关联仍使用雪花主键 id；display_no 只用于打印与人工检索，不保证唯一。
ALTER TABLE so_sales_order
    ADD COLUMN display_no VARCHAR(8);

COMMENT ON COLUMN so_sales_order.display_no IS
    '8 位纯数字展示编号（由 id 派生，不保证唯一；业务关联仍使用 id）';

-- 存量单据：用 id 的 md5 前 8 位十六进制转 32 位整数后取模，保证一次迁移内稳定生成。
UPDATE so_sales_order
SET display_no = lpad(
        ((('x' || substr(md5(id::text), 1, 8))::bit(32)::bigint % 100000000)::text),
        8,
        '0'
    )
WHERE display_no IS NULL;

CREATE INDEX idx_so_sales_order_display_no
    ON so_sales_order (display_no);
