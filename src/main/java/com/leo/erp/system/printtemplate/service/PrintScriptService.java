package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrintScriptService {

    private static final Pattern SAFE_METHOD = Pattern.compile(
            "^\\s*LODOP\\.(SET_|ADD_|NewPage|NEWPAGE|SET_PRINT|SELECT_|DELETE_|PRINT_INIT|PRINT\\b|PREVIEW|PRINT_DESIGN|PRINT_SETUP)[A-Za-z_]*\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> DISALLOWED_METHODS = Set.of(
            "GET_FILE", "SEND_PRINT_RAWDATA", "WRITE_PORT_DATA"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern EACH_BLOCK = Pattern.compile(
            "\\{\\{#each\\s+(\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
    private static final Pattern IF_BLOCK = Pattern.compile(
            "\\{\\{#if\\s+(\\w+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);
    private static final Pattern JS_LINE = Pattern.compile(
            "^\\s*(var\\s|let\\s|const\\s|for\\s*\\(|if\\s*\\(|while\\s*\\(|function\\s|=>|\\})\\s*$)",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    // Layout parameters per bill_type for COORD templates
    private record CoordLayout(int tableTop, int rowH, int maxRows, int pageH) {}
    private static final Map<String, CoordLayout> COORD_LAYOUTS = Map.of(
            "sales-outbound", new CoordLayout(138, 24, 10, 0),
            "freight-bill", new CoordLayout(164, 20, 50, 0),
            "freight-statement", new CoordLayout(130, 20, 50, 0),
            "customer-statement", new CoordLayout(130, 20, 50, 1050)
    );

    private final PrintTemplateRepository templateRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PrintScriptService(PrintTemplateRepository templateRepository, JdbcTemplate jdbc,
                              ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Load record + items from DB, process template, return result based on template type. */
    public Map<String, String> generateFromRecord(String templateId, String moduleKey, Long recordId) {
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

        // Load line items
        List<Map<String, String>> items = loadItems(table, moduleKey, recordId);

        boolean isCoord = "COORD".equals(template.getTemplateType());

        if (isCoord) {
            String script = generateCoordScript(template, data, items, moduleKey);
            return Map.of("script", script, "type", "COORD");
        } else {
            String html = generate(templateId, data);
            return Map.of("html", html, "type", "HTML");
        }
    }

    private List<Map<String, String>> loadItems(String table, String moduleKey, Long recordId) {
        String itemTable = table + "_item";
        List<Map<String, String>> result = new ArrayList<>();
        try {
            var items = jdbc.queryForList(
                    "SELECT * FROM " + itemTable + " WHERE " +
                    (table.startsWith("po_") ? "order_id" :
                     table.startsWith("so_") ? "order_id" :
                     table.startsWith("ct_") ? "contract_id" :
                     table.startsWith("fm_") ? "receipt_id" :
                     table.startsWith("st_") ? "statement_id" :
                     "inbound_id") + " = ?", recordId);
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
            // items table may not exist
        }
        return result;
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

    // ─── COORD 脚本模板 ────────────────────────────────────

    /**
     * Generate pure LODOP script from a COORD template.
     * Handles {{#each}}, {{#if}}, {{fieldName}}, and removes JS artefacts.
     */
    public String generateCoordScript(PrintTemplate template, Map<String, String> data,
                                       List<Map<String, String>> rawItems, String moduleKey) {
        String source = template.getTemplateHtml();

        // 1. Enrich items with derived fields
        CoordLayout layout = COORD_LAYOUTS.getOrDefault(moduleKey,
                new CoordLayout(130, 20, 50, 0));
        List<Map<String, String>> items = enrichItems(rawItems, data, layout, moduleKey);
        data.put("_items_json", toJsonArray(rawItems));

        // 2. Add computed top-level fields
        enrichTopLevel(data);

        // 3. Expand {{#each details}}...{{/each}} blocks
        source = expandEachBlocks(source, items, layout);

        // 4. Expand {{#if field}}...{{/if}} blocks
        source = expandIfBlocks(source, data);

        // 5. Replace remaining {{fieldName}} placeholders
        source = PLACEHOLDER.matcher(source).replaceAll(mr -> {
            String key = mr.group(1);
            String value = data.getOrDefault(key, "");
            return escapeJs(value);
        });

        // 6. Remove JS artefact lines
        source = removeJsLines(source);

        // 7. Validate each LODOP call
        validateLodopCalls(source);

        return source;
    }

    // ─── 数据预处理 ──────────────────────────────────────────

    private void enrichTopLevel(Map<String, String> data) {
        LocalDateTime now = LocalDateTime.now();
        data.putIfAbsent("_printDate", DATE_FMT.format(now));
        data.putIfAbsent("_printTime", TIME_FMT.format(now));

        // BillNo label: "单据号:xxx" or "单据号："
        String outboundNo = data.getOrDefault("outboundNo", "");
        data.putIfAbsent("_billNoLabel", outboundNo.isEmpty() ? "单据号：" : "单据号:" + outboundNo);
        String outboundNoLabel = data.getOrDefault("outboundNoLabel", "");
        if (outboundNoLabel.isEmpty()) {
            data.putIfAbsent("outboundNoLabel", outboundNo.isEmpty() ? "" : outboundNo);
        }

        // Date parsing for A5
        String outboundDate = data.getOrDefault("outboundDate", "");
        if (!outboundDate.isEmpty()) {
            Matcher dm = Pattern.compile("(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})").matcher(outboundDate);
            if (dm.find()) {
                data.putIfAbsent("_dateYear", dm.group(1));
                data.putIfAbsent("_dateMonth", String.format("%02d", Integer.parseInt(dm.group(2))));
                data.putIfAbsent("_dateDay", String.format("%02d", Integer.parseInt(dm.group(3))));
            }
        }
    }

    private List<Map<String, String>> enrichItems(List<Map<String, String>> rawItems,
                                                   Map<String, String> data,
                                                   CoordLayout layout,
                                                   String moduleKey) {
        List<Map<String, String>> enriched = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalQuantity = 0;

        // For freight-bill: group by projectName
        boolean needsGrouping = "freight-bill".equals(moduleKey);
        final List<String> groupOrder = needsGrouping ? new ArrayList<>() : List.of();
        final Map<String, List<Map<String, String>>> groups = needsGrouping ? new java.util.LinkedHashMap<>() : Map.of();
        if (needsGrouping && !rawItems.isEmpty()) {
            for (var item : rawItems) {
                String pn = item.getOrDefault("projectName", "");
                groups.computeIfAbsent(pn, k -> {
                    groupOrder.add(k);
                    return new ArrayList<>();
                }).add(item);
            }
        }

        int seq = 1;
        List<Map<String, String>> flatItems;
        if (needsGrouping && groups != null && groupOrder != null) {
            flatItems = new ArrayList<>();
            for (String pn : groupOrder) {
                List<Map<String, String>> groupItems = groups.get(pn);
                for (var item : groupItems) {
                    flatItems.add(item);
                }
                // Add separator entry
                if (!pn.isEmpty()) {
                    Map<String, String> sep = new HashMap<>();
                    sep.put("_isSeparator", "true");
                    sep.put("_groupName", pn);
                    flatItems.add(sep);
                }
            }
        } else {
            flatItems = new ArrayList<>(rawItems);
        }

        int dataIdx = 0;
        int rowTop = layout.tableTop();
        String prevBillNo = null;
        int pageBreakH = layout.pageH();

        for (var item : flatItems) {
            Map<String, String> enrichedItem = new HashMap<>(item);

            if (!"true".equals(item.get("_isSeparator"))) {
                enrichedItem.put("_index", String.valueOf(seq++));
                dataIdx++;

                // Piece weight display
                String category = item.getOrDefault("category", "");
                boolean isCoil = "盘螺".equals(category) || "线材".equals(category);
                String pieceWeightDisplay = "-";
                if (!isCoil) {
                    BigDecimal w = safeDecimal(item.get("weightTon"));
                    BigDecimal q = safeDecimal(item.get("quantity"));
                    if (w.compareTo(BigDecimal.ZERO) > 0 && q.compareTo(BigDecimal.ZERO) > 0) {
                        pieceWeightDisplay = w.divide(q, 3, RoundingMode.HALF_UP).toPlainString();
                    }
                }
                enrichedItem.put("pieceWeightDisplay", pieceWeightDisplay);

                // Brand display (last 2 chars for A5)
                String brand = item.getOrDefault("brand", "");
                enrichedItem.put("brandDisplay", brand.length() > 2 ? brand.substring(brand.length() - 2) : brand);

                // Number formatting
                enrichedItem.put("weightTonDisplay", formatDecimal(item.get("weightTon"), 3));
                enrichedItem.put("unitPriceDisplay", formatDecimal(item.get("unitPrice"), 2));
                enrichedItem.put("amountDisplay", formatDecimal(item.get("amount"), 2));

                // Page break for A4 portrait
                if (pageBreakH > 0 && rowTop + layout.rowH() * 2 > pageBreakH) {
                    enrichedItem.put("_needsNewPage", "true");
                    rowTop = 20;
                }

                // BillNo separator for customer-statement-A4
                String billNo = item.getOrDefault("sourceNo", "");
                if (prevBillNo != null && !billNo.equals(prevBillNo) && !billNo.isEmpty()) {
                    enrichedItem.put("_needsSeparator", "true");
                }
                prevBillNo = billNo;

                // Totals
                totalWeight = totalWeight.add(safeDecimal(item.get("weightTon")));
                totalAmount = totalAmount.add(safeDecimal(item.get("amount")));
                totalQuantity += Integer.parseInt(item.getOrDefault("quantity", "0"));
            }

            enrichedItem.put("_rowTop", String.valueOf(rowTop));
            enriched.add(enrichedItem);
            rowTop += layout.rowH();
        }

        // Summary positions
        data.put("totalWeight", totalWeight.toPlainString());
        data.put("totalWeightDisplay", totalWeight.setScale(3, RoundingMode.HALF_UP).toPlainString());
        data.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        data.put("totalQuantity", String.valueOf(totalQuantity));

        int sumTop = layout.tableTop() + layout.maxRows() * layout.rowH();
        int itemCount = (int) flatItems.stream().filter(i -> !"true".equals(i.get("_isSeparator"))).count();
        int actualSumTop = layout.tableTop() + (needsGrouping ? flatItems.size() : Math.min(itemCount, layout.maxRows())) * layout.rowH();
        data.put("_sumTop", String.valueOf(needsGrouping ? actualSumTop : sumTop));
        data.put("_sumTop2", String.valueOf((needsGrouping ? actualSumTop : sumTop) + 2));

        // Footer positions
        int footerTop = (needsGrouping ? actualSumTop : sumTop) + 40;
        data.put("_footerTop", String.valueOf(footerTop));
        data.put("_footerLineTop", String.valueOf(footerTop + 28));
        data.put("_footerDateTop", String.valueOf(footerTop + 34));

        // Empty row handling
        boolean hasEmptyRows = !needsGrouping && itemCount < layout.maxRows();
        data.put("_hasEmptyRows", hasEmptyRows ? "true" : "");
        int emptyRowTop = layout.tableTop() + itemCount * layout.rowH();
        data.put("_emptyRowTop", String.valueOf(emptyRowTop));

        return enriched;
    }

    // ─── {{#each}} 展开 ────────────────────────────────────

    private String expandEachBlocks(String source, List<Map<String, String>> items, CoordLayout layout) {
        Matcher m = EACH_BLOCK.matcher(source);
        StringBuilder sb = new StringBuilder(source.length() + items.size() * 256);
        while (m.find()) {
            String inner = m.group(2);
            StringBuilder expanded = new StringBuilder();
            for (var item : items) {
                String row = PLACEHOLDER.matcher(inner).replaceAll(mr -> {
                    String key = mr.group(1);
                    return escapeJs(item.getOrDefault(key, ""));
                });
                expanded.append(row);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(expanded.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ─── {{#if}} 展开 ─────────────────────────────────────

    private String expandIfBlocks(String source, Map<String, String> data) {
        Matcher m = IF_BLOCK.matcher(source);
        StringBuilder sb = new StringBuilder(source.length());
        while (m.find()) {
            String field = m.group(1);
            String inner = m.group(2);
            String value = data.getOrDefault(field, "");
            if (!value.isEmpty() && !"false".equals(value) && !"0".equals(value)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(inner));
            } else {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ─── JS 行移除 ──────────────────────────────────────────

    private String removeJsLines(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append(line).append('\n');
            } else if (trimmed.startsWith("LODOP.") || trimmed.startsWith("{{") || trimmed.startsWith("<!--")) {
                sb.append(line).append('\n');
            } else if (!JS_LINE.matcher(trimmed).find()) {
                // Keep lines that don't look like JS
                sb.append(line).append('\n');
            }
            // else: skip JS lines (var, for, if, function, etc.)
        }
        return sb.toString().trim();
    }

    // ─── 校验 ──────────────────────────────────────────────

    private void validateLodopCalls(String script) {
        for (String line : script.split(";\\r?\\n?")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("LODOP.")) continue;
            if (!SAFE_METHOD.matcher(trimmed).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "模板包含不安全的调用: " + truncated(trimmed));
            }
            for (String disallowed : DISALLOWED_METHODS) {
                if (trimmed.toUpperCase(Locale.ROOT).contains(disallowed)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "模板包含被禁用的方法: " + disallowed);
                }
            }
        }
    }

    // ─── 工具方法 ───────────────────────────────────────────

    /** Escape for JS single-quoted strings. */
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

    private String toJsonArray(List<Map<String, String>> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var item : items) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            boolean firstField = true;
            for (var entry : item.entrySet()) {
                if (entry.getValue() == null) continue;
                if (!firstField) sb.append(",");
                firstField = false;
                sb.append("\"").append(entry.getKey()).append("\":\"")
                  .append(escapeJs(String.valueOf(entry.getValue()))).append("\"");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private BigDecimal safeDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String formatDecimal(String val, int scale) {
        BigDecimal d = safeDecimal(val);
        if (d.compareTo(BigDecimal.ZERO) == 0) return "";
        return d.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String truncated(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
