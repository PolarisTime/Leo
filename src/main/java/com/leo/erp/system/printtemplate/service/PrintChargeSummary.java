package com.leo.erp.system.printtemplate.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

final class PrintChargeSummary {

    private static final String PAYABLE = "PAYABLE";
    private static final String RECEIVABLE = "RECEIVABLE";
    private static final String INTERNAL = "INTERNAL";

    private PrintChargeSummary() {
    }

    static void applyTo(Map<String, String> variables, Map<String, List<Map<String, String>>> sections) {
        List<Map<String, String>> chargeItems = sections == null
                ? List.of()
                : sections.getOrDefault(PrintRecordData.CHARGE_ITEMS_SECTION, List.of());
        String defaultDirection = defaultDirection(variables);
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        for (Map<String, String> item : chargeItems) {
            if (!billable(item)) {
                continue;
            }
            String direction = value(item, "chargeDirection").trim().toUpperCase();
            if (INTERNAL.equals(direction) || !includedInDefaultSummary(direction, defaultDirection)) {
                continue;
            }
            BigDecimal amount = decimal(value(item, "amount"));
            if (PAYABLE.equals(direction)) {
                payable = payable.add(amount);
            } else if (RECEIVABLE.equals(direction)) {
                receivable = receivable.add(amount);
            }
        }

        BigDecimal totalCharge = payable.add(receivable);
        variables.put("totalChargeAmount", money(totalCharge));
        putAmountIfPresent(variables, "payableAmount", payableBase(variables).add(payable));
        putAmountIfPresent(variables, "receivableAmount", decimal(value(variables, "totalAmount")).add(receivable));
    }

    private static String defaultDirection(Map<String, String> variables) {
        return switch (value(variables, "moduleKey")) {
            case "purchase-order", "purchase-inbound", "freight-bill", "logistics" -> PAYABLE;
            case "sales-order", "sales-outbound" -> RECEIVABLE;
            default -> "";
        };
    }

    private static boolean includedInDefaultSummary(String direction, String defaultDirection) {
        if (!defaultDirection.isBlank()) {
            return defaultDirection.equals(direction);
        }
        return PAYABLE.equals(direction) || RECEIVABLE.equals(direction);
    }

    private static void putAmountIfPresent(Map<String, String> variables, String key, BigDecimal fallback) {
        String existing = value(variables, key);
        variables.put(key, existing.isBlank() ? money(fallback) : money(decimal(existing)));
    }

    private static BigDecimal payableBase(Map<String, String> variables) {
        BigDecimal freight = decimal(value(variables, "totalFreight"));
        if (freight.compareTo(BigDecimal.ZERO) > 0) {
            return freight;
        }
        return decimal(value(variables, "totalAmount"));
    }

    private static boolean billable(Map<String, String> item) {
        String billable = value(item, "billable");
        return billable.isBlank() || Boolean.parseBoolean(billable);
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(PrintRecordFieldFormatter.PRICE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static String value(Map<String, String> row, String key) {
        if (row == null) {
            return "";
        }
        String value = row.get(key);
        return value == null ? "" : value;
    }
}
