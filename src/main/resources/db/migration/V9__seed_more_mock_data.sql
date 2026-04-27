-- 更多物料数据
INSERT INTO md_material (
    id, material_code, brand, material, category, spec, length, unit, piece_weight_ton, pieces_per_bundle, unit_price, remark
) VALUES
    (700600000000000001, 'RB-H400-22-12', '沙钢', 'HRB400', '螺纹钢', '22', '12米', '吨', 2.980, 74, 3500.00, '常用规格'),
    (700600000000000002, 'RB-H400-16-9', '永钢', 'HRB400', '螺纹钢', '16', '9米', '吨', 1.580, 112, 3450.00, '短尺螺纹钢'),
    (700600000000000003, 'RB-H500-20-12', '中天', 'HRB500', '螺纹钢', '20', '12米', '吨', 2.470, 84, 3650.00, '高强螺纹钢'),
    (700600000000000004, 'PX-H400-10', '沙钢', 'HRB400', '盘螺', '10', NULL, '吨', 1.950, 1, 3580.00, '盘螺10mm'),
    (700600000000000005, 'XA-Q235-8', '日照', 'Q235', '线材', '8', NULL, '吨', 2.100, 1, 3420.00, '线材8mm'),
    (700600000000000006, 'XA-Q195-6.5', '迁安', 'Q195', '线材', '6.5', NULL, '吨', 2.100, 1, 3380.00, '低碳线材'),
    (700600000000000007, 'H-H300-300', '莱钢', 'Q235B', 'H型钢', '300*300', '12米', '吨', 1.200, 1, 3850.00, '热轧H型钢'),
    (700600000000000008, 'C-C200-75', '马钢', 'Q235B', '槽钢', '200*75', '12米', '吨', 1.000, 1, 3750.00, '普通槽钢'),
    (700600000000000009, 'L-L100-10', '唐钢', 'Q235B', '角钢', '100*100*10', '12米', '吨', 0.900, 1, 3700.00, '等边角钢'),
    (700600000000000010, 'P-P6-1500', '鞍钢', 'Q235B', '钢板', '6mm', '1500*6000', '吨', 1.000, 1, 3900.00, '热轧钢板')
ON CONFLICT (material_code) DO NOTHING;

-- 更多供应商
INSERT INTO md_supplier (
    id, supplier_code, supplier_name, contact_name, contact_phone, city, status, remark
) VALUES
    (700610000000000001, 'SUP0004', '安徽马钢物资贸易有限公司', '孙伟', '13820000004', '马鞍山', '正常', 'H型钢主力供应商'),
    (700610000000000002, 'SUP0005', '唐山钢铁集团销售有限公司', '李强', '13820000005', '唐山', '正常', '型材供应商'),
    (700610000000000003, 'SUP0006', '鞍钢股份有限公司营销中心', '张明', '13820000006', '鞍山', '正常', '板材供应商')
ON CONFLICT (supplier_code) DO NOTHING;

-- 更多客户
INSERT INTO md_customer (
    id, customer_code, customer_name, contact_name, contact_phone, city, settlement_mode, status, remark
) VALUES
    (700620000000000001, 'CUS0004', '上海建工集团股份有限公司', '王磊', '13830000004', '上海', '月结30天', '正常', '大型建筑集团'),
    (700620000000000002, 'CUS0005', '浙江绿城房地产集团有限公司', '刘芳', '13830000005', '杭州', '月结15天', '正常', '房地产开发商'),
    (700620000000000003, 'CUS0006', '安徽建工集团有限公司', '赵刚', '13830000006', '合肥', '月结30天', '正常', '省级建筑企业'),
    (700620000000000004, 'CUS0007', '江苏中南建筑产业集团有限公司', '周涛', '13830000007', '南通', '现款', '正常', '民营建筑龙头')
ON CONFLICT (customer_code) DO NOTHING;

-- 更多承运商
INSERT INTO md_carrier (
    id, carrier_code, carrier_name, contact_name, contact_phone, vehicle_type, price_mode, status, remark
) VALUES
    (700630000000000001, 'CAR0003', '南京恒通运输有限公司', '陈军', '13840000003', '9.6米高栏', '按吨', '正常', '市内短驳'),
    (700630000000000002, 'CAR0004', '安徽皖通物流有限公司', '吴斌', '13840000004', '17.5米平板', '按车', '正常', '跨省专线')
