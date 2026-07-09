UPDATE sys_print_template
SET source_checksum = CASE template_code
    WHEN 'TPL_322775358715723776' THEN '97b6842300b9502c47a8572b61401bf638d65e1576b49e5bf45ad373a36dd28c'
    WHEN 'SALES_ORDER_YINGJIE_A4_REMARK_PDF' THEN '226e61289d6d85b4550bb22f4be1c84250df9679f94adc39adffed1cabe3e8f9'
    WHEN 'DEFAULT_LOGISTICS_PDF_FORM' THEN '9e7c5f3df80a9935012d11d5b434eb8cd0189cac4ef0f4faa6ee81720dc63b76'
    WHEN 'DEFAULT_PURCHASE_ORDER_PDF_FORM' THEN '77bd9df0e299d97ea80acbad45d8f7b7cf5ff826a169b36e962028b52798097f'
    WHEN 'DEFAULT_REPORT_PDF_FORM' THEN '6fff50b7cdafd829048992a221141a38595321995ce64d1d2d7359f35be02e4c'
    WHEN 'DEFAULT_SALES_ORDER_PDF_FORM' THEN '70cf3131af479685a70827172ca98f9c2168714cda71a6d7c18a5d977d0519f7'
    WHEN 'DEFAULT_CUSTOMER_STATEMENT_PDF_FORM' THEN '4ceabafc2861fc34d9910d3912ffd2499e05b2636fd16ed7f036bf25d1541c3b'
    WHEN 'DEFAULT_PURCHASE_INBOUND_PDF_FORM' THEN '6995ba5e7ab349d41df9e6262a6567c19c6f148e086006b4008c21637a13e393'
    WHEN 'DEFAULT_PURCHASE_CONTRACT_PDF_FORM' THEN '877b117f86cf46bc59362ccb07ff4bce856de5c1ce5e680f36c64349477f2338'
    WHEN 'DEFAULT_SALES_OUTBOUND_PDF_FORM' THEN 'cb73a5423486c940f34e95de0fd3ead0898b71d507f43a4f4c369e2357b18b7c'
    WHEN 'DEFAULT_SALES_CONTRACT_PDF_FORM' THEN '55faa497940eb28c99304f3384a618f86929659d06f79e6645a188cad82bd4c6'
    WHEN 'DEFAULT_SUPPLIER_STATEMENT_PDF_FORM' THEN 'bb9e7627469a1303d6d1ea314883c8f92e40835134c235bb7c32c2c7812dc54c'
    WHEN 'DEFAULT_FREIGHT_STATEMENT_PDF_FORM' THEN 'de9ec7c01bc06cb2e4c589e42de07a2d049f793ea27af0e27a637c31ebafa459'
    ELSE source_checksum
END,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code IN (
    'TPL_322775358715723776',
    'SALES_ORDER_YINGJIE_A4_REMARK_PDF',
    'DEFAULT_LOGISTICS_PDF_FORM',
    'DEFAULT_PURCHASE_ORDER_PDF_FORM',
    'DEFAULT_REPORT_PDF_FORM',
    'DEFAULT_SALES_ORDER_PDF_FORM',
    'DEFAULT_CUSTOMER_STATEMENT_PDF_FORM',
    'DEFAULT_PURCHASE_INBOUND_PDF_FORM',
    'DEFAULT_PURCHASE_CONTRACT_PDF_FORM',
    'DEFAULT_SALES_OUTBOUND_PDF_FORM',
    'DEFAULT_SALES_CONTRACT_PDF_FORM',
    'DEFAULT_SUPPLIER_STATEMENT_PDF_FORM',
    'DEFAULT_FREIGHT_STATEMENT_PDF_FORM'
)
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;

WITH cleaned AS (
    SELECT
        id,
        template_html::jsonb
            #- '{fields,billNo,minimumFontSize}'
            #- '{fields,projectName,minimumFontSize}'
            #- '{fields,projectAddress,minimumFontSize}'
            #- '{clauses,minimumFontSize}' AS template_json
    FROM sys_print_template
    WHERE template_code = 'TPL_333661633949728768'
      AND template_type = 'PDF_FORM'
      AND sync_mode = 'MANUAL'
      AND deleted_flag = FALSE
      AND template_html IS NOT NULL
)
UPDATE sys_print_template template
SET template_html = cleaned.template_json::text,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    version_no = template.version_no + 1
FROM cleaned
WHERE template.id = cleaned.id
  AND template.template_html IS DISTINCT FROM cleaned.template_json::text;
