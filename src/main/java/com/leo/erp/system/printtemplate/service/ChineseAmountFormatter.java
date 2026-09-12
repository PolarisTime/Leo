package com.leo.erp.system.printtemplate.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 人民币金额转中文大写，用于打印单据。精确到分，支持到「万亿」级。
 * 例：1000 → 壹仟元整；250.50 → 贰佰伍拾元伍角；0.05 → 零元零伍分。
 */
final class ChineseAmountFormatter {

    private static final char[] DIGITS = {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};
    private static final String[] GROUP_UNITS = {"", "万", "亿", "万亿", "亿亿"};
    private static final String[] POSITION_UNITS = {"", "拾", "佰", "仟"};

    private ChineseAmountFormatter() {
    }

    /** 中文大写金额，如「壹仟元整」。 */
    static String toWords(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        boolean negative = value.signum() < 0;
        value = value.abs().setScale(2, RoundingMode.HALF_UP);

        long yuan = value.setScale(0, RoundingMode.DOWN).longValue();
        int cents = value.subtract(BigDecimal.valueOf(yuan)).movePointRight(2).intValue();
        int jiao = cents / 10;
        int fen = cents % 10;

        StringBuilder builder = new StringBuilder();
        if (negative) {
            builder.append('负');
        }
        builder.append(integerToWords(yuan)).append('元');
        if (jiao == 0 && fen == 0) {
            builder.append('整');
            return builder.toString();
        }
        if (jiao == 0) {
            builder.append('零');
        } else {
            builder.append(DIGITS[jiao]).append('角');
        }
        if (fen != 0) {
            builder.append(DIGITS[fen]).append('分');
        }
        return builder.toString();
    }

    /** 去掉无意义的小数位：1000.00 → 「1000」，250.50 → 「250.50」。 */
    static String plain(BigDecimal amount) {
        BigDecimal scaled = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        String text = scaled.toPlainString();
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    private static String integerToWords(long value) {
        if (value == 0) {
            return "零";
        }
        List<Integer> groups = new ArrayList<>();
        long remaining = value;
        while (remaining > 0) {
            groups.add((int) (remaining % 10000));
            remaining /= 10000;
        }
        StringBuilder builder = new StringBuilder();
        boolean pendingZero = false;
        for (int index = groups.size() - 1; index >= 0; index--) {
            int group = groups.get(index);
            if (group == 0) {
                if (builder.length() > 0) {
                    pendingZero = true;
                }
                continue;
            }
            if (builder.length() > 0 && (pendingZero || group < 1000)) {
                builder.append('零');
            }
            builder.append(groupToWords(group)).append(GROUP_UNITS[index]);
            pendingZero = false;
        }
        return builder.toString();
    }

    private static String groupToWords(int group) {
        StringBuilder builder = new StringBuilder();
        boolean zeroGap = false;
        boolean lowerNonZero = false;
        int position = 0;
        int remaining = group;
        while (remaining > 0) {
            int digit = remaining % 10;
            if (digit == 0) {
                if (lowerNonZero) {
                    zeroGap = true;
                }
            } else {
                if (zeroGap) {
                    builder.insert(0, '零');
                    zeroGap = false;
                }
                builder.insert(0, POSITION_UNITS[position]);
                builder.insert(0, DIGITS[digit]);
                lowerNonZero = true;
            }
            remaining /= 10;
            position++;
        }
        return builder.toString();
    }
}
