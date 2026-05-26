-- Conservative PostgreSQL query tuning indexes for current repository hot paths.
-- Keep this migration focused: avoid broad duplicate indexes and only cover
-- filters/orderings that are used by existing services.

-- Purchase allocation: several summaries filter/group by source_purchase_order_item_id
-- and join back to the inbound header.
CREATE INDEX IF NOT EXISTS idx_po_inbound_item_source_purchase_item_inbound
    ON po_purchase_inbound_item (source_purchase_order_item_id, inbound_id)
    WHERE source_purchase_order_item_id IS NOT NULL;

-- Invoice issue allocation summaries filter by source_sales_order_item_id only;
-- the older (source_no, source_sales_order_item_id) index cannot serve that efficiently.
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_item_source_sales_item
    ON fm_invoice_issue_item (source_sales_order_item_id, issue_id)
    WHERE source_sales_order_item_id IS NOT NULL;

-- Piece-weight allocation repeatedly reads/deletes the unallocated rows of a
-- purchase item ordered by piece_no.
CREATE INDEX IF NOT EXISTS idx_po_item_piece_weight_available
    ON po_purchase_order_item_piece_weight (purchase_order_item_id, piece_no)
    WHERE sales_order_item_id IS NULL;

-- Session management queries active refresh tokens by user and globally.
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_active_user
    ON auth_refresh_token (user_id, expires_at, created_at)
    WHERE deleted_flag = FALSE AND revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_active_expires
    ON auth_refresh_token (expires_at)
    WHERE deleted_flag = FALSE AND revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_revoke_reason
    ON auth_refresh_token (user_id, revoke_reason, revoked_at DESC)
    WHERE deleted_flag = FALSE AND revoke_reason IS NOT NULL;

-- API key admin filtering commonly combines user/status/expiry over live keys.
CREATE INDEX IF NOT EXISTS idx_auth_api_key_active_user_status
    ON auth_api_key (user_id, status, expires_at)
    WHERE deleted_flag = FALSE;

-- Attachment lookups always ignore deleted bindings and return stable ordering.
CREATE INDEX IF NOT EXISTS idx_sys_attachment_binding_active_record_order
    ON sys_attachment_binding (module_key, record_id, sort_order, id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_sys_attachment_binding_active_attachment_order
    ON sys_attachment_binding (module_key, attachment_id, record_id, sort_order, id)
    WHERE deleted_flag = FALSE;
