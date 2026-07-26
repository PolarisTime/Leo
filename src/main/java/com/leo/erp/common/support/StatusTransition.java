package com.leo.erp.common.support;

/**
 * 一条不可变的单据状态迁移边。
 */
public record StatusTransition(String from, String to) {

    public StatusTransition {
        from = normalize(from, "起始状态");
        to = normalize(to, "目标状态");
    }

    public static StatusTransition of(String from, String to) {
        return new StatusTransition(from, to);
    }

    private static String normalize(String status, String fieldName) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return status.trim();
    }
}
