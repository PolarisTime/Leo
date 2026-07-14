UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN (
    'cash-reversal',
    'invoice-receipt',
    'invoice-issue',
    'pending-invoice-receipt-report'
);

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN (
    'cash-reversal',
    'invoice-receipt',
    'invoice-issue',
    'pending-invoice-receipt-report'
);

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN (
    'cash-reversal',
    'invoice-receipt',
    'invoice-issue',
    'pending-invoice-receipt-report'
);

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code IN ('RULE_CR', 'RULE_SP', 'RULE_KP');

COMMENT ON TABLE public.fm_cash_reversal
    IS '历史资金冲销单归档；资金冲销功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_receipt
    IS '历史收票单归档；收票功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_receipt_item
    IS '历史收票单明细归档；收票功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_receipt_source_order
    IS '历史收票来源单据归档；收票功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_issue
    IS '历史开票单归档；开票功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_issue_item
    IS '历史开票单明细归档；开票功能已退役，禁止新增业务数据';

COMMENT ON TABLE public.fm_invoice_issue_source_order
    IS '历史开票来源单据归档；开票功能已退役，禁止新增业务数据';
