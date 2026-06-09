UPDATE sys_print_template
SET template_html = jsonb_set(template_html::jsonb, '{static,0,fontSize}', '16'::jsonb, false)::text,
    version_no = GREATEST(version_no, 3),
    updated_at = now()
WHERE bill_type = 'sales-order'
  AND template_type = 'PDF_FORM'
  AND template_code = 'SALES_ORDER_YINGJIE_A4_REMARK_PDF'
  AND jsonb_path_query_first(template_html::jsonb, '$.static[0].text') #>> '{}' = '嘉兴颖捷建材有限公司（供货单）';
