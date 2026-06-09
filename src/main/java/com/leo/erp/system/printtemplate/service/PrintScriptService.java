package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PrintScriptService {

    private record PrintRecordSource(String tableName, String itemTableName, String itemFkColumn) {
    }

    private record CoordLayout(
            int tableTop,
            int rowH,
            int maxRows,
            int pageH,
            Integer sumTop,
            boolean limitToMaxRows
    ) {
    }

    private static final Set<String> COIL_CATEGORIES = Set.of("盘螺", "线材");
    private static final int WEIGHT_SCALE = 3;
    private static final int PRICE_SCALE = 2;
    private static final Pattern LAYOUT_FIELD_PATTERN = Pattern.compile(
            "\\{\\{\\s*(?:#if\\s+)?(?:rowTop|sumTop|sumTop2|emptyRowTop|footerTop|footerLineTop|footerDateTop|hasEmptyRows|needsNewPage|needsSeparator|isSeparator|groupName|index)\\s*\\}\\}"
    );

    private static final Map<String, CoordLayout> COORD_LAYOUTS = Map.of(
            "sales-order", new CoordLayout(161, 41, 8, 0, 453, true),
            "sales-outbound", new CoordLayout(138, 24, 10, 0, null, false),
            "freight-bill", new CoordLayout(164, 20, 50, 0, null, false),
            "freight-statement", new CoordLayout(130, 20, 50, 0, null, false),
            "customer-statement", new CoordLayout(130, 20, 50, 1050, null, false)
    );

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
    private final PrintLayoutLodopRenderer layoutLodopRenderer;

    public PrintScriptService(
            PrintTemplateRepository templateRepository,
            JdbcTemplate jdbc,
            PrintLayoutLodopRenderer layoutLodopRenderer
    ) {
        this.templateRepository = templateRepository;
        this.jdbc = jdbc;
        this.layoutLodopRenderer = layoutLodopRenderer;
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
        Map<String, String> data = toCamelStringMap(row);

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
        items = preparePrintItems(moduleKey, template.getTemplateName(), template.getTemplateHtml(), data, items);

        Map<String, Object> result = new HashMap<>();
        result.put("templateName", template.getTemplateName());
        result.put("templateHtml", renderTemplateHtml(template, data, items));
        result.put("templateType", template.getTemplateType() != null ? template.getTemplateType() : "COORD");
        result.put("data", data);
        result.put("items", items);
        return result;
    }

    private String renderTemplateHtml(PrintTemplate template, Map<String, String> data, List<Map<String, String>> items) {
        String templateHtml = template.getTemplateHtml();
        if ("COORD".equals(template.getTemplateType()) && layoutLodopRenderer.supports(templateHtml)) {
            return layoutLodopRenderer.render(template.getTemplateName(), templateHtml, data, items);
        }
        return templateHtml;
    }

    private List<Map<String, String>> loadItems(PrintRecordSource source, Long recordId) {
        List<Map<String, String>> result = new ArrayList<>();
        String sql = "SELECT * FROM " + source.itemTableName()
                + " WHERE " + source.itemFkColumn() + " = ? ORDER BY line_no ASC, id ASC";
        var items = jdbc.queryForList(sql, recordId);
        for (var item : items) {
            result.add(toCamelStringMap(item));
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
        String orderNo = data.get("orderNo");
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

        String outboundNo = data.get("outboundNo");
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
        String projectId = data.get("projectId");
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

        String projectName = data.get("projectName");
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
            String sourceNo = value(item, "sourceNo").trim();
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
                    billTimes.put(stringValue(orderNo), stringValue(deliveryDate));
                }
            }
        } catch (Exception ignored) { }

        for (Map<String, String> item : items) {
            putIfPresent(item, "billTime", billTimes.get(value(item, "sourceNo")));
        }
    }

    /** 物流对账单明细补充来源物流单日期、单价、运费、承运方、备注。 */
    private void enrichFreightStatement(Map<String, String> data, List<Map<String, String>> items) {
        Set<String> sourceNos = new HashSet<>();
        for (Map<String, String> item : items) {
            String sourceNo = value(item, "sourceNo").trim();
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
                    bills.put(stringValue(billNo), row);
                }
            }
        } catch (Exception ignored) { }

        for (Map<String, String> item : items) {
            Map<String, Object> bill = bills.get(value(item, "sourceNo"));
            if (bill == null) {
                putIfPresent(item, "carrierName", data.get("carrierName"));
                continue;
            }
            putIfPresent(item, "billTime", stringValue(bill.get("bill_time")));
            putIfPresent(item, "carrierName", stringValue(bill.get("carrier_name")));
            putIfPresent(item, "unitPrice", stringValue(bill.get("unit_price")));
            putIfPresent(item, "amount", stringValue(bill.get("total_freight")));
            putIfPresent(item, "remark", stringValue(bill.get("remark")));
        }
    }

    private List<Map<String, String>> preparePrintItems(
            String moduleKey,
            String templateName,
            String templateHtml,
            Map<String, String> data,
            List<Map<String, String>> rawItems
    ) {
        enrichTopLevelPrintFields(data);
        CoordLayout layout = resolveCoordLayout(moduleKey, templateName);
        boolean needsLayout = templateHtml != null && LAYOUT_FIELD_PATTERN.matcher(templateHtml).find();
        boolean needsGrouping = needsLayout && "freight-bill".equals(moduleKey);
        List<Map<String, String>> items = needsLayout
                ? flattenItemsForLayout(moduleKey, rawItems)
                : rawItems.stream().map(this::enrichItemPrintFields).toList();
        if (needsLayout && layout.limitToMaxRows() && !needsGrouping) {
            items = items.stream().limit(layout.maxRows()).toList();
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        int rowTop = layout.tableTop();
        int index = 1;
        String previousSourceNo = null;
        List<Map<String, String>> result = new ArrayList<>();

        for (Map<String, String> item : items) {
            Map<String, String> enriched = new HashMap<>(item);
            boolean separator = "true".equals(enriched.get("isSeparator"));
            if (!separator) {
                enriched.put("index", String.valueOf(index++));
                if (needsLayout && layout.pageH() > 0 && rowTop + layout.rowH() * 2 > layout.pageH()) {
                    enriched.put("needsNewPage", "true");
                    rowTop = 20;
                }
                String sourceNo = value(enriched, "sourceNo");
                if (needsLayout && previousSourceNo != null && !sourceNo.isBlank() && !sourceNo.equals(previousSourceNo)) {
                    enriched.put("needsSeparator", "true");
                }
                previousSourceNo = sourceNo;
                totalQuantity = totalQuantity.add(decimal(value(enriched, "quantity")));
                totalWeight = totalWeight.add(decimal(value(enriched, "weightTon")));
                totalAmount = totalAmount.add(decimal(value(enriched, "amount")));
            }
            if (needsLayout) {
                enriched.put("rowTop", String.valueOf(rowTop));
                rowTop += layout.rowH();
            }
            result.add(enriched);
        }

        data.put("totalQuantity", formatQuantity(totalQuantity));
        data.put("totalWeight", formatDecimal(totalWeight, WEIGHT_SCALE));
        data.put("totalAmount", formatDecimal(totalAmount, PRICE_SCALE));
        if (needsLayout) {
            long itemCount = result.stream().filter(item -> !"true".equals(item.get("isSeparator"))).count();
            int actualItemCount = needsGrouping ? result.size() : (int) Math.min(itemCount, layout.maxRows());
            int sumTop = layout.sumTop() != null
                    ? layout.sumTop()
                    : layout.tableTop() + actualItemCount * layout.rowH();
            if (!needsGrouping && layout.sumTop() == null) {
                sumTop = layout.tableTop() + layout.maxRows() * layout.rowH();
            }
            int footerTop = sumTop + 40;
            data.put("sumTop", String.valueOf(sumTop));
            data.put("sumTop2", String.valueOf(sumTop + 2));
            data.put("footerTop", String.valueOf(footerTop));
            data.put("footerLineTop", String.valueOf(footerTop + 28));
            data.put("footerDateTop", String.valueOf(footerTop + 34));
            data.put("hasEmptyRows", !needsGrouping && itemCount < layout.maxRows() ? "true" : "");
            data.put("emptyRowTop", String.valueOf(layout.tableTop() + itemCount * layout.rowH()));
        }
        return result;
    }

    private CoordLayout resolveCoordLayout(String moduleKey, String templateName) {
        String name = templateName == null ? "" : templateName;
        if (("sales-order".equals(moduleKey) || "sales-outbound".equals(moduleKey))
                && name.contains("A4")
                && name.contains("备注")) {
            return new CoordLayout(204, 24, 10, 0, 444, true);
        }
        return COORD_LAYOUTS.getOrDefault(moduleKey, new CoordLayout(130, 20, 50, 0, null, false));
    }

    private void enrichTopLevelPrintFields(Map<String, String> data) {
        LocalDateTime now = LocalDateTime.now();
        data.putIfAbsent("printDate", now.toLocalDate().toString());
        data.putIfAbsent("printTime", now.toString());
        putDateParts(data, resolveBillDate(data));
        formatDecimalField(data, "totalWeight", WEIGHT_SCALE);
        formatDecimalField(data, "totalFreight", PRICE_SCALE);
        formatDecimalField(data, "totalAmount", PRICE_SCALE);
        formatDecimalField(data, "paidAmount", PRICE_SCALE);
        formatDecimalField(data, "receiptAmount", PRICE_SCALE);
        formatDecimalField(data, "paymentAmount", PRICE_SCALE);
        data.putIfAbsent("billNo", firstPresent(data, "outboundNo", "orderNo", "billNo", "statementNo"));
    }

    private void putDateParts(Map<String, String> data, String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return;
        }
        String[] parts = rawDate.split("[T\\s]", 2)[0].split("[-/年/月/日]");
        if (parts.length < 3) {
            return;
        }
        data.put("dateYear", parts[0]);
        data.put("dateMonth", parts[1].length() == 1 ? "0" + parts[1] : parts[1]);
        data.put("dateDay", parts[2].length() == 1 ? "0" + parts[2] : parts[2]);
    }

    private String resolveBillDate(Map<String, String> data) {
        return firstPresent(data, "deliveryDate", "outboundDate", "orderDate", "inboundDate", "billTime", "endDate");
    }

    private String firstPresent(Map<String, String> data, String... keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private List<Map<String, String>> flattenItemsForLayout(String moduleKey, List<Map<String, String>> rawItems) {
        if (!"freight-bill".equals(moduleKey) || rawItems.isEmpty()) {
            return rawItems.stream().map(this::enrichItemPrintFields).toList();
        }

        Map<String, List<Map<String, String>>> groupMap = new java.util.LinkedHashMap<>();
        for (Map<String, String> item : rawItems) {
            String projectName = value(item, "projectName");
            groupMap.computeIfAbsent(projectName, key -> new ArrayList<>()).add(enrichItemPrintFields(item));
        }

        List<Map<String, String>> result = new ArrayList<>();
        groupMap.forEach((projectName, groupItems) -> {
            result.addAll(groupItems);
            if (!projectName.isBlank()) {
                Map<String, String> separator = new HashMap<>();
                separator.put("isSeparator", "true");
                separator.put("groupName", projectName);
                result.add(separator);
            }
        });
        return result;
    }

    private Map<String, String> enrichItemPrintFields(Map<String, String> item) {
        Map<String, String> enriched = new HashMap<>(item);
        if (COIL_CATEGORIES.contains(value(enriched, "category"))) {
            enriched.put("pieceWeightTon", "-");
        } else if (value(enriched, "pieceWeightTon").isBlank()) {
            BigDecimal quantity = decimal(value(enriched, "quantity"));
            BigDecimal weight = decimal(value(enriched, "weightTon"));
            if (quantity.compareTo(BigDecimal.ZERO) > 0 && weight.compareTo(BigDecimal.ZERO) > 0) {
                enriched.put("pieceWeightTon", weight.divide(quantity, WEIGHT_SCALE, RoundingMode.HALF_UP).toPlainString());
            }
        }
        formatDecimalField(enriched, "pieceWeightTon", WEIGHT_SCALE);
        formatDecimalField(enriched, "weightTon", WEIGHT_SCALE);
        formatDecimalField(enriched, "unitPrice", PRICE_SCALE);
        formatDecimalField(enriched, "amount", PRICE_SCALE);
        return enriched;
    }

    private void formatDecimalField(Map<String, String> row, String key, int scale) {
        String value = row.get(key);
        if (value == null || value.isBlank() || "-".equals(value)) {
            return;
        }
        BigDecimal decimal = decimal(value);
        if (decimal.compareTo(BigDecimal.ZERO) == 0) {
            row.put(key, "");
            return;
        }
        row.put(key, formatDecimal(decimal, scale));
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatDecimal(BigDecimal value, int scale) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? "" : value.stripTrailingZeros().toPlainString();
    }

    private Map<String, String> toCamelStringMap(Map<String, ?> row) {
        Map<String, String> result = new HashMap<>();
        for (var entry : row.entrySet()) {
            if (entry.getValue() != null) {
                result.put(toCamelCase(entry.getKey()), stringValue(entry.getValue()));
            }
        }
        return result;
    }

    private String toCamelCase(String key) {
        if (key == null || key.isBlank() || !key.contains("_")) {
            return key == null ? "" : key;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        return String.valueOf(value);
    }
}
