package com.leo.erp.common.moduleexport.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.support.SqlIdentifier;
import com.leo.erp.security.permission.PermissionCodes;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 业务单据导出目录：模块键 → 单据主表 / 导出列 / 读取权限。
 *
 * <p>导出列在此显式声明，<strong>不复用打印运行时配置</strong>：打印列描述的是打印模板可用的占位符，
 * 若导出直接依赖它，打印侧增删列会静默改变导出文件的列集合；显式声明同时让我们能剔除
 * 内部主键与外键（雪花 ID 在 XLSX 中作为数值会丢精度，且对使用者无意义）。</p>
 *
 * <p>只登记主表的单据级字段（一行一条单据），明细行不属于列表导出的语义。</p>
 */
@Component
public class ModuleExportCatalog {

    private static final Map<String, ModuleExportDefinition> DEFINITIONS = buildDefinitions();

    /** 支持按记录 id 集合导出的模块键（稳定顺序，供契约测试与文档使用）。 */
    public static Set<String> moduleKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(DEFINITIONS.keySet()));
    }

    /** 解析模块定义；模块不支持时抛出 422（语义校验失败），由全局异常处理器映射为 ProblemDetail。 */
    public ModuleExportDefinition require(String moduleKey) {
        return find(moduleKey).orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION_ERROR, "不支持的导出模块: " + moduleKey));
    }

    public Optional<ModuleExportDefinition> find(String moduleKey) {
        return Optional.ofNullable(DEFINITIONS.get(normalize(moduleKey)));
    }

    /** 归一化模块键：与前端的 path/模块键写法差异（前导斜杠、大小写）保持宽容。 */
    public static String normalize(String moduleKey) {
        if (moduleKey == null) {
            return null;
        }
        return moduleKey.trim().replaceFirst("^/+", "").toLowerCase(Locale.ROOT);
    }

    private static Map<String, ModuleExportDefinition> buildDefinitions() {
        Map<String, ModuleExportDefinition> definitions = new LinkedHashMap<>();

        definitions.put(ModuleKeys.PURCHASE_ORDER, definition(
                ModuleKeys.PURCHASE_ORDER, "po_purchase_order", PermissionCodes.PURCHASE_ORDERS_READ,
                column("order_no", "订单号"),
                column("supplier_name", "供应商"),
                column("order_date", "下单日期"),
                column("buyer_name", "采购员"),
                column("total_weight", "总重量"),
                column("total_amount", "总金额"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.SALES_ORDER, definition(
                ModuleKeys.SALES_ORDER, "so_sales_order", PermissionCodes.SALES_ORDERS_READ,
                column("order_no", "订单号"),
                column("purchase_inbound_no", "采购入库单号"),
                column("purchase_order_no", "采购订单号"),
                column("customer_name", "客户"),
                column("customer_code", "客户编码"),
                column("project_name", "项目"),
                column("delivery_date", "交货日期"),
                column("sales_name", "业务员"),
                column("total_weight", "总重量"),
                column("total_amount", "总金额"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.PURCHASE_INBOUND, definition(
                ModuleKeys.PURCHASE_INBOUND, "po_purchase_inbound", PermissionCodes.PURCHASE_INBOUNDS_READ,
                column("inbound_no", "入库单号"),
                column("purchase_order_no", "采购订单号"),
                column("supplier_name", "供应商"),
                column("warehouse_name", "仓库"),
                column("inbound_date", "入库日期"),
                column("settlement_mode", "结算方式"),
                column("total_weight", "总重量"),
                column("total_amount", "总金额"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.SALES_OUTBOUND, definition(
                ModuleKeys.SALES_OUTBOUND, "so_sales_outbound", PermissionCodes.SALES_OUTBOUNDS_READ,
                column("outbound_no", "出库单号"),
                column("sales_order_no", "销售订单号"),
                column("customer_name", "客户"),
                column("project_name", "项目"),
                column("warehouse_name", "仓库"),
                column("outbound_date", "出库日期"),
                column("total_weight", "总重量"),
                column("total_amount", "总金额"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.SALES_RETURN, definition(
                ModuleKeys.SALES_RETURN, "so_sales_return", PermissionCodes.SALES_RETURNS_READ,
                column("return_no", "退货单号"),
                column("sales_order_no", "销售订单号"),
                column("customer_name", "客户"),
                column("project_name", "项目"),
                column("warehouse_name", "仓库"),
                column("return_date", "退货日期"),
                column("total_weight", "总重量"),
                column("total_amount", "总金额"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.FREIGHT_BILL, definition(
                ModuleKeys.FREIGHT_BILL, "lg_freight_bill", PermissionCodes.FREIGHT_BILLS_READ,
                column("bill_no", "物流单号"),
                column("carrier_name", "物流商"),
                column("customer_name", "客户"),
                column("project_name", "项目"),
                column("bill_time", "开单时间"),
                column("vehicle_plate", "车牌号"),
                column("unit_price", "单价"),
                column("total_weight", "总重量"),
                column("total_freight", "总运费"),
                column("status", "状态"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.CUSTOMER_STATEMENT, definition(
                ModuleKeys.CUSTOMER_STATEMENT, "st_customer_statement", PermissionCodes.CUSTOMER_STATEMENTS_READ,
                column("statement_no", "对账单号"),
                column("customer_name", "客户"),
                column("customer_code", "客户编码"),
                column("project_name", "项目"),
                column("start_date", "开始日期"),
                column("end_date", "结束日期"),
                column("sales_amount", "销售金额"),
                column("receipt_amount", "收款金额"),
                column("closing_amount", "结余金额"),
                column("status", "状态"),
                column("created_name", "制单人"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.FREIGHT_STATEMENT, definition(
                ModuleKeys.FREIGHT_STATEMENT, "st_freight_statement", PermissionCodes.FREIGHT_STATEMENTS_READ,
                column("statement_no", "对账单号"),
                column("carrier_name", "物流商"),
                column("carrier_code", "物流商编码"),
                column("start_date", "开始日期"),
                column("end_date", "结束日期"),
                column("total_weight", "总重量"),
                column("total_freight", "总运费"),
                column("paid_amount", "已付金额"),
                column("unpaid_amount", "未付金额"),
                column("status", "状态"),
                column("sign_status", "签收状态"),
                column("attachment", "附件"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.RECEIPT, definition(
                ModuleKeys.RECEIPT, "fm_receipt", PermissionCodes.RECEIPTS_READ,
                column("receipt_no", "收款单号"),
                column("customer_name", "客户"),
                column("customer_code", "客户编码"),
                column("project_name", "项目"),
                column("receipt_date", "收款日期"),
                column("pay_type", "支付方式"),
                column("amount", "金额"),
                column("status", "状态"),
                column("operator_name", "经办人"),
                column("remark", "备注"),
                column("settlement_company_name", "结算主体")));

        definitions.put(ModuleKeys.PAYMENT, definition(
                ModuleKeys.PAYMENT, "fm_payment", PermissionCodes.PAYMENTS_READ,
                column("payment_no", "付款单号"),
                column("business_type", "业务类型"),
                column("counterparty_name", "往来单位"),
                column("counterparty_code", "往来单位编码"),
                column("payment_date", "付款日期"),
                column("pay_type", "支付方式"),
                column("amount", "金额"),
                column("status", "状态"),
                column("operator_name", "经办人"),
                column("remark", "备注")));

        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    private static ModuleExportDefinition definition(String moduleKey,
                                                     String tableName,
                                                     String readPermission,
                                                     ModuleExportColumn... columns) {
        requireIdentifier(moduleKey, tableName);
        List<ModuleExportColumn> resolved = List.of(columns);
        if (resolved.isEmpty()) {
            throw new IllegalStateException("导出模块缺少列定义: " + moduleKey);
        }
        resolved.forEach(column -> requireIdentifier(moduleKey, column.field()));
        return new ModuleExportDefinition(moduleKey, tableName, readPermission, resolved);
    }

    private static ModuleExportColumn column(String field, String header) {
        return new ModuleExportColumn(field, header);
    }

    private static void requireIdentifier(String moduleKey, String value) {
        if (!SqlIdentifier.isValid(value)) {
            throw new IllegalStateException("导出模块 " + moduleKey + " 的 SQL 标识符非法: " + value);
        }
    }
}
