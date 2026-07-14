-- 仅用于本地开发库的业务测试数据。保护条件不满足时，本 seed 只登记版本，不写入业务数据。
WITH dev_guard AS (
    SELECT id AS company_id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
      AND status = '正常'
      AND company_name = 'test'
      AND current_database() = 'leo'
    ORDER BY id
    LIMIT 1
),
seed_material AS (
    SELECT material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           CASE WHEN unit_price > 0 THEN unit_price ELSE 3600.00 END AS purchase_unit_price
    FROM md_material
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN brand = '中杭' AND category = '直条' THEN 0 ELSE 1 END, id
    LIMIT 1
),
seed AS (
    SELECT g.company_id,
           g.company_name,
           m.material_code,
           m.brand,
           m.category,
           m.material,
           m.spec,
           m.length,
           m.unit,
           m.quantity_unit,
           m.piece_weight_ton,
           m.pieces_per_bundle,
           m.purchase_unit_price,
           10::integer AS purchase_quantity,
           6::integer AS sales_quantity,
           (10 * m.piece_weight_ton)::numeric(18,8) AS purchase_weight,
           (6 * m.piece_weight_ton)::numeric(18,8) AS sales_weight,
           round((10 * m.piece_weight_ton * m.purchase_unit_price)::numeric, 2)::numeric(14,2) AS purchase_amount,
           round((6 * m.piece_weight_ton * (m.purchase_unit_price + 260.00))::numeric, 2)::numeric(14,2) AS sales_amount,
           (m.purchase_unit_price + 260.00)::numeric(12,2) AS sales_unit_price
    FROM dev_guard g
    CROSS JOIN seed_material m
),
customer_insert AS (
    INSERT INTO md_customer (
        id,
        customer_code,
        customer_name,
        contact_name,
        contact_phone,
        city,
        settlement_mode,
        status,
        remark,
        project_name,
        project_name_abbr,
        project_address,
        default_settlement_company_id,
        default_settlement_company_name
    )
    SELECT 900000000000000001,
           'DEV-CUST-001',
           '开发测试客户',
           '张三',
           '13800000001',
           '嘉兴',
           '月结',
           '正常',
           'Flyway dev 测试数据，可按环境清理',
           '开发测试项目',
           '开发项目',
           '嘉兴市开发区测试路 1 号',
           company_id,
           company_name
    FROM seed
    ON CONFLICT (customer_code) DO NOTHING
),
supplier_insert AS (
    INSERT INTO md_supplier (
        id,
        supplier_code,
        supplier_name,
        contact_name,
        contact_phone,
        city,
        status,
        remark
    )
    SELECT 900000000000000002,
           'DEV-SUP-001',
           '开发测试供应商',
           '李四',
           '13800000002',
           '杭州',
           '正常',
           'Flyway dev 测试数据，可按环境清理'
    FROM seed
    ON CONFLICT (supplier_code) DO NOTHING
),
project_insert AS (
    INSERT INTO md_project (
        id,
        project_code,
        project_name,
        project_name_abbr,
        project_address,
        project_manager,
        customer_code,
        status,
        remark
    )
    SELECT 900000000000000003,
           'DEV-PROJ-001',
           '开发测试项目',
           '开发项目',
           '嘉兴市开发区测试路 1 号',
           '王五',
           'DEV-CUST-001',
           '正常',
           'Flyway dev 测试数据，可按环境清理'
    FROM seed
    ON CONFLICT (project_code) DO NOTHING
),
warehouse_insert AS (
    INSERT INTO md_warehouse (
        id,
        warehouse_code,
        warehouse_name,
        warehouse_type,
        contact_name,
        contact_phone,
        address,
        status,
        remark
    )
    SELECT 900000000000000004,
           'DEV-WH-001',
           '开发测试仓库',
           '自营仓',
           '赵六',
           '13800000003',
           '嘉兴市开发区测试仓库',
           '正常',
           'Flyway dev 测试数据，可按环境清理'
    FROM seed
    ON CONFLICT (warehouse_code) DO NOTHING
),
purchase_order_insert AS (
    INSERT INTO po_purchase_order (
        id,
        order_no,
        supplier_name,
        order_date,
        buyer_name,
        total_weight,
        total_amount,
        status,
        remark,
        settlement_company_id,
        settlement_company_name
    )
    SELECT 900000000000000101,
           'DEV-PO-20260705-001',
           '开发测试供应商',
           TIMESTAMP '2026-07-05 09:00:00',
           '采购员A',
           purchase_weight,
           purchase_amount,
           '已审核',
           '开发测试采购订单',
           company_id,
           company_name
    FROM seed
    ON CONFLICT (order_no) DO NOTHING
),
purchase_order_item_insert AS (
    INSERT INTO po_purchase_order_item (
        id,
        order_id,
        line_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        batch_no,
        warehouse_name,
        actual_weight_ton,
        actual_piece_weight_ton
    )
    SELECT 900000000000000102,
           900000000000000101,
           1,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           purchase_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           purchase_weight,
           purchase_unit_price,
           purchase_amount,
           'DEV-BATCH-20260705',
           '开发测试仓库',
           purchase_weight,
           piece_weight_ton
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
purchase_inbound_insert AS (
    INSERT INTO po_purchase_inbound (
        id,
        inbound_no,
        purchase_order_no,
        supplier_name,
        warehouse_name,
        inbound_date,
        settlement_mode,
        total_weight,
        total_amount,
        status,
        remark,
        settlement_company_id,
        settlement_company_name
    )
    SELECT 900000000000000201,
           'DEV-PI-20260705-001',
           'DEV-PO-20260705-001',
           '开发测试供应商',
           '开发测试仓库',
           TIMESTAMP '2026-07-05 10:00:00',
           '月结',
           purchase_weight,
           purchase_amount,
           '完成入库',
           '开发测试采购入库，作为库存来源',
           company_id,
           company_name
    FROM seed
    ON CONFLICT (inbound_no) DO NOTHING
),
purchase_inbound_item_insert AS (
    INSERT INTO po_purchase_inbound_item (
        id,
        inbound_id,
        line_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        batch_no,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        warehouse_name,
        source_purchase_order_item_id,
        weigh_weight_ton,
        weight_adjustment_ton,
        weight_adjustment_amount,
        settlement_mode,
        settlement_company_id,
        settlement_company_name
    )
    SELECT 900000000000000202,
           900000000000000201,
           1,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           'DEV-BATCH-20260705',
           purchase_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           purchase_weight,
           purchase_unit_price,
           purchase_amount,
           '开发测试仓库',
           900000000000000102,
           purchase_weight,
           0,
           0,
           '月结',
           company_id,
           company_name
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
sales_order_insert AS (
    INSERT INTO so_sales_order (
        id,
        order_no,
        purchase_inbound_no,
        purchase_order_no,
        customer_name,
        project_name,
        delivery_date,
        sales_name,
        total_weight,
        total_amount,
        status,
        customer_code,
        project_id,
        settlement_company_id,
        settlement_company_name,
        remark
    )
    SELECT 900000000000000301,
           'DEV-SO-20260705-001',
           'DEV-PI-20260705-001',
           'DEV-PO-20260705-001',
           '开发测试客户',
           '开发测试项目',
           TIMESTAMP '2026-07-05 14:00:00',
           '销售员A',
           sales_weight,
           sales_amount,
           '完成销售',
           'DEV-CUST-001',
           900000000000000003,
           company_id,
           company_name,
           '开发测试销售订单'
    FROM seed
    ON CONFLICT (order_no) DO NOTHING
),
sales_order_item_insert AS (
    INSERT INTO so_sales_order_item (
        id,
        order_id,
        line_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        batch_no,
        source_inbound_item_id,
        warehouse_name,
        source_purchase_order_item_id,
        original_weight_ton,
        settlement_company_id,
        settlement_company_name
    )
    SELECT 900000000000000302,
           900000000000000301,
           1,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           sales_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           sales_weight,
           sales_unit_price,
           sales_amount,
           'DEV-BATCH-20260705',
           900000000000000202,
           '开发测试仓库',
           900000000000000102,
           sales_weight,
           company_id,
           company_name
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
sales_outbound_insert AS (
    INSERT INTO so_sales_outbound (
        id,
        outbound_no,
        sales_order_no,
        customer_name,
        project_name,
        warehouse_name,
        outbound_date,
        total_weight,
        total_amount,
        status,
        settlement_company_id,
        settlement_company_name,
        remark
    )
    SELECT 900000000000000401,
           'DEV-DO-20260705-001',
           'DEV-SO-20260705-001',
           '开发测试客户',
           '开发测试项目',
           '开发测试仓库',
           TIMESTAMP '2026-07-05 15:00:00',
           sales_weight,
           sales_amount,
           '已审核',
           company_id,
           company_name,
           '开发测试销售出库，库存留存 4 件'
    FROM seed
    ON CONFLICT (outbound_no) DO NOTHING
),
sales_outbound_item_insert AS (
    INSERT INTO so_sales_outbound_item (
        id,
        outbound_id,
        line_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        batch_no,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        warehouse_name,
        source_sales_order_item_id,
        settlement_company_id,
        settlement_company_name
    )
    SELECT 900000000000000402,
           900000000000000401,
           1,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           'DEV-BATCH-20260705',
           sales_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           sales_weight,
           sales_unit_price,
           sales_amount,
           '开发测试仓库',
           900000000000000302,
           company_id,
           company_name
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
supplier_statement_insert AS (
    INSERT INTO st_supplier_statement (
        id,
        statement_no,
        supplier_name,
        supplier_code,
        settlement_company_id,
        settlement_company_name,
        start_date,
        end_date,
        purchase_amount,
        payment_amount,
        closing_amount,
        status,
        version,
        remark
    )
    SELECT 900000000000000501,
           'DEV-SS-20260705-001',
           '开发测试供应商',
           'DEV-SUP-001',
           company_id,
           company_name,
           TIMESTAMP '2026-07-01 00:00:00',
           TIMESTAMP '2026-07-31 23:59:59',
           purchase_amount,
           purchase_amount,
           0,
           '已确认',
           0,
           '开发测试供应商对账单'
    FROM seed
    ON CONFLICT (statement_no) DO NOTHING
),
supplier_statement_item_insert AS (
    INSERT INTO st_supplier_statement_item (
        id,
        statement_id,
        line_no,
        source_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        batch_no,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        weigh_weight_ton,
        weight_adjustment_ton,
        weight_adjustment_amount,
        source_inbound_item_id
    )
    SELECT 900000000000000502,
           900000000000000501,
           1,
           'DEV-PI-20260705-001',
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           'DEV-BATCH-20260705',
           purchase_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           purchase_weight,
           purchase_unit_price,
           purchase_amount,
           purchase_weight,
           0,
           0,
           900000000000000202
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
customer_statement_insert AS (
    INSERT INTO st_customer_statement (
        id,
        statement_no,
        customer_name,
        project_name,
        start_date,
        end_date,
        sales_amount,
        receipt_amount,
        closing_amount,
        status,
        version,
        customer_code,
        project_id,
        settlement_company_id,
        settlement_company_name,
        remark
    )
    SELECT 900000000000000601,
           'DEV-CS-20260705-001',
           '开发测试客户',
           '开发测试项目',
           TIMESTAMP '2026-07-01 00:00:00',
           TIMESTAMP '2026-07-31 23:59:59',
           sales_amount,
           sales_amount,
           0,
           '已确认',
           0,
           'DEV-CUST-001',
           900000000000000003,
           company_id,
           company_name,
           '开发测试客户对账单'
    FROM seed
    ON CONFLICT (statement_no) DO NOTHING
),
customer_statement_item_insert AS (
    INSERT INTO st_customer_statement_item (
        id,
        statement_id,
        line_no,
        source_no,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount,
        batch_no,
        source_sales_order_item_id,
        project_id,
        customer_code
    )
    SELECT 900000000000000602,
           900000000000000601,
           1,
           'DEV-SO-20260705-001',
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           sales_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           sales_weight,
           sales_unit_price,
           sales_amount,
           'DEV-BATCH-20260705',
           900000000000000302,
           900000000000000003,
           'DEV-CUST-001'
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
payment_insert AS (
    INSERT INTO fm_payment (
        id,
        payment_no,
        business_type,
        counterparty_name,
        counterparty_code,
        source_statement_id,
        payment_date,
        pay_type,
        amount,
        status,
        operator_name,
        version,
        remark
    )
    SELECT 900000000000000701,
           'DEV-PAY-20260705-001',
           '供应商',
           '开发测试供应商',
           'DEV-SUP-001',
           900000000000000501,
           TIMESTAMP '2026-07-06 10:00:00',
           '银行转账',
           purchase_amount,
           '已审核',
           '出纳A',
           0,
           '开发测试供应商付款'
    FROM seed
    ON CONFLICT (payment_no) DO NOTHING
),
payment_allocation_insert AS (
    INSERT INTO fm_payment_allocation (
        id,
        payment_id,
        line_no,
        source_statement_id,
        allocated_amount
    )
    SELECT 900000000000000702,
           900000000000000701,
           1,
           900000000000000501,
           purchase_amount
    FROM seed
    ON CONFLICT (payment_id, source_statement_id) DO NOTHING
),
receipt_insert AS (
    INSERT INTO fm_receipt (
        id,
        receipt_no,
        customer_name,
        project_name,
        source_customer_statement_id,
        receipt_date,
        pay_type,
        amount,
        status,
        customer_code,
        project_id,
        settlement_company_id,
        settlement_company_name,
        operator_name,
        version,
        remark
    )
    SELECT 900000000000000801,
           'DEV-REC-20260705-001',
           '开发测试客户',
           '开发测试项目',
           900000000000000601,
           TIMESTAMP '2026-07-06 11:00:00',
           '银行转账',
           sales_amount,
           '已审核',
           'DEV-CUST-001',
           900000000000000003,
           company_id,
           company_name,
           '出纳A',
           0,
           '开发测试客户收款'
    FROM seed
    ON CONFLICT (receipt_no) DO NOTHING
),
receipt_allocation_insert AS (
    INSERT INTO fm_receipt_allocation (
        id,
        receipt_id,
        line_no,
        source_statement_id,
        allocated_amount
    )
    SELECT 900000000000000802,
           900000000000000801,
           1,
           900000000000000601,
           sales_amount
    FROM seed
    ON CONFLICT (receipt_id, source_statement_id) DO NOTHING
),
invoice_receipt_insert AS (
    INSERT INTO fm_invoice_receipt (
        id,
        receive_no,
        invoice_no,
        supplier_name,
        invoice_title,
        invoice_date,
        invoice_type,
        amount,
        tax_amount,
        status,
        operator_name,
        remark
    )
    SELECT 900000000000000901,
           'DEV-IR-20260705-001',
           'DEV-IN-SUP-20260705-001',
           '开发测试供应商',
           company_name,
           TIMESTAMP '2026-07-06 13:00:00',
           '增值税专用发票',
           purchase_amount,
           round((purchase_amount * 0.13)::numeric, 2)::numeric(14,2),
           '已收票',
           '财务A',
           '开发测试采购收票'
    FROM seed
    ON CONFLICT (receive_no) DO NOTHING
),
invoice_receipt_item_insert AS (
    INSERT INTO fm_invoice_receipt_item (
        id,
        receipt_id,
        line_no,
        source_no,
        source_purchase_order_item_id,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        warehouse_name,
        batch_no,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount
    )
    SELECT 900000000000000902,
           900000000000000901,
           1,
           'DEV-PO-20260705-001',
           900000000000000102,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           '开发测试仓库',
           'DEV-BATCH-20260705',
           purchase_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           purchase_weight,
           purchase_unit_price,
           purchase_amount
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
invoice_receipt_source_insert AS (
    INSERT INTO fm_invoice_receipt_source_order (
        id,
        receipt_id,
        purchase_order_id
    )
    SELECT 900000000000000903,
           900000000000000901,
           900000000000000101
    FROM seed
    ON CONFLICT (receipt_id, purchase_order_id) DO NOTHING
),
invoice_issue_insert AS (
    INSERT INTO fm_invoice_issue (
        id,
        issue_no,
        invoice_no,
        customer_name,
        project_name,
        settlement_company_id,
        settlement_company_name,
        invoice_date,
        invoice_type,
        amount,
        tax_amount,
        status,
        operator_name,
        remark
    )
    SELECT 900000000000001001,
           'DEV-II-20260705-001',
           'DEV-IN-CUS-20260705-001',
           '开发测试客户',
           '开发测试项目',
           company_id,
           company_name,
           TIMESTAMP '2026-07-06 14:00:00',
           '增值税专用发票',
           sales_amount,
           round((sales_amount * 0.13)::numeric, 2)::numeric(14,2),
           '已开票',
           '财务A',
           '开发测试销售开票'
    FROM seed
    ON CONFLICT (issue_no) DO NOTHING
),
invoice_issue_item_insert AS (
    INSERT INTO fm_invoice_issue_item (
        id,
        issue_id,
        line_no,
        source_no,
        source_sales_order_item_id,
        material_code,
        brand,
        category,
        material,
        spec,
        length,
        unit,
        warehouse_name,
        batch_no,
        quantity,
        quantity_unit,
        piece_weight_ton,
        pieces_per_bundle,
        weight_ton,
        unit_price,
        amount
    )
    SELECT 900000000000001002,
           900000000000001001,
           1,
           'DEV-SO-20260705-001',
           900000000000000302,
           material_code,
           brand,
           category,
           material,
           spec,
           length,
           unit,
           '开发测试仓库',
           'DEV-BATCH-20260705',
           sales_quantity,
           quantity_unit,
           piece_weight_ton,
           pieces_per_bundle,
           sales_weight,
           sales_unit_price,
           sales_amount
    FROM seed
    ON CONFLICT (id) DO NOTHING
),
invoice_issue_source_insert AS (
    INSERT INTO fm_invoice_issue_source_order (
        id,
        issue_id,
        sales_order_id
    )
    SELECT 900000000000001003,
           900000000000001001,
           900000000000000301
    FROM seed
    ON CONFLICT (issue_id, sales_order_id) DO NOTHING
)
SELECT 1;
