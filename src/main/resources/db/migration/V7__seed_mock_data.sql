INSERT INTO sys_bootstrap_marker (id, marker_code, marker_name)
VALUES (700000000000000001, 'MOCK_DATA_V7', 'Flyway mock seed data v7')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user (
    id, login_name, password_hash, user_name, mobile, role_name, data_scope, permission_summary, status, remark
) VALUES
    (700100000000000001, 'buyer01', '$2a$10$SMZjHfcmw5JUsGFL9UFTPuDc6yi.Ds051AJqa.a0QH.J0uDJwsCg6', '采购专员-张凯', '13810000001', 'PURCHASER', 'ALL', '采购订单、采购入库、供应商对账', 'NORMAL', 'Mock 用户'),
    (700100000000000002, 'sales01', '$2a$10$SMZjHfcmw5JUsGFL9UFTPuDc6yi.Ds051AJqa.a0QH.J0uDJwsCg6', '销售经理-李然', '13810000002', 'SALES_MANAGER', 'ALL', '销售订单、销售出库、客户对账', 'NORMAL', 'Mock 用户'),
    (700100000000000003, 'finance01', '$2a$10$SMZjHfcmw5JUsGFL9UFTPuDc6yi.Ds051AJqa.a0QH.J0uDJwsCg6', '财务主管-周敏', '13810000003', 'FINANCE_MANAGER', 'ALL', '收付款、往来对账', 'NORMAL', 'Mock 用户'),
    (700100000000000004, 'ops01', '$2a$10$SMZjHfcmw5JUsGFL9UFTPuDc6yi.Ds051AJqa.a0QH.J0uDJwsCg6', '运维支持-何川', '13810000004', 'OPS_SUPPORT', 'ALL', '系统设置、运维支持', 'NORMAL', 'Mock 用户')
ON CONFLICT (login_name) DO NOTHING;

INSERT INTO md_material (
    id, material_code, brand, material, category, spec, length, unit, piece_weight_ton, pieces_per_bundle, unit_price, remark
) VALUES
    (700200000000000001, 'RB-H400-18-12', '沙钢', 'HRB400', '螺纹钢', '18', '12米', '吨', 2.300, 91, 3480.00, '主销规格'),
    (700200000000000002, 'RB-H400-20-9', '永钢', 'HRB400', '螺纹钢', '20', '9米', '吨', 2.468, 84, 3460.00, '工程短尺'),
    (700200000000000003, 'RB-H500-25-12', '中天', 'HRB500', '螺纹钢', '25', '12米', '吨', 3.820, 60, 3620.00, '高强主材'),
    (700200000000000004, 'PX-H400-8', '沙钢', 'HRB400', '盘螺', '8', NULL, '吨', 1.950, 1, 3570.00, '盘螺按默认件重结算'),
    (700200000000000005, 'XA-Q235-6.5', '日照', 'Q235', '线材', '6.5', NULL, '吨', 2.100, 1, 3410.00, '线材按默认件重结算')
ON CONFLICT (material_code) DO NOTHING;

INSERT INTO md_supplier (
    id, supplier_code, supplier_name, contact_name, contact_phone, city, status, remark
) VALUES
    (700210000000000001, 'SUP0001', '江苏钢联供应链有限公司', '王建国', '13820000001', '无锡', '正常', '长期合作供应商'),
    (700210000000000002, 'SUP0002', '上海闵盛金属材料有限公司', '赵新', '13820000002', '上海', '正常', '盘螺资源稳定'),
    (700210000000000003, 'SUP0003', '杭州联晟物资有限公司', '钱涛', '13820000003', '杭州', '正常', '线材补充供应商')
ON CONFLICT (supplier_code) DO NOTHING;

INSERT INTO md_customer (
    id, customer_code, customer_name, contact_name, contact_phone, city, settlement_mode, status, remark
) VALUES
    (700220000000000001, 'CUS0001', '南京城建项目管理有限公司', '陈浩', '13830000001', '南京', '月结30天', '正常', '多个项目并行'),
    (700220000000000002, 'CUS0002', '苏州轨交工程有限公司', '顾峰', '13830000002', '苏州', '现款', '正常', '重点直供客户'),
    (700220000000000003, 'CUS0003', '合肥高新建设集团', '杨青', '13830000003', '合肥', '月结15天', '正常', '园区项目客户')
