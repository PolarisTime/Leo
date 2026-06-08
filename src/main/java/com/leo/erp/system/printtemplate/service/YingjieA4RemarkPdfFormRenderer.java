package com.leo.erp.system.printtemplate.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class YingjieA4RemarkPdfFormRenderer implements PdfFormRenderer {

    public static final String FORM_CODE = "YINGJIE_A4_REMARK";
    private static final String TEMPLATE_PATH = "print-forms/yingjie-a4-remark.pdf";
    private static final int MAX_DETAIL_ROWS = 10;

    @Override
    public String formCode() {
        return FORM_CODE;
    }

    @Override
    public String defaultTemplatePath() {
        return TEMPLATE_PATH;
    }

    @Override
    public Map<String, String> buildFields(Map<String, String> data, List<Map<String, String>> items) {
        Map<String, String> values = new HashMap<>();
        values.put("remark", value(data, "remark"));
        values.put("customerName", value(data, "customerName"));
        values.put("billNo", resolveBillNo(data));
        values.put("projectName", value(data, "projectName"));
        values.put("billDate", resolveBillDate(data));
        values.put("projectAddress", value(data, "projectAddress"));
        values.put("vehiclePlate", value(data, "vehiclePlate"));

        Totals totals = new Totals();
        int rowCount = Math.min(items.size(), MAX_DETAIL_ROWS);
        for (int i = 0; i < MAX_DETAIL_ROWS; i++) {
            if (i < rowCount) {
                putItemFields(values, i, items.get(i), totals);
            } else if (i == rowCount) {
                values.put(itemField(i, "emptyMarker"), "----------------以下无内容----------------");
            }
        }
        String totalQuantity = formatTotalQuantity(totals.quantity);
        String totalWeight = formatTotalWeight(totals.weight);
        values.put("totalQuantity", totalQuantity);
        values.put("totalWeight", totalWeight);
        values.put("totalSummary", totalSummary(totalQuantity, totalWeight));
        return values;
    }

    private void putItemFields(Map<String, String> values, int rowIndex, Map<String, String> item, Totals totals) {
        String quantity = value(item, "quantity");
        String weight = displayWeight(item);
        totals.quantity = totals.quantity.add(decimal(quantity));
        totals.weight = totals.weight.add(decimal(weight));

        values.put(itemField(rowIndex, "brand"), value(item, "brand"));
        values.put(itemField(rowIndex, "category"), value(item, "category"));
        values.put(itemField(rowIndex, "material"), value(item, "material"));
        values.put(itemField(rowIndex, "spec"), value(item, "spec"));
        values.put(itemField(rowIndex, "length"), value(item, "length"));
        values.put(itemField(rowIndex, "quantity"), quantity);
        values.put(itemField(rowIndex, "pieceWeight"), displayPieceWeight(item));
        values.put(itemField(rowIndex, "weight"), weight);
    }

    private String itemField(int rowIndex, String fieldName) {
        return "item_" + rowIndex + "_" + fieldName;
    }

    private String resolveBillNo(Map<String, String> data) {
        String outboundNo = value(data, "outboundNo");
        if (!outboundNo.isBlank()) {
            return outboundNo;
        }
        String orderNo = value(data, "orderNo");
        if (!orderNo.isBlank()) {
            return orderNo;
        }
        return value(data, "billNo");
    }

    private String resolveBillDate(Map<String, String> data) {
        return formatChineseDate(value(data, "deliveryDate", "outboundDate", "orderDate"));
    }

    private String formatChineseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "";
        }
        String datePart = rawDate.trim().split("[T\\s]", 2)[0];
        if (datePart.contains("年")) {
            return datePart;
        }
        String[] parts = datePart.split("[-/]");
        if (parts.length < 3) {
            return rawDate;
        }
        String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
        String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
        return parts[0] + "年" + month + "月" + day + "日";
    }

    private String displayPieceWeight(Map<String, String> item) {
        return value(item, "pieceWeightTon");
    }

    private String displayWeight(Map<String, String> item) {
        return value(item, "weightTon");
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatTotalWeight(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTotalQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String totalSummary(String totalQuantity, String totalWeight) {
        if (totalQuantity.isBlank() && totalWeight.isBlank()) {
            return "";
        }
        String quantity = totalQuantity.isBlank() ? "0" : totalQuantity;
        String weight = totalWeight.isBlank() ? "0" : totalWeight;
        return "合计件数：" + quantity + "    合计重量：" + weight + " 吨";
    }

    private String value(Map<String, String> data, String... keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static class Totals {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal weight = BigDecimal.ZERO;
    }
}
