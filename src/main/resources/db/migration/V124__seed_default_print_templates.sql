-- 为采购/销售/物流/报表模块生成默认打印模板，覆盖单据全部字段
-- 删除旧的测试/冒烟模板
DELETE FROM sys_print_template WHERE id IN (305874694840520704, 306479056159772672);

-- 采购订单：更新现有默认模板为完整字段
UPDATE sys_print_template SET template_html = '<h1>采购订单</h1>
<div class="print-subtitle">单号：{{orderNo}}　　日期：{{orderDate}}</div>
<table><tbody>
<tr><th>供应商</th><td>{{supplierName}}</td><th>采购员</th><td>{{buyerName}}</td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总金额</th><td>{{totalAmount}}</td></tr>
<tr><th>状态</th><td>{{status}}</td><th>备注</th><td>{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>每件支数</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.piecesPerBundle}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>'
WHERE id = 700540000000000001;

-- 销售出库：更新现有默认模板为完整字段
UPDATE sys_print_template SET template_html = '<h1>销售出库</h1>
<div class="print-subtitle">单号：{{outboundNo}}　　日期：{{outboundDate}}</div>
<table><tbody>
<tr><th>客户名称</th><td>{{customerName}}</td><th>项目名称</th><td>{{projectName}}</td></tr>
<tr><th>关联订单</th><td>{{salesOrderNo}}</td><th></th><td></td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总金额</th><td>{{totalAmount}}</td></tr>
<tr><th>状态</th><td>{{status}}</td><th>备注</th><td>{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>每件支数</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.piecesPerBundle}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>'
WHERE id = 700540000000000002;

-- 采购入库
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000011, 'purchase-inbound', '采购入库默认模板',
'<h1>采购入库</h1>
<div class="print-subtitle">单号：{{inboundNo}}　　日期：{{inboundDate}}</div>
<table><tbody>
<tr><th>供应商</th><td>{{supplierName}}</td><th>关联订单</th><td>{{purchaseOrderNo}}</td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总金额</th><td>{{totalAmount}}</td></tr>
<tr><th>状态</th><td>{{status}}</td><th>备注</th><td>{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>码头</th><th>批号</th><th>数量</th><th>件重（吨）</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.warehouseName}}</td><td>{{detail.batchNo}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);

-- 销售订单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000012, 'sales-order', '销售订单默认模板',
'<h1>销售订单</h1>
<div class="print-subtitle">单号：{{orderNo}}　　日期：{{deliveryDate}}</div>
<table><tbody>
<tr><th>客户名称</th><td>{{customerName}}</td><th>项目名称</th><td>{{projectName}}</td></tr>
<tr><th>销售员</th><td>{{salesName}}</td><th>关联采购订单</th><td>{{purchaseOrderNo}}</td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总金额</th><td>{{totalAmount}}</td></tr>
<tr><th>状态</th><td>{{status}}</td><th>备注</th><td>{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>每件支数</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.piecesPerBundle}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);

-- 物流单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000013, 'freight-bill', '物流单默认模板',
'<h1>物流单</h1>
<div class="print-subtitle">单号：{{billNo}}　　日期：{{billTime}}</div>
<table><tbody>
<tr><th>物流商</th><td>{{carrierName}}</td><th>车号</th><td>{{vehiclePlate}}</td></tr>
<tr><th>客户名称</th><td>{{customerName}}</td><th>项目名称</th><td>{{projectName}}</td></tr>
<tr><th>关联出库单</th><td>{{outboundNo}}</td><th>单价</th><td>{{unitPrice}}</td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总运费</th><td>{{totalFreight}}</td></tr>
<tr><th>审核状态</th><td>{{status}}</td><th>送达状态</th><td>{{deliveryStatus}}</td></tr>
<tr><th>备注</th><td colspan="3">{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>出库单号</th><th>商品编码</th><th>商品名称</th><th>规格</th><th>材质</th><th>客户</th><th>项目</th><th>品牌</th><th>类别</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>每件支数</th><th>批号</th><th>总重量（吨）</th><th>仓库</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.sourceNo}}</td><td>{{detail.materialCode}}</td><td>{{detail.materialName}}</td><td>{{detail.spec}}</td><td>{{detail.material}}</td><td>{{detail.customerName}}</td><td>{{detail.projectName}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.piecesPerBundle}}</td><td>{{detail.batchNo}}</td><td>{{detail.weightTon}}</td><td>{{detail.warehouseName}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);

