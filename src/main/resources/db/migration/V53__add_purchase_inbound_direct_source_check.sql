ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT chk_po_purchase_inbound_item_source_identity_nn
        CHECK (source_purchase_order_item_id IS NOT NULL) NOT VALID;
