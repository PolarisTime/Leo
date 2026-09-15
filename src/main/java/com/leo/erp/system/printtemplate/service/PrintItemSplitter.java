package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.PrecisionConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印明细「按每份件数拆分」工具。
 *
 * <p>当 {@code splitPieceCount = N}（N ≥ 1）且明细数量大于 N 时，把一条明细拆成多行：
 * 每行 N 件，无法整除的余数并入最后一行（例：数量 100、N=25 → 25/25/25/25；
 * 数量 103、N=25 → 25/25/25/28）。</p>
 *
 * <p>重量与金额按「该份数量 / 原数量」比例缩放。为保证拆分后各份之和与原值一致，
 * 采用累计舍入法：第 i 份 = round(原值 × 累计数量_i / 原数量) − round(原值 × 累计数量_{i-1} / 原数量)。
 * 相邻项相消后，各份之和恰好等于 round(原值)。</p>
 */
public final class PrintItemSplitter {

    static final String QUANTITY_FIELD = "quantity";
    static final String WEIGHT_FIELD = "weightTon";
    static final String AMOUNT_FIELD = "amount";
    static final String SPLIT_INDEX_FIELD = "_splitIndex";
    static final String SPLIT_TOTAL_FIELD = "_splitTotal";

    private static final int WEIGHT_SCALE = PrecisionConstants.WEIGHT_SCALE;
    private static final int AMOUNT_SCALE = PrecisionConstants.AMOUNT_SCALE;

    private PrintItemSplitter() {
    }

    /**
     * 拆分 Map 形式的打印明细。未开启拆分（null / &lt;1）、明细为空或单条无需拆分时原样保留。
     */
    public static List<Map<String, String>> splitItems(List<Map<String, String>> items, Integer splitPieceCount) {
        if (!isActive(splitPieceCount) || items == null || items.isEmpty()) {
            return items;
        }
        List<Map<String, String>> result = new ArrayList<>(items.size());
        boolean changed = false;
        for (Map<String, String> item : items) {
            List<Map<String, String>> rows = splitItem(item, splitPieceCount);
            if (rows.size() != 1 || rows.get(0) != item) {
                changed = true;
            }
            result.addAll(rows);
        }
        return changed ? result : items;
    }

    /**
     * 计算拆分后的份列表。数量缺失、非正、≤ N 或拆分后只有一份时返回 {@code null}（表示不拆）。
     *
     * @param totalQuantity 原数量，必须为正数
     * @param totalWeight   原重量，为 {@code null} 时不缩放
     * @param totalAmount   原金额，为 {@code null} 时不缩放
     */
    public static List<Part> split(
            BigDecimal totalQuantity,
            BigDecimal totalWeight,
            BigDecimal totalAmount,
            Integer splitPieceCount
    ) {
        if (!isActive(splitPieceCount) || totalQuantity == null || totalQuantity.signum() <= 0) {
            return null;
        }
        BigDecimal pieceCount = BigDecimal.valueOf(splitPieceCount);
        if (totalQuantity.compareTo(pieceCount) <= 0) {
            return null;
        }

        List<BigDecimal> quantities = new ArrayList<>();
        BigDecimal remaining = totalQuantity;
        while (remaining.compareTo(pieceCount) >= 0) {
            quantities.add(pieceCount);
            remaining = remaining.subtract(pieceCount);
        }
        if (quantities.size() <= 1) {
            return null;
        }
        // 余数并入最后一行，保证各份数量之和与原数量一致
        int lastIndex = quantities.size() - 1;
        quantities.set(lastIndex, quantities.get(lastIndex).add(remaining));

        int total = quantities.size();
        List<Part> parts = new ArrayList<>(total);
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal previousWeight = BigDecimal.ZERO;
        BigDecimal previousAmount = BigDecimal.ZERO;
        for (int index = 0; index < total; index += 1) {
            cumulative = cumulative.add(quantities.get(index));
            BigDecimal cumulativeWeight = scaledCumulative(totalWeight, cumulative, totalQuantity, WEIGHT_SCALE);
            BigDecimal cumulativeAmount = scaledCumulative(totalAmount, cumulative, totalQuantity, AMOUNT_SCALE);
            parts.add(new Part(
                    index + 1,
                    total,
                    quantities.get(index),
                    totalWeight == null ? null : cumulativeWeight.subtract(previousWeight),
                    totalAmount == null ? null : cumulativeAmount.subtract(previousAmount)
            ));
            previousWeight = cumulativeWeight;
            previousAmount = cumulativeAmount;
        }
        return parts;
    }

    private static List<Map<String, String>> splitItem(Map<String, String> item, int splitPieceCount) {
        BigDecimal totalQuantity = decimal(item.get(QUANTITY_FIELD));
        BigDecimal totalWeight = decimal(item.get(WEIGHT_FIELD));
        BigDecimal totalAmount = decimal(item.get(AMOUNT_FIELD));
        List<Part> parts = split(totalQuantity, totalWeight, totalAmount, splitPieceCount);
        if (parts == null) {
            return List.of(item);
        }
        List<Map<String, String>> rows = new ArrayList<>(parts.size());
        for (Part part : parts) {
            Map<String, String> row = new LinkedHashMap<>(item);
            row.put(QUANTITY_FIELD, plainQuantity(part.quantity()));
            if (totalWeight != null) {
                row.put(WEIGHT_FIELD, fixed(part.weight(), WEIGHT_SCALE));
            } else {
                row.remove(WEIGHT_FIELD);
            }
            if (totalAmount != null) {
                row.put(AMOUNT_FIELD, fixed(part.amount(), AMOUNT_SCALE));
            } else {
                row.remove(AMOUNT_FIELD);
            }
            row.put(SPLIT_INDEX_FIELD, String.valueOf(part.index()));
            row.put(SPLIT_TOTAL_FIELD, String.valueOf(part.total()));
            rows.add(row);
        }
        return rows;
    }

    private static boolean isActive(Integer splitPieceCount) {
        return splitPieceCount != null && splitPieceCount >= 1;
    }

    private static BigDecimal scaledCumulative(
            BigDecimal total,
            BigDecimal cumulative,
            BigDecimal totalQuantity,
            int scale
    ) {
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total.multiply(cumulative).divide(totalQuantity, scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String plainQuantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String fixed(BigDecimal value, int scale) {
        return value == null ? "" : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /** 拆分后的一份明细：份序号从 1 开始，数量为整数。 */
    public record Part(int index, int total, BigDecimal quantity, BigDecimal weight, BigDecimal amount) {
    }
}