ON CONFLICT (customer_code) DO NOTHING;

INSERT INTO md_carrier (
    id, carrier_code, carrier_name, contact_name, contact_phone, vehicle_type, price_mode, status, remark
) VALUES
    (700230000000000001, 'CAR0001', '江苏快运物流有限公司', '刘彬', '13840000001', '17.5米平板', '按吨', '正常', '主力承运商'),
    (700230000000000002, 'CAR0002', '沪宁联运车队', '马超', '13840000002', '13米高栏', '按车', '正常', '短驳补充运力')
ON CONFLICT (carrier_code) DO NOTHING;

INSERT INTO md_warehouse (
    id, warehouse_code, warehouse_name, warehouse_type, contact_name, contact_phone, address, status, remark
) VALUES
    (700240000000000001, 'WH0001', '南京滨江一号库', '自营库', '孙亮', '13850000001', '南京市江宁区滨江开发区钢材路88号', '正常', '主出入库仓'),
    (700240000000000002, 'WH0002', '苏州园区周转库', '合作库', '蒋磊', '13850000002', '苏州市工业园区东宏路66号', '正常', '项目周转仓')
ON CONFLICT (warehouse_code) DO NOTHING;

INSERT INTO po_purchase_order (
    id, order_no, supplier_name, order_date, buyer_name, total_weight, total_amount, status, remark
) VALUES
    (700300000000000001, '2026PO000001', '江苏钢联供应链有限公司', DATE '2026-04-02', '采购专员-张凯', 8.588, 30064.96, '已审核', '南京项目首批螺纹钢采购'),
    (700300000000000002, '2026PO000002', '上海闵盛金属材料有限公司', DATE '2026-04-05', '采购专员-张凯', 4.050, 14458.50, '已审核', '补充盘螺和线材')
ON CONFLICT (order_no) DO NOTHING;

