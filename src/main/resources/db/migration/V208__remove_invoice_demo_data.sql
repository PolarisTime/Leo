-- Remove legacy invoice demo rows seeded by V47/V50.
-- Match both identifiers and original business fields to avoid deleting user-created data.

DELETE FROM fm_invoice_receipt_source_order
WHERE receipt_id IN (
    SELECT id
    FROM fm_invoice_receipt
    WHERE id = 700780000000000001
      AND receive_no = '2026SP000001'
      AND invoice_no = '031002600011'
      AND supplier_name = '江苏钢联供应链有限公司'
      AND amount = 12000.00
      AND tax_amount = 1560.00
);

DELETE FROM fm_invoice_issue_source_order
WHERE issue_id IN (
    SELECT id
    FROM fm_invoice_issue
    WHERE id = 700781000000000001
      AND issue_no = '2026KP000001'
      AND invoice_no = '044002600021'
      AND customer_name = '南京城建项目管理有限公司'
      AND project_name = '江北快速路一期'
      AND amount = 8000.00
      AND tax_amount = 1040.00
);

DELETE FROM fm_invoice_receipt_item
WHERE id = 700790000000000001
  AND receipt_id IN (
      SELECT id
      FROM fm_invoice_receipt
      WHERE id = 700780000000000001
        AND receive_no = '2026SP000001'
        AND invoice_no = '031002600011'
        AND supplier_name = '江苏钢联供应链有限公司'
        AND amount = 12000.00
        AND tax_amount = 1560.00
  )
  AND source_no = 'PO-202604-001'
  AND material_code = 'MAT-PO-001'
  AND brand = '宝钢'
  AND amount = 12000.00;

DELETE FROM fm_invoice_issue_item
WHERE id = 700791000000000001
  AND issue_id IN (
      SELECT id
      FROM fm_invoice_issue
      WHERE id = 700781000000000001
        AND issue_no = '2026KP000001'
        AND invoice_no = '044002600021'
        AND customer_name = '南京城建项目管理有限公司'
        AND project_name = '江北快速路一期'
        AND amount = 8000.00
        AND tax_amount = 1040.00
  )
  AND source_no = 'SO-202604-001'
  AND material_code = 'MAT-SO-001'
  AND brand = '鞍钢'
  AND amount = 8000.00;

DELETE FROM fm_invoice_receipt
WHERE id = 700780000000000001
  AND receive_no = '2026SP000001'
  AND invoice_no = '031002600011'
  AND supplier_name = '江苏钢联供应链有限公司'
  AND amount = 12000.00
  AND tax_amount = 1560.00;

DELETE FROM fm_invoice_issue
WHERE id = 700781000000000001
  AND issue_no = '2026KP000001'
  AND invoice_no = '044002600021'
  AND customer_name = '南京城建项目管理有限公司'
  AND project_name = '江北快速路一期'
  AND amount = 8000.00
  AND tax_amount = 1040.00;
