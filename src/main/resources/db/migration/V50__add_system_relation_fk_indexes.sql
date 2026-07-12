-- Existing indexes on these columns are partial or do not have the foreign-key
-- column as their leading column. Full indexes also cover soft-deleted rows.
CREATE INDEX idx_sys_role_parent_id_fk
    ON public.sys_role (parent_id);

CREATE INDEX idx_sys_attachment_binding_attachment_id_fk
    ON public.sys_attachment_binding (attachment_id);
