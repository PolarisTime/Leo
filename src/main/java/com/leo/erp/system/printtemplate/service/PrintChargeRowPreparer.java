package com.leo.erp.system.printtemplate.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把单据附加费用转换成可打印的「费用行」。
 *
 * <p>只有模板里显式声明了 {@code {{chargeName}}} 才追加费用行，因此 A4/PDF 等
 * 现有模板完全不受影响；追加的行按顺序排在货物明细之后，占用明细表里的空行
 * （A5 套打模板：类别打「件重」列、金额打「总重」列）。</p>
 *
 * <p>直接读 {@code bd_document_charge_item} 而不是依赖费用模块的仓库类：打印模块
 * 通过 SQL 读取其它模块的数据是既有约定（见 {@link PrintRecordDataProvider}），
 * 也避免跨模块直接依赖内部 repository。</p>
 */
@Component
class PrintChargeRowPreparer {

    /** 费用行标记：布局准备阶段据此把它放到空行，并且不计入合计与明细分页。 */
    static final String CHARGE_ROW_FIELD = "isChargeRow";

    private static final String CHARGE_NAME_PLACEHOLDER = "{{chargeName}}";

    private static final String CHARGE_ITEM_SQL = """
            SELECT charge_name, amount
              FROM bd_document_charge_item
             WHERE module_key = ? AND document_id = ? AND deleted_flag = FALSE
             ORDER BY line_no ASC, id ASC
            """;

    private final JdbcTemplate jdbc;

    PrintChargeRowPreparer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Map<String, String>> appendChargeRows(String moduleKey,
                                               Long recordId,
                                               String templateHtml,
                                               List<Map<String, String>> items) {
        if (recordId == null || templateHtml == null || !templateHtml.contains(CHARGE_NAME_PLACEHOLDER)) {
            return items;
        }
        List<Map<String, Object>> chargeItems = jdbc.queryForList(CHARGE_ITEM_SQL, moduleKey, recordId);
        if (chargeItems.isEmpty()) {
            return items;
        }
        List<Map<String, String>> rows = new ArrayList<>(items);
        for (Map<String, Object> chargeItem : chargeItems) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(CHARGE_ROW_FIELD, "true");
            row.put("chargeName", PrintItemFormatting.normalizeText(String.valueOf(chargeItem.getOrDefault("charge_name", ""))));
            row.put("chargeAmount", PrintItemFormatting.plainAmount(toAmount(chargeItem.get("amount"))));
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal toAmount(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
