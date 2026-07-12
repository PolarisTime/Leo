-- Foreign-key maintenance must include historical soft-deleted references.
CREATE INDEX idx_po_purchase_refund_source_purchase_order_id_fk
    ON public.po_purchase_refund (source_purchase_order_id);
CREATE INDEX idx_fm_supplier_refund_receipt_purchase_refund_id_fk
    ON public.fm_supplier_refund_receipt (purchase_refund_id);
CREATE INDEX idx_sys_role_conflict_conflict_role_id_fk
    ON public.sys_role_conflict (conflict_role_id);
