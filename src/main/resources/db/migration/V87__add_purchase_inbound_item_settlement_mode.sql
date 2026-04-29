ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS settlement_mode VARCHAR(32);

UPDATE po_purchase_inbound_item item
SET settlement_mode = COALESCE(NULLIF(TRIM(item.settlement_mode), ''), NULLIF(TRIM(inbound.settlement_mode), ''), '理算')
FROM po_purchase_inbound inbound
WHERE item.inbound_id = inbound.id
  AND (item.settlement_mode IS NULL OR TRIM(item.settlement_mode) = '');

UPDATE po_purchase_inbound_item
SET settlement_mode = '理算'
WHERE settlement_mode IS NULL OR TRIM(settlement_mode) = '';

ALTER TABLE po_purchase_inbound_item
    ALTER COLUMN settlement_mode SET NOT NULL;