-- 供应商对账单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000014, 'supplier-statement', '供应商对账单默认模板',
'<h1>供应商对账单</h1>
<div class="print-subtitle">单号：{{statementNo}}</div>
<table><tbody>
<tr><th>供应商</th><td>{{supplierName}}</td><th>期间</th><td>{{startDate}} ~ {{endDate}}</td></tr>
<tr><th>采购金额</th><td>{{purchaseAmount}}</td><th>付款金额</th><td>{{paymentAmount}}</td></tr>
<tr><th>期末余额</th><td>{{closingAmount}}</td><th>状态</th><td>{{status}}</td></tr>
<tr><th>备注</th><td colspan="3">{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);

-- 客户对账单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000015, 'customer-statement', '客户对账单默认模板',
'<h1>客户对账单</h1>
<div class="print-subtitle">单号：{{statementNo}}</div>
<table><tbody>
<tr><th>客户</th><td>{{customerName}}</td><th>项目</th><td>{{projectName}}</td></tr>
<tr><th>期间</th><td>{{startDate}} ~ {{endDate}}</td><th></th><td></td></tr>
<tr><th>销售金额</th><td>{{salesAmount}}</td><th>收款金额</th><td>{{receiptAmount}}</td></tr>
<tr><th>期末余额</th><td>{{closingAmount}}</td><th>状态</th><td>{{status}}</td></tr>
<tr><th>备注</th><td colspan="3">{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>商品编码</th><th>品牌</th><th>类别</th><th>材质</th><th>规格</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>总重量（吨）</th><th>单价</th><th>金额</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.materialCode}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.material}}</td><td>{{detail.spec}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.weightTon}}</td><td>{{detail.unitPrice}}</td><td>{{detail.amount}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);

-- 物流对账单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000016, 'freight-statement', '物流对账单默认模板',
'<h1>物流对账单</h1>
<div class="print-subtitle">单号：{{statementNo}}</div>
<table><tbody>
<tr><th>物流商</th><td>{{carrierName}}</td><th>期间</th><td>{{startDate}} ~ {{endDate}}</td></tr>
<tr><th>总重量（吨）</th><td>{{totalWeight}}</td><th>总运费</th><td>{{totalFreight}}</td></tr>
<tr><th>已付金额</th><td>{{paidAmount}}</td><th>审核状态</th><td>{{status}}</td></tr>
<tr><th>签署状态</th><td>{{signStatus}}</td><th>附件</th><td>{{attachment}}</td></tr>
<tr><th>备注</th><td colspan="3">{{remark}}</td></tr>
</tbody></table>
<div class="print-block"><table><thead><tr>
<th>出库单号</th><th>商品编码</th><th>商品名称</th><th>规格</th><th>材质</th><th>客户</th><th>项目</th><th>品牌</th><th>类别</th><th>长度</th><th>数量</th><th>件重（吨）</th><th>每件支数</th><th>批号</th><th>总重量（吨）</th><th>仓库</th>
</tr></thead><tbody>
<!--DETAIL_ROW_START--><tr>
<td>{{detail.sourceNo}}</td><td>{{detail.materialCode}}</td><td>{{detail.materialName}}</td><td>{{detail.spec}}</td><td>{{detail.material}}</td><td>{{detail.customerName}}</td><td>{{detail.projectName}}</td><td>{{detail.brand}}</td><td>{{detail.category}}</td><td>{{detail.length}}</td><td>{{detail.quantity}}</td><td>{{detail.pieceWeightTon}}</td><td>{{detail.piecesPerBundle}}</td><td>{{detail.batchNo}}</td><td>{{detail.weightTon}}</td><td>{{detail.warehouseName}}</td>
</tr><!--DETAIL_ROW_END-->
</tbody></table></div>
<div class="print-footnote">打印时间：{{_printTime}}</div>',
'1', false);
