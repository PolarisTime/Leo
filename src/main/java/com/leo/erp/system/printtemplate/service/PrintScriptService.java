package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        if ("sales-order".equals(moduleKey)) {
            enrichSalesOrder(data);
        } else if ("sales-outbound".equals(moduleKey)) {
            enrichSalesOutbound(data);
        } else if ("customer-statement".equals(moduleKey)) {
            enrichCustomerStatementItems(items);
        } else if ("freight-statement".equals(moduleKey)) {
            enrichFreightStatement(data, items);
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

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private static void putIfPresent(Map<String, String> row, String key, Object value) {
        if (value != null) {
            row.put(key, String.valueOf(value));
        }
    }

    /** 销售订单打印数据补充：projectAddress + vehiclePlate */
    private void enrichSalesOrder(Map<String, String> data) {
        enrichProjectAddress(data);

        // vehiclePlate — 通过出库单间接关联物流单
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

    /** 销售出库打印数据补充：projectAddress + vehiclePlate */
    private void enrichSalesOutbound(Map<String, String> data) {
        enrichProjectAddress(data);

        String outboundNo = data.get("outbound_no");
        if (outboundNo != null && !outboundNo.isEmpty()) {
            try {
                List<String> plates = jdbc.queryForList(
                        "SELECT DISTINCT fb.vehicle_plate " +
                                "FROM lg_freight_bill_item fbi " +
                                "JOIN lg_freight_bill fb ON fb.id = fbi.bill_id " +
                                "WHERE fbi.source_no = ? AND fb.vehicle_plate IS NOT NULL",
                        String.class, outboundNo);
                if (!plates.isEmpty()) {
                    data.put("vehiclePlate", String.join(", ", plates));
                }
            } catch (Exception ignored) { }
        }
    }

    private void enrichProjectAddress(Map<String, String> data) {
        String projectId = data.get("project_id");
        if (projectId != null && !projectId.isEmpty()) {
            try {
                String addr = jdbc.queryForObject(
                        "SELECT project_address FROM md_project WHERE id = ? AND deleted_flag = FALSE",
                        String.class, Long.parseLong(projectId));
                if (addr != null && !addr.isEmpty()) {
                    data.put("projectAddress", addr);
                    return;
                }
            } catch (Exception ignored) { }
        }

        String projectName = data.get("project_name");
        if (projectName != null && !projectName.isEmpty()) {
            try {
                List<String> addrs = jdbc.queryForList(
                        "SELECT project_address FROM md_project " +
                                "WHERE project_name = ? AND deleted_flag = FALSE " +
                                "AND project_address IS NOT NULL ORDER BY id LIMIT 1",
                        String.class, projectName);
                if (!addrs.isEmpty()) {
                    data.put("projectAddress", addrs.get(0));
                }
            } catch (Exception ignored) { }
        }
    }

    /** 客户对账单明细补充来源销售订单日期。 */
    private void enrichCustomerStatementItems(List<Map<String, String>> items) {
        Set<String> sourceNos = new HashSet<>();
        for (Map<String, String> item : items) {
            String sourceNo = value(item, "source_no").trim();
            if (!sourceNo.isEmpty()) {
                sourceNos.add(sourceNo);
            }
        }
        if (sourceNos.isEmpty()) {
            return;
        }

        Map<String, String> billTimes = new HashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceNos.size(), "?"));
        List<Object> args = new ArrayList<>(sourceNos);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT order_no, delivery_date FROM so_sales_order " +
                            "WHERE deleted_flag = FALSE AND order_no IN (" + placeholders + ")",
                    args.toArray());
            for (Map<String, Object> row : rows) {
                Object orderNo = row.get("order_no");
                Object deliveryDate = row.get("delivery_date");
                if (orderNo != null && deliveryDate != null) {
                    billTimes.put(String.valueOf(orderNo), String.valueOf(deliveryDate));
                }
            }
        } catch (Exception ignored) { }

        for (Map<String, String> item : items) {
            putIfPresent(item, "billTime", billTimes.get(value(item, "source_no")));
        }
    }

    /** 物流对账单明细补充来源物流单日期、单价、运费、承运方、备注。 */
    private void enrichFreightStatement(Map<String, String> data, List<Map<String, String>> items) {
        Set<String> sourceNos = new HashSet<>();
        for (Map<String, String> item : items) {
            String sourceNo = value(item, "source_no").trim();
            if (!sourceNo.isEmpty()) {
                sourceNos.add(sourceNo);
            }
        }
        if (sourceNos.isEmpty()) {
            return;
        }

        Map<String, Map<String, Object>> bills = new HashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceNos.size(), "?"));
        List<Object> args = new ArrayList<>(sourceNos);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT bill_no, bill_time, carrier_name, unit_price, total_freight, remark " +
                            "FROM lg_freight_bill WHERE deleted_flag = FALSE AND bill_no IN (" + placeholders + ")",
                    args.toArray());
            for (Map<String, Object> row : rows) {
                Object billNo = row.get("bill_no");
                if (billNo != null) {
                    bills.put(String.valueOf(billNo), row);
                }
            }
        } catch (Exception ignored) { }

        for (Map<String, String> item : items) {
            Map<String, Object> bill = bills.get(value(item, "source_no"));
            if (bill == null) {
                putIfPresent(item, "carrierName", data.get("carrier_name"));
                continue;
            }
            putIfPresent(item, "billTime", bill.get("bill_time"));
            putIfPresent(item, "carrierName", bill.get("carrier_name"));
            putIfPresent(item, "unitPrice", bill.get("unit_price"));
            putIfPresent(item, "amount", bill.get("total_freight"));
            putIfPresent(item, "remark", bill.get("remark"));
        }
    }
}
