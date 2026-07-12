ALTER TABLE public.po_purchase_inbound_item
    ALTER COLUMN source_purchase_order_item_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_inbound_item_source_identity_nn;
