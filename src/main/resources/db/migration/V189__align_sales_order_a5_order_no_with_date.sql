-- Align the sales-order A5 top-right order number with the date left edge.
UPDATE sys_print_template
SET template_html = replace(
        template_html,
        'LODOP.ADD_PRINT_TEXT(65,570,150,20,"{{orderNo}}");',
        'LODOP.ADD_PRINT_TEXT(65,565,150,20,"{{orderNo}}");'
    ),
    version_no = GREATEST(version_no, 5) + 1,
    updated_at = now()
WHERE id = 700540000000000023
  AND bill_type = 'sales-order'
  AND template_html LIKE '%LODOP.ADD_PRINT_TEXT(65,570,150,20,"{{orderNo}}");%';
