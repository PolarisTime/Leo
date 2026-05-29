package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrintScriptService {

    private record PrintRecordSource(String tableName, String itemTableName, String itemFkColumn) {
    }

    private static final Map<String, PrintRecordSource> MODULE_SOURCES = Map.ofEntries(
            Map.entry("purchase-order", new PrintRecordSource(
                    "po_purchase_order", "po_purchase_order_item", "order_id")),
            Map.entry("sales-order", new PrintRecordSource(
                    "so_sales_order", "so_sales_order_item", "order_id")),
            Map.entry("purchase-inbound", new PrintRecordSource(
                    "po_purchase_inbound", "po_purchase_inbound_item", "inbound_id")),
            Map.entry("sales-outbound", new PrintRecordSource(
                    "so_sales_outbound", "so_sales_outbound_item", "outbound_id")),
            Map.entry("freight-bill", new PrintRecordSource(
                    "lg_freight_bill", "lg_freight_bill_item", "bill_id")),
            Map.entry("purchase-contract", new PrintRecordSource(
                    "ct_purchase_contract", "ct_purchase_contract_item", "contract_id")),
            Map.entry("sales-contract", new PrintRecordSource(
                    "ct_sales_contract", "ct_sales_contract_item", "contract_id")),
            Map.entry("customer-statement", new PrintRecordSource(
                    "st_customer_statement", "st_customer_statement_item", "statement_id")),
            Map.entry("supplier-statement", new PrintRecordSource(
                    "st_supplier_statement", "st_supplier_statement_item", "statement_id")),
            Map.entry("freight-statement", new PrintRecordSource(
                    "st_freight_statement", "st_freight_statement_item", "statement_id")),
            Map.entry("receipt", new PrintRecordSource(
                    "fm_receipt", "fm_receipt_allocation", "receipt_id")),
            Map.entry("payment", new PrintRecordSource(
                    "fm_payment", "fm_payment_allocation", "payment_id")),
            Map.entry("invoice-issue", new PrintRecordSource(
                    "fm_invoice_issue", "fm_invoice_issue_item", "issue_id")),
            Map.entry("invoice-receipt", new PrintRecordSource(
                    "fm_invoice_receipt", "fm_invoice_receipt_item", "receipt_id"))
    );

    private final PrintTemplateRepository templateRepository;
    private final JdbcTemplate jdbc;

    public PrintScriptService(PrintTemplateRepository templateRepository, JdbcTemplate jdbc) {
        this.templateRepository = templateRepository;
        this.jdbc = jdbc;
    }

    /** Load record + items from DB, return raw template + data for frontend rendering. */
    public Map<String, Object> generateFromRecord(String templateId, String moduleKey, Long recordId) {
        PrintTemplate template = templateRepository.findByIdAndDeletedFlagFalse(Long.parseLong(templateId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在"));

        PrintRecordSource source = MODULE_SOURCES.get(moduleKey);
        if (source == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的打印模块: " + moduleKey);
        }
        if (!moduleKey.equals(template.getBillType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "打印模板与当前模块不匹配");
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM " + source.tableName() + " WHERE id = ? AND deleted_flag = FALSE", recordId);
        Map<String, String> data = new HashMap<>();
        for (var entry : row.entrySet()) {
            if (entry.getValue() == null) continue;
            data.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        List<Map<String, String>> items = loadItems(source, recordId);

        // 销售订单：补充 projectAddress（来自 md_project）和 vehiclePlate（来自物流单）
        if ("sales-order".equals(moduleKey)) {
            enrichSalesOrder(data);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("templateName", template.getTemplateName());
        result.put("templateHtml", template.getTemplateHtml());
        result.put("templateType", template.getTemplateType() != null ? template.getTemplateType() : "HTML");
        result.put("data", data);
        result.put("items", items);
        return result;
    }

    private List<Map<String, String>> loadItems(PrintRecordSource source, Long recordId) {
        List<Map<String, String>> result = new ArrayList<>();
        String sql = "SELECT * FROM " + source.itemTableName()
                + " WHERE " + source.itemFkColumn() + " = ? ORDER BY line_no ASC, id ASC";
        var items = jdbc.queryForList(sql, recordId);
        for (var item : items) {
            Map<String, String> row = new HashMap<>();
            for (var entry : item.entrySet()) {
                if (entry.getValue() != null) {
                    row.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            result.add(row);
        }
        return result;
    }

    /** 销售订单打印数据补充：projectAddress + vehiclePlate */
    private void enrichSalesOrder(Map<String, String> data) {
        // 1. projectAddress — 从 md_project 表查
        String projectId = data.get("project_id");
        if (projectId != null && !projectId.isEmpty()) {
            try {
                String addr = jdbc.queryForObject(
                        "SELECT project_address FROM md_project WHERE id = ? AND deleted_flag = FALSE",
                        String.class, Long.parseLong(projectId));
                if (addr != null && !addr.isEmpty()) {
                    data.put("projectAddress", addr);
                }
            } catch (Exception ignored) { }
        }

        // 2. vehiclePlate — 通过出库单间接关联物流单
        //    链路：so_sales_order.order_no → so_sales_outbound.sales_order_no
        //          → lg_freight_bill_item.source_no (= outbound_no)
        //          → lg_freight_bill.vehicle_plate
        String orderNo = data.get("order_no");
        if (orderNo != null && !orderNo.isEmpty()) {
            try {
                List<String> plates = jdbc.queryForList(
                        "SELECT DISTINCT fb.vehicle_plate " +
                        "FROM so_sales_outbound ob " +
                        "JOIN lg_freight_bill_item fbi ON fbi.source_no = ob.outbound_no " +
                        "JOIN lg_freight_bill fb ON fb.id = fbi.bill_id " +
                        "WHERE ob.sales_order_no = ? AND fb.vehicle_plate IS NOT NULL",
                        String.class, orderNo);
                if (!plates.isEmpty()) {
                    data.put("vehiclePlate", String.join(", ", plates));
                }
            } catch (Exception ignored) { }
        }
    }
}
