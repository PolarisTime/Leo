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
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PrintScriptService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    // moduleKey → table_name mapping for record loading
    private static final Map<String, String> MODULE_TABLES = Map.ofEntries(
            Map.entry("purchase-order", "po_purchase_order"),
            Map.entry("sales-order", "so_sales_order"),
            Map.entry("purchase-inbound", "po_purchase_inbound"),
            Map.entry("sales-outbound", "so_sales_outbound"),
            Map.entry("freight-bill", "lg_freight_bill"),
            Map.entry("purchase-contract", "ct_purchase_contract"),
            Map.entry("sales-contract", "ct_sales_contract"),
            Map.entry("customer-statement", "st_customer_statement"),
            Map.entry("supplier-statement", "st_supplier_statement"),
            Map.entry("freight-statement", "st_freight_statement"),
            Map.entry("receipt", "fm_receipt"),
            Map.entry("payment", "fm_payment"),
            Map.entry("invoice-issue", "fm_invoice_issue"),
            Map.entry("invoice-receipt", "fm_invoice_receipt")
    );

    // Whitelist of valid item tables for loadItems() SQL safety
    private static final Set<String> VALID_ITEM_TABLES = Set.of(
            "po_purchase_order_item", "po_purchase_inbound_item",
            "so_sales_order_item", "so_sales_outbound_item",
            "lg_freight_bill_item",
            "ct_purchase_contract_item", "ct_sales_contract_item",
            "st_customer_statement_item", "st_supplier_statement_item",
            "st_freight_statement_item",
            "fm_receipt_item", "fm_payment_item",
            "fm_invoice_issue_item", "fm_invoice_receipt_item"
    );

    // Table prefix → foreign key column mapping
    private static final Map<String, String> TABLE_FK_COLUMNS = Map.of(
            "po_", "order_id",
            "so_", "order_id",
            "ct_", "contract_id",
            "fm_", "receipt_id",
            "st_", "statement_id",
            "lg_", "order_id"
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

        String table = MODULE_TABLES.get(moduleKey);
        if (table == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的打印模块: " + moduleKey);
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM " + table + " WHERE id = ? AND deleted_flag = FALSE", recordId);
        Map<String, String> data = new HashMap<>();
        for (var entry : row.entrySet()) {
            if (entry.getValue() == null) continue;
            data.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        List<Map<String, String>> items = loadItems(table, recordId);

        // 销售订单：补充 projectAddress（来自 md_project）和 vehiclePlate（来自物流单）
        if ("sales-order".equals(moduleKey)) {
            enrichSalesOrder(data);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("templateHtml", template.getTemplateHtml());
        result.put("templateType", template.getTemplateType() != null ? template.getTemplateType() : "HTML");
        result.put("data", data);
        result.put("items", items);
        return result;
    }

    private List<Map<String, String>> loadItems(String table, Long recordId) {
        String itemTable = table + "_item";

        if (!VALID_ITEM_TABLES.contains(itemTable)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "非法的明细表名: " + itemTable
            );
        }

        String fkColumn = TABLE_FK_COLUMNS.entrySet().stream()
                .filter(e -> table.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("inbound_id");

        List<Map<String, String>> result = new ArrayList<>();
        try {
            String sql = "SELECT * FROM " + itemTable + " WHERE " + fkColumn + " = ?";
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
        } catch (Exception ignored) {
            // items table may not exist for this module
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

    // ─── HTML 模板 ─────────────────────────────────────────

    public String generate(String templateId, Map<String, String> data) {
        PrintTemplate template = templateRepository.findByIdAndDeletedFlagFalse(Long.parseLong(templateId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在"));

        for (var entry : data.entrySet()) {
            String value = entry.getValue();
            if (value == null) continue;
            if (value.length() > 2000) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "字段 " + entry.getKey() + " 内容过长，最大2000字符");
            }
        }

        String html = PLACEHOLDER.matcher(template.getTemplateHtml()).replaceAll(mr -> {
            String key = mr.group(1);
            String value = data.getOrDefault(key, "");
            return escapeJs(value);
        });

        return html;
    }

    // ─── 工具方法 ───────────────────────────────────────────

    /** Escape for JS single-quoted strings. Used by ClodopScriptGenerator. */
    static String escapeJs(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<'  -> sb.append("\\x3c");
                case '>'  -> sb.append("\\x3e");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\x%02x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
