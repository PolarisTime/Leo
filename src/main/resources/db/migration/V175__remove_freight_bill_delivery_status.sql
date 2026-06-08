ALTER TABLE lg_freight_bill DROP CONSTRAINT IF EXISTS chk_delivery_status;

DROP INDEX IF EXISTS idx_lg_freight_bill_status;
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_status ON lg_freight_bill (status);

ALTER TABLE lg_freight_bill DROP COLUMN IF EXISTS delivery_status;

UPDATE sys_print_template
SET template_html = REPLACE(
        template_html,
        '<tr><th>审核状态</th><td>{{status}}</td><th>送达状态</th><td>{{deliveryStatus}}</td></tr>',
        '<tr><th>审核状态</th><td colspan="3">{{status}}</td></tr>'
    )
WHERE bill_type = 'freight-bill'
  AND template_html LIKE '%{{deliveryStatus}}%';