ON CONFLICT (carrier_code) DO NOTHING;

-- 更多仓库
INSERT INTO md_warehouse (
    id, warehouse_code, warehouse_name, warehouse_type, contact_name, contact_phone, address, status, remark
) VALUES
    (700640000000000001, 'WH0003', '马鞍山港口仓库', '港口库', '郑浩', '13850000003', '马鞍山市花山区港口路168号', '正常', '钢材专用码头库'),
    (700640000000000002, 'WH0004', '合肥瑶海钢材市场', '市场库', '黄涛', '13850000004', '合肥市瑶海区长江东路钢材市场A区', '正常', '市场周转仓')
ON CONFLICT (warehouse_code) DO NOTHING;

-- 更多采购订单
INSERT INTO po_purchase_order (
    id, order_no, supplier_name, order_date, buyer_name, total_weight, total_amount, status, remark
) VALUES
    (700650000000000001, '2026PO000003', '安徽马钢物资贸易有限公司', DATE '2026-04-10', '采购专员-张凯', 12.000, 46200.00, '已审核', 'H型钢采购'),
    (700650000000000002, '2026PO000004', '唐山钢铁集团销售有限公司', DATE '2026-04-12', '采购专员-张凯', 8.500, 31450.00, '待审核', '角钢槽钢补充'),
    (700650000000000003, '2026PO000005', '江苏钢联供应链有限公司', DATE '2026-04-15', '采购专员-张凯', 15.000, 52500.00, '已审核', '螺纹钢大批量采购')
ON CONFLICT (order_no) DO NOTHING;

