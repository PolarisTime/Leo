INSERT INTO fm_invoice_receipt_item (
    id, receipt_id, line_no, source_no, source_purchase_order_item_id,
    material_code, brand, category, material, spec, length, unit, warehouse_name, batch_no,
    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES (
    700790000000000001, 700780000000000001, 1, 'PO-202604-001', NULL,
    'MAT-PO-001', '宝钢', '板材', 'Q235B', '10mm', '6000', '吨', '烟台港一号库', '',
    2, '件', 2.000, 0, 4.000, 3000.00, 12000.00
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fm_invoice_issue_item (
    id, issue_id, line_no, source_no, source_sales_order_item_id,
    material_code, brand, category, material, spec, length, unit, warehouse_name, batch_no,
    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES (
    700791000000000001, 700781000000000001, 1, 'SO-202604-001', NULL,
    'MAT-SO-001', '鞍钢', '卷板', 'Q355B', '12mm', '9000', '吨', '江北项目仓', '',
    2, '件', 4.000, 0, 8.000, 1000.00, 8000.00
)
ON CONFLICT (id) DO NOTHING;