INSERT INTO po_purchase_order_item (
    id, order_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700301000000000001, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 2, 2.300, 91, 4.600, 3480.00, 16008.00),
    (700301000000000002, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000001'), 2, 'RB-H500-25-12', '中天', '螺纹钢', 'HRB500', '25', '12米', '吨', 1, 3.820, 60, 3.820, 3620.00, 13828.40),
    (700301000000000003, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000002'), 1, 'PX-H400-8', '沙钢', '盘螺', 'HRB400', '8', NULL, '吨', 1, 1.950, 1, 1.950, 3570.00, 6961.50),
    (700301000000000004, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000002'), 2, 'XA-Q235-6.5', '日照', '线材', 'Q235', '6.5', NULL, '吨', 1, 2.100, 1, 2.100, 3410.00, 7161.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO po_purchase_inbound (
    id, inbound_no, purchase_order_no, supplier_name, warehouse_name, inbound_date, settlement_mode, total_weight, total_amount, status, remark
) VALUES
    (700310000000000001, '2026PI000001', '2026PO000001', '江苏钢联供应链有限公司', '南京滨江一号库', DATE '2026-04-04', '理算', 8.588, 30064.96, '完成入库', '全单到货'),
    (700310000000000002, '2026PI000002', '2026PO000002', '上海闵盛金属材料有限公司', '南京滨江一号库', DATE '2026-04-07', '过磅', 4.050, 14458.50, '完成入库', '盘螺线材到货')
ON CONFLICT (inbound_no) DO NOTHING;

INSERT INTO po_purchase_inbound_item (
    id, inbound_id, line_no, material_code, brand, category, material, spec, length, unit, batch_no, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700311000000000001, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 'BATCH-240404-01', 2, 2.300, 91, 4.600, 3480.00, 16008.00),
    (700311000000000002, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000001'), 2, 'RB-H500-25-12', '中天', '螺纹钢', 'HRB500', '25', '12米', '吨', 'BATCH-240404-02', 1, 3.820, 60, 3.820, 3620.00, 13828.40),
    (700311000000000003, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000002'), 1, 'PX-H400-8', '沙钢', '盘螺', 'HRB400', '8', NULL, '吨', 'BATCH-240407-01', 1, 1.950, 1, 1.950, 3570.00, 6961.50),
    (700311000000000004, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000002'), 2, 'XA-Q235-6.5', '日照', '线材', 'Q235', '6.5', NULL, '吨', 'BATCH-240407-02', 1, 2.100, 1, 2.100, 3410.00, 7161.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO so_sales_order (
    id, order_no, purchase_inbound_no, customer_name, project_name, order_date, sales_name, total_weight, total_amount, status, remark
) VALUES
    (700320000000000001, '2026SO000001', '2026PI000001', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-08', '销售经理-李然', 4.600, 16790.00, '已审核', '18螺纹钢发往江北项目'),
    (700320000000000002, '2026SO000002', '2026PI000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', DATE '2026-04-09', '销售经理-李然', 4.050, 15162.00, '已审核', '盘螺线材配套发货')
ON CONFLICT (order_no) DO NOTHING;

INSERT INTO so_sales_order_item (
    id, order_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700321000000000001, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 2, 2.300, 91, 4.600, 3650.00, 16790.00),
    (700321000000000002, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000002'), 1, 'PX-H400-8', '沙钢', '盘螺', 'HRB400', '8', NULL, '吨', 1, 1.950, 1, 1.950, 3740.00, 7293.00),
    (700321000000000003, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000002'), 2, 'XA-Q235-6.5', '日照', '线材', 'Q235', '6.5', NULL, '吨', 1, 2.100, 1, 2.100, 3747.14, 7869.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO so_sales_outbound (
    id, outbound_no, sales_order_no, customer_name, project_name, warehouse_name, outbound_date, total_weight, total_amount, status, remark
) VALUES
    (700330000000000001, '2026OB000001', '2026SO000001', '南京城建项目管理有限公司', '江北快速路一期', '南京滨江一号库', DATE '2026-04-10', 4.600, 16790.00, '已完成', '整单出库'),
    (700330000000000002, '2026OB000002', '2026SO000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', '南京滨江一号库', DATE '2026-04-11', 4.050, 15162.00, '已完成', '盘螺线材组合出库')
ON CONFLICT (outbound_no) DO NOTHING;

INSERT INTO so_sales_outbound_item (
    id, outbound_id, line_no, material_code, brand, category, material, spec, length, unit, batch_no, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700331000000000001, (SELECT id FROM so_sales_outbound WHERE outbound_no = '2026OB000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 'BATCH-240404-01', 2, 2.300, 91, 4.600, 3650.00, 16790.00),
    (700331000000000002, (SELECT id FROM so_sales_outbound WHERE outbound_no = '2026OB000002'), 1, 'PX-H400-8', '沙钢', '盘螺', 'HRB400', '8', NULL, '吨', 'BATCH-240407-01', 1, 1.950, 1, 1.950, 3740.00, 7293.00),
    (700331000000000003, (SELECT id FROM so_sales_outbound WHERE outbound_no = '2026OB000002'), 2, 'XA-Q235-6.5', '日照', '线材', 'Q235', '6.5', NULL, '吨', 'BATCH-240407-02', 1, 2.100, 1, 2.100, 3747.14, 7869.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lg_freight_bill (
    id, bill_no, outbound_no, carrier_name, customer_name, project_name, bill_time, unit_price, total_weight, total_freight, status, delivery_status, remark
) VALUES
    (700340000000000001, '2026FB000001', '2026OB000001', '江苏快运物流有限公司', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-10', 85.00, 4.600, 391.00, '已审核', '已送达', '南京市内送货'),
    (700340000000000002, '2026FB000002', '2026OB000002', '沪宁联运车队', '苏州轨交工程有限公司', '轨交5号线钢筋标段', DATE '2026-04-11', 92.00, 4.050, 372.60, '已审核', '未送达', '次日上午到场')
ON CONFLICT (bill_no) DO NOTHING;

INSERT INTO lg_freight_bill_item (
    id, bill_id, line_no, source_no, customer_name, project_name, material_code, material_name, brand, category, material, spec, length, quantity, piece_weight_ton, pieces_per_bundle, batch_no, weight_ton, warehouse_name
) VALUES
    (700341000000000001, (SELECT id FROM lg_freight_bill WHERE bill_no = '2026FB000001'), 1, '2026OB000001', '南京城建项目管理有限公司', '江北快速路一期', 'RB-H400-18-12', '螺纹钢18', '沙钢', '螺纹钢', 'HRB400', '18', '12米', 2, 2.300, 91, 'BATCH-240404-01', 4.600, '南京滨江一号库'),
    (700341000000000002, (SELECT id FROM lg_freight_bill WHERE bill_no = '2026FB000002'), 1, '2026OB000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', 'PX-H400-8', '盘螺8', '沙钢', '盘螺', 'HRB400', '8', NULL, 1, 1.950, 1, 'BATCH-240407-01', 1.950, '南京滨江一号库'),
    (700341000000000003, (SELECT id FROM lg_freight_bill WHERE bill_no = '2026FB000002'), 2, '2026OB000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', 'XA-Q235-6.5', '线材6.5', '日照', '线材', 'Q235', '6.5', NULL, 1, 2.100, 1, 'BATCH-240407-02', 2.100, '南京滨江一号库')
ON CONFLICT (id) DO NOTHING;

INSERT INTO st_supplier_statement (
    id, statement_no, source_inbound_nos, supplier_name, start_date, end_date, purchase_amount, payment_amount, closing_amount, status, remark
) VALUES
    (700350000000000001, '2026SS000001', '2026PI000001', '江苏钢联供应链有限公司', DATE '2026-04-01', DATE '2026-04-15', 30064.96, 12000.00, 18064.96, '已确认', '首批到货对账'),
    (700350000000000002, '2026SS000002', '2026PI000002', '上海闵盛金属材料有限公司', DATE '2026-04-01', DATE '2026-04-15', 14458.50, 0.00, 14458.50, '待确认', '待供应商回签')
ON CONFLICT (statement_no) DO NOTHING;

INSERT INTO st_customer_statement (
    id, statement_no, source_order_nos, customer_name, project_name, start_date, end_date, sales_amount, receipt_amount, closing_amount, status, remark
) VALUES
    (700360000000000001, '2026CS000001', '2026SO000001', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-01', DATE '2026-04-15', 16790.00, 8000.00, 8790.00, '已确认', '项目进度款已收部分'),
    (700360000000000002, '2026CS000002', '2026SO000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', DATE '2026-04-01', DATE '2026-04-15', 15162.00, 0.00, 15162.00, '待确认', '待客户确认结算')
ON CONFLICT (statement_no) DO NOTHING;

INSERT INTO st_freight_statement (
    id, statement_no, source_bill_nos, carrier_name, start_date, end_date, total_weight, total_freight, paid_amount, unpaid_amount, status, sign_status, attachment, remark
) VALUES
    (700370000000000001, '2026FS000001', '2026FB000001,2026FB000002', '江苏快运物流有限公司', DATE '2026-04-01', DATE '2026-04-15', 4.600, 391.00, 200.00, 191.00, '已确认', '已签署', 'freight-statement-202604.pdf', '本期已完成一车对账'),
    (700370000000000002, '2026FS000002', '2026FB000002', '沪宁联运车队', DATE '2026-04-01', DATE '2026-04-15', 4.050, 372.60, 0.00, 372.60, '待确认', '未签署', NULL, '待送达后确认')
ON CONFLICT (statement_no) DO NOTHING;

INSERT INTO st_freight_statement_item (
    id, statement_id, line_no, source_no, customer_name, project_name, material_code, material_name, brand, category, material, spec, length, quantity, piece_weight_ton, pieces_per_bundle, batch_no, weight_ton, warehouse_name
) VALUES
    (700371000000000001, (SELECT id FROM st_freight_statement WHERE statement_no = '2026FS000001'), 1, '2026OB000001', '南京城建项目管理有限公司', '江北快速路一期', 'RB-H400-18-12', '螺纹钢18', '沙钢', '螺纹钢', 'HRB400', '18', '12米', 2, 2.300, 91, 'BATCH-240404-01', 4.600, '南京滨江一号库'),
    (700371000000000002, (SELECT id FROM st_freight_statement WHERE statement_no = '2026FS000002'), 1, '2026OB000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', 'PX-H400-8', '盘螺8', '沙钢', '盘螺', 'HRB400', '8', NULL, 1, 1.950, 1, 'BATCH-240407-01', 1.950, '南京滨江一号库'),
    (700371000000000003, (SELECT id FROM st_freight_statement WHERE statement_no = '2026FS000002'), 2, '2026OB000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', 'XA-Q235-6.5', '线材6.5', '日照', '线材', 'Q235', '6.5', NULL, 1, 2.100, 1, 'BATCH-240407-02', 2.100, '南京滨江一号库')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ct_purchase_contract (
    id, contract_no, supplier_name, sign_date, effective_date, expire_date, buyer_name, total_weight, total_amount, status, remark
) VALUES
    (700380000000000001, '2026PC000001', '江苏钢联供应链有限公司', DATE '2026-04-01', DATE '2026-04-01', DATE '2026-06-30', '采购专员-张凯', 20.000, 70000.00, '执行中', '二季度框架采购合同')
ON CONFLICT (contract_no) DO NOTHING;

INSERT INTO ct_purchase_contract_item (
    id, contract_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700381000000000001, (SELECT id FROM ct_purchase_contract WHERE contract_no = '2026PC000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 4, 2.300, 91, 9.200, 3480.00, 32016.00),
    (700381000000000002, (SELECT id FROM ct_purchase_contract WHERE contract_no = '2026PC000001'), 2, 'RB-H500-25-12', '中天', '螺纹钢', 'HRB500', '25', '12米', '吨', 2, 3.820, 60, 7.640, 3662.83, 27984.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ct_sales_contract (
    id, contract_no, customer_name, project_name, sign_date, effective_date, expire_date, sales_name, total_weight, total_amount, status, remark
) VALUES
    (700390000000000001, '2026SC000001', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-03', DATE '2026-04-03', DATE '2026-07-31', '销售经理-李然', 15.000, 57000.00, '执行中', '项目供货销售合同')
ON CONFLICT (contract_no) DO NOTHING;

INSERT INTO ct_sales_contract_item (
    id, contract_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700391000000000001, (SELECT id FROM ct_sales_contract WHERE contract_no = '2026SC000001'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 4, 2.300, 91, 9.200, 3650.00, 33580.00),
    (700391000000000002, (SELECT id FROM ct_sales_contract WHERE contract_no = '2026SC000001'), 2, 'PX-H400-8', '沙钢', '盘螺', 'HRB400', '8', NULL, '吨', 1, 1.950, 1, 1.950, 3723.08, 7260.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fm_receipt (
    id, receipt_no, customer_name, project_name, receipt_date, pay_type, amount, status, operator_name, remark
) VALUES
    (700400000000000001, '2026RC000001', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-12', '银行转账', 8000.00, '已收款', '财务主管-周敏', '客户首笔回款'),
    (700400000000000002, '2026RC000002', '苏州轨交工程有限公司', '轨交5号线钢筋标段', DATE '2026-04-18', '承兑汇票', 5000.00, '部分结清', '财务主管-周敏', '票据待到期')
ON CONFLICT (receipt_no) DO NOTHING;

INSERT INTO fm_payment (
    id, payment_no, business_type, counterparty_name, payment_date, pay_type, amount, status, operator_name, remark
) VALUES
    (700410000000000001, '2026PM000001', '供应商付款', '江苏钢联供应链有限公司', DATE '2026-04-13', '银行转账', 12000.00, '已付款', '财务主管-周敏', '支付首批货款'),
    (700410000000000002, '2026PM000002', '物流付款', '江苏快运物流有限公司', DATE '2026-04-16', '银行转账', 200.00, '已付款', '财务主管-周敏', '支付部分运费')
ON CONFLICT (payment_no) DO NOTHING;

INSERT INTO sys_no_rule (
    id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark
) VALUES
    (700500000000000001, 'RULE_PO', '采购订单单号规则', '采购订单', 'PO', 'yyyy', 6, 'YEARLY', '2026PO000001', '正常', '每年重置'),
    (700500000000000002, 'RULE_PI', '采购入库单号规则', '采购入库', 'PI', 'yyyy', 6, 'YEARLY', '2026PI000001', '正常', '每年重置'),
    (700500000000000003, 'RULE_SO', '销售订单单号规则', '销售订单', 'SO', 'yyyy', 6, 'YEARLY', '2026SO000001', '正常', '每年重置'),
    (700500000000000004, 'RULE_OB', '销售出库单号规则', '销售出库', 'OB', 'yyyy', 6, 'YEARLY', '2026OB000001', '正常', '每年重置'),
    (700500000000000005, 'RULE_FB', '物流单单号规则', '物流单', 'FB', 'yyyy', 6, 'YEARLY', '2026FB000001', '正常', '每年重置'),
    (700500000000000006, 'RULE_SS', '供应商对账单规则', '供应商对账单', 'SS', 'yyyy', 6, 'YEARLY', '2026SS000001', '正常', '每年重置'),
    (700500000000000007, 'RULE_CS', '客户对账单规则', '客户对账单', 'CS', 'yyyy', 6, 'YEARLY', '2026CS000001', '正常', '每年重置'),
    (700500000000000008, 'RULE_FS', '物流对账单规则', '物流对账单', 'FS', 'yyyy', 6, 'YEARLY', '2026FS000001', '正常', '每年重置')
ON CONFLICT (setting_code) DO NOTHING;

INSERT INTO sys_permission (
    id, permission_code, permission_name, module_name, permission_type, action_name, scope_name, resource_key, status, remark
) VALUES
    (700510000000000001, 'PURCHASE_ORDER_VIEW', '采购订单查看', '采购管理', '菜单', '查看', '全部', '/purchase-orders', '正常', 'Mock 权限'),
    (700510000000000002, 'SALES_ORDER_EDIT', '销售订单编辑', '销售管理', '按钮', '编辑', '全部', '/sales-orders:edit', '正常', 'Mock 权限'),
    (700510000000000003, 'FREIGHT_STATEMENT_AUDIT', '物流对账审核', '对账管理', '按钮', '审核', '全部', '/freight-statements:audit', '正常', 'Mock 权限'),
    (700510000000000004, 'SYSTEM_SETTING_EDIT', '系统设置维护', '系统设置', '菜单', '维护', '全部', '/general-settings', '正常', 'Mock 权限'),
    (700510000000000005, 'OPS_TICKET_HANDLE', '运维工单处理', '运维支持', '按钮', '处理', '全部', '/ops-support:handle', '正常', 'Mock 权限')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role (
    id, role_code, role_name, role_type, data_scope, permission_codes, permission_count, permission_summary, user_count, status, remark
) VALUES
    (700520000000000001, 'ADMIN', '系统管理员', '平台角色', '全部', 'PURCHASE_ORDER_VIEW,SALES_ORDER_EDIT,FREIGHT_STATEMENT_AUDIT,SYSTEM_SETTING_EDIT,OPS_TICKET_HANDLE', 5, '全模块权限', 1, '正常', '系统内置角色'),
    (700520000000000002, 'PURCHASER', '采购专员', '业务角色', '全部', 'PURCHASE_ORDER_VIEW', 1, '采购订单、入库、供应商对账', 1, '正常', '采购岗位'),
    (700520000000000003, 'SALES_MANAGER', '销售经理', '业务角色', '全部', 'SALES_ORDER_EDIT,FREIGHT_STATEMENT_AUDIT', 2, '销售订单、出库、客户与物流对账', 1, '正常', '销售岗位'),
    (700520000000000004, 'FINANCE_MANAGER', '财务主管', '业务角色', '全部', 'SYSTEM_SETTING_EDIT', 1, '收付款及对账确认', 1, '正常', '财务岗位')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO ops_ticket (
    id, ticket_no, issue_type, priority_level, submitter_name, handler_name, submit_date, status, remark
) VALUES
    (700530000000000001, '2026OT000001', '登录异常', '高', '财务主管-周敏', '运维支持-何川', DATE '2026-04-14', '处理中', '财务人员反馈浏览器缓存导致重复登录'),
    (700530000000000002, '2026OT000002', '打印模板调整', '中', '销售经理-李然', '运维支持-何川', DATE '2026-04-15', '待确认', '出库单打印模板需增加项目名称')
ON CONFLICT (ticket_no) DO NOTHING;