-- 采购订单明细
INSERT INTO po_purchase_order_item (
    id, order_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700651000000000001, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000003'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 10, 1.200, 1, 12.000, 3850.00, 46200.00),
    (700651000000000002, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000004'), 1, 'L-L100-10', '唐钢', '角钢', 'Q235B', '100*100*10', '12米', '吨', 5, 0.900, 1, 4.500, 3700.00, 16650.00),
    (700651000000000003, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000004'), 2, 'C-C200-75', '马钢', '槽钢', 'Q235B', '200*75', '12米', '吨', 4, 1.000, 1, 4.000, 3750.00, 15000.00),
    (700651000000000004, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000005'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 5, 2.300, 91, 11.500, 3480.00, 40020.00),
    (700651000000000005, (SELECT id FROM po_purchase_order WHERE order_no = '2026PO000005'), 2, 'RB-H400-22-12', '沙钢', '螺纹钢', 'HRB400', '22', '12米', '吨', 1, 2.980, 74, 2.980, 3500.00, 10430.00)
ON CONFLICT (id) DO NOTHING;

-- 更多采购入库
INSERT INTO po_purchase_inbound (
    id, inbound_no, purchase_order_no, supplier_name, warehouse_name, inbound_date, settlement_mode, total_weight, total_amount, status, remark
) VALUES
    (700660000000000001, '2026PI000003', '2026PO000003', '安徽马钢物资贸易有限公司', '马鞍山港口仓库', DATE '2026-04-14', '过磅', 12.000, 46200.00, '完成入库', 'H型钢到货'),
    (700660000000000002, '2026PI000004', '2026PO000005', '江苏钢联供应链有限公司', '南京滨江一号库', DATE '2026-04-18', '理算', 14.480, 50450.00, '完成入库', '螺纹钢到货')
ON CONFLICT (inbound_no) DO NOTHING;

-- 采购入库明细
INSERT INTO po_purchase_inbound_item (
    id, inbound_id, line_no, material_code, brand, category, material, spec, length, unit, batch_no, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700661000000000001, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000003'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 'BATCH-260414-01', 10, 1.200, 1, 12.000, 3850.00, 46200.00),
    (700661000000000002, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000004'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 'BATCH-260418-01', 5, 2.300, 91, 11.500, 3480.00, 40020.00),
    (700661000000000003, (SELECT id FROM po_purchase_inbound WHERE inbound_no = '2026PI000004'), 2, 'RB-H400-22-12', '沙钢', '螺纹钢', 'HRB400', '22', '12米', '吨', 'BATCH-260418-02', 1, 2.980, 74, 2.980, 3500.00, 10430.00)
ON CONFLICT (id) DO NOTHING;

-- 更多销售订单
INSERT INTO so_sales_order (
    id, order_no, purchase_inbound_no, customer_name, project_name, order_date, sales_name, total_weight, total_amount, status, remark
) VALUES
    (700670000000000001, '2026SO000003', '2026PI000003', '上海建工集团股份有限公司', '浦东新区商业综合体', DATE '2026-04-16', '销售经理-李然', 12.000, 48000.00, '已审核', 'H型钢供货'),
    (700670000000000002, '2026SO000004', '2026PI000004', '浙江绿城房地产集团有限公司', '绿城·春江明月项目', DATE '2026-04-19', '销售经理-李然', 8.000, 29600.00, '待审核', '螺纹钢供货'),
    (700670000000000003, '2026SO000005', NULL, '安徽建工集团有限公司', '合肥地铁4号线', DATE '2026-04-20', '销售经理-李然', 6.000, 22200.00, '已审核', '螺纹钢配套')
ON CONFLICT (order_no) DO NOTHING;

-- 销售订单明细
INSERT INTO so_sales_order_item (
    id, order_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700671000000000001, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000003'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 10, 1.200, 1, 12.000, 4000.00, 48000.00),
    (700671000000000002, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000004'), 1, 'RB-H400-18-12', '沙钢', '螺纹钢', 'HRB400', '18', '12米', '吨', 3, 2.300, 91, 6.900, 3650.00, 25185.00),
    (700671000000000003, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000004'), 2, 'RB-H400-22-12', '沙钢', '螺纹钢', 'HRB400', '22', '12米', '吨', 0.5, 2.980, 74, 1.490, 3650.00, 5438.50),
    (700671000000000004, (SELECT id FROM so_sales_order WHERE order_no = '2026SO000005'), 1, 'RB-H400-16-9', '永钢', '螺纹钢', 'HRB400', '16', '9米', '吨', 4, 1.580, 112, 6.320, 3650.00, 23068.00)
ON CONFLICT (id) DO NOTHING;

-- 更多销售出库
INSERT INTO so_sales_outbound (
    id, outbound_no, sales_order_no, customer_name, project_name, warehouse_name, outbound_date, total_weight, total_amount, status, remark
) VALUES
    (700680000000000001, '2026OB000003', '2026SO000003', '上海建工集团股份有限公司', '浦东新区商业综合体', '马鞍山港口仓库', DATE '2026-04-17', 12.000, 48000.00, '已完成', 'H型钢出库'),
    (700680000000000002, '2026OB000004', '2026SO000005', '安徽建工集团有限公司', '合肥地铁4号线', '合肥瑶海钢材市场', DATE '2026-04-21', 6.000, 22200.00, '已完成', '螺纹钢出库')
ON CONFLICT (outbound_no) DO NOTHING;

-- 销售出库明细
INSERT INTO so_sales_outbound_item (
    id, outbound_id, line_no, material_code, brand, category, material, spec, length, unit, batch_no, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700681000000000001, (SELECT id FROM so_sales_outbound WHERE outbound_no = '2026OB000003'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 'BATCH-260414-01', 10, 1.200, 1, 12.000, 4000.00, 48000.00),
    (700681000000000002, (SELECT id FROM so_sales_outbound WHERE outbound_no = '2026OB000004'), 1, 'RB-H400-16-9', '永钢', '螺纹钢', 'HRB400', '16', '9米', '吨', 'BATCH-260418-03', 4, 1.580, 112, 6.320, 3650.00, 23068.00)
ON CONFLICT (id) DO NOTHING;

-- 更多物流单
INSERT INTO lg_freight_bill (
    id, bill_no, outbound_no, carrier_name, customer_name, project_name, bill_time, unit_price, total_weight, total_freight, status, delivery_status, remark
) VALUES
    (700690000000000001, '2026FB000003', '2026OB000003', '安徽皖通物流有限公司', '上海建工集团股份有限公司', '浦东新区商业综合体', DATE '2026-04-17', 95.00, 12.000, 1140.00, '已审核', '已送达', '跨省长途运输'),
    (700690000000000002, '2026FB000004', '2026OB000004', '南京恒通运输有限公司', '安徽建工集团有限公司', '合肥地铁4号线', DATE '2026-04-21', 75.00, 6.000, 450.00, '已审核', '已送达', '省内短途运输')
ON CONFLICT (bill_no) DO NOTHING;

-- 物流单明细
INSERT INTO lg_freight_bill_item (
    id, bill_id, line_no, source_no, customer_name, project_name, material_code, material_name, brand, category, material, spec, length, quantity, piece_weight_ton, pieces_per_bundle, batch_no, weight_ton, warehouse_name
) VALUES
    (700691000000000001, (SELECT id FROM lg_freight_bill WHERE bill_no = '2026FB000003'), 1, '2026OB000003', '上海建工集团股份有限公司', '浦东新区商业综合体', 'H-H300-300', 'H型钢300*300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', 10, 1.200, 1, 'BATCH-260414-01', 12.000, '马鞍山港口仓库'),
    (700691000000000002, (SELECT id FROM lg_freight_bill WHERE bill_no = '2026FB000004'), 1, '2026OB000004', '安徽建工集团有限公司', '合肥地铁4号线', 'RB-H400-16-9', '螺纹钢16', '永钢', '螺纹钢', 'HRB400', '16', '9米', 4, 1.580, 112, 'BATCH-260418-03', 6.320, '合肥瑶海钢材市场')
ON CONFLICT (id) DO NOTHING;

-- 更多收款单
INSERT INTO fm_receipt (
    id, receipt_no, customer_name, project_name, receipt_date, pay_type, amount, status, operator_name, remark
) VALUES
    (700700000000000001, '2026RC000003', '上海建工集团股份有限公司', '浦东新区商业综合体', DATE '2026-04-19', '银行转账', 24000.00, '已收款', '财务主管-周敏', '50%预收款'),
    (700700000000000002, '2026RC000004', '安徽建工集团有限公司', '合肥地铁4号线', DATE '2026-04-22', '银行转账', 22200.00, '已收款', '财务主管-周敏', '全额收款'),
    (700700000000000003, '2026RC000005', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-23', '银行转账', 4000.00, '部分结清', '财务主管-周敏', '第二笔回款')
ON CONFLICT (receipt_no) DO NOTHING;

-- 更多付款单
INSERT INTO fm_payment (
    id, payment_no, business_type, counterparty_name, payment_date, pay_type, amount, status, operator_name, remark
) VALUES
    (700710000000000001, '2026PM000003', '供应商付款', '安徽马钢物资贸易有限公司', DATE '2026-04-16', '银行转账', 46200.00, '已付款', '财务主管-周敏', 'H型钢全额付款'),
    (700710000000000002, '2026PM000004', '物流付款', '安徽皖通物流有限公司', DATE '2026-04-18', '银行转账', 1140.00, '已付款', '财务主管-周敏', '支付跨省运费'),
    (700710000000000003, '2026PM000005', '供应商付款', '江苏钢联供应链有限公司', DATE '2026-04-20', '银行转账', 25000.00, '部分付款', '财务主管-周敏', '支付部分货款')
ON CONFLICT (payment_no) DO NOTHING;

-- 更多采购合同
INSERT INTO ct_purchase_contract (
    id, contract_no, supplier_name, sign_date, effective_date, expire_date, buyer_name, total_weight, total_amount, status, remark
) VALUES
    (700720000000000001, '2026PC000002', '安徽马钢物资贸易有限公司', DATE '2026-04-08', DATE '2026-04-08', DATE '2026-09-30', '采购专员-张凯', 50.000, 192500.00, '执行中', 'H型钢年度框架合同')
ON CONFLICT (contract_no) DO NOTHING;

INSERT INTO ct_purchase_contract_item (
    id, contract_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700721000000000001, (SELECT id FROM ct_purchase_contract WHERE contract_no = '2026PC000002'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 30, 1.200, 1, 36.000, 3850.00, 138600.00),
    (700721000000000002, (SELECT id FROM ct_purchase_contract WHERE contract_no = '2026PC000002'), 2, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '9米', '吨', 14, 1.200, 1, 14.000, 3850.00, 53900.00)
ON CONFLICT (id) DO NOTHING;

-- 更多销售合同
INSERT INTO ct_sales_contract (
    id, contract_no, customer_name, project_name, sign_date, effective_date, expire_date, sales_name, total_weight, total_amount, status, remark
) VALUES
    (700730000000000001, '2026SC000002', '上海建工集团股份有限公司', '浦东新区商业综合体', DATE '2026-04-10', DATE '2026-04-10', DATE '2026-12-31', '销售经理-李然', 30.000, 120000.00, '执行中', '年度供货合同')
ON CONFLICT (contract_no) DO NOTHING;

INSERT INTO ct_sales_contract_item (
    id, contract_id, line_no, material_code, brand, category, material, spec, length, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
) VALUES
    (700731000000000001, (SELECT id FROM ct_sales_contract WHERE contract_no = '2026SC000002'), 1, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '12米', '吨', 20, 1.200, 1, 24.000, 4000.00, 96000.00),
    (700731000000000002, (SELECT id FROM ct_sales_contract WHERE contract_no = '2026SC000002'), 2, 'H-H300-300', '莱钢', 'H型钢', 'Q235B', '300*300', '9米', '吨', 6, 1.200, 1, 7.200, 4000.00, 28800.00)
ON CONFLICT (id) DO NOTHING;

-- 更多供应商对账单
INSERT INTO st_supplier_statement (
    id, statement_no, source_inbound_nos, supplier_name, start_date, end_date, purchase_amount, payment_amount, closing_amount, status, remark
) VALUES
    (700740000000000001, '2026SS000003', '2026PI000003', '安徽马钢物资贸易有限公司', DATE '2026-04-01', DATE '2026-04-30', 46200.00, 46200.00, 0.00, '已确认', '已结清'),
    (700740000000000002, '2026SS000004', '2026PI000004', '江苏钢联供应链有限公司', DATE '2026-04-16', DATE '2026-04-30', 50450.00, 25000.00, 25450.00, '待确认', '待回签')
ON CONFLICT (statement_no) DO NOTHING;

-- 更多客户对账单
INSERT INTO st_customer_statement (
    id, statement_no, source_order_nos, customer_name, project_name, start_date, end_date, sales_amount, receipt_amount, closing_amount, status, remark
) VALUES
    (700750000000000001, '2026CS000003', '2026SO000003', '上海建工集团股份有限公司', '浦东新区商业综合体', DATE '2026-04-01', DATE '2026-04-30', 48000.00, 24000.00, 24000.00, '已确认', '50%已收'),
    (700750000000000002, '2026CS000004', '2026SO000005', '安徽建工集团有限公司', '合肥地铁4号线', DATE '2026-04-01', DATE '2026-04-30', 22200.00, 22200.00, 0.00, '已确认', '已结清'),
    (700750000000000003, '2026CS000005', '2026SO000001,2026SO000002', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-16', DATE '2026-04-30', 31952.00, 12000.00, 19952.00, '待确认', '第二期对账')
ON CONFLICT (statement_no) DO NOTHING;

-- 更多物流对账单
INSERT INTO st_freight_statement (
    id, statement_no, source_bill_nos, carrier_name, start_date, end_date, total_weight, total_freight, paid_amount, unpaid_amount, status, sign_status, attachment, remark
) VALUES
    (700760000000000001, '2026FS000003', '2026FB000003', '安徽皖通物流有限公司', DATE '2026-04-01', DATE '2026-04-30', 12.000, 1140.00, 1140.00, 0.00, '已确认', '已签署', 'freight-statement-202604-03.pdf', '跨省运输对账'),
    (700760000000000002, '2026FS000004', '2026FB000004', '南京恒通运输有限公司', DATE '2026-04-01', DATE '2026-04-30', 6.000, 450.00, 0.00, 450.00, '待确认', '未签署', NULL, '待确认签署')
ON CONFLICT (statement_no) DO NOTHING;

-- 更多工单
INSERT INTO ops_ticket (
    id, ticket_no, issue_type, priority_level, submitter_name, handler_name, submit_date, status, remark
) VALUES
    (700770000000000001, '2026OT000003', '数据导出异常', '中', '采购专员-张凯', '运维支持-何川', DATE '2026-04-17', '已解决', 'Excel导出中文乱码问题'),
    (700770000000000002, '2026OT000004', '权限配置', '低', '销售经理-李然', '运维支持-何川', DATE '2026-04-18', '待确认', '新员工账号权限申请'),
    (700770000000000003, '2026OT000005', '系统性能', '高', '财务主管-周敏', '运维支持-何川', DATE '2026-04-22', '处理中', '月末结账时系统响应慢')
ON CONFLICT (ticket_no) DO NOTHING;
