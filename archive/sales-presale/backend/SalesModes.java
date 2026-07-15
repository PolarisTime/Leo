package com.leo.erp.sales.order.domain.entity;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Set;

/**
 * 已归档：销售订单预售模式已退役，生产代码不再编译或引用此类。
 */
public final class SalesModes {

    public static final String NORMAL = "NORMAL";
    public static final String PRESALE = "PRESALE";
    public static final Set<String> ALLOWED = Set.of(NORMAL, PRESALE);

    private SalesModes() {
    }

    public static String normalizeRequired(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售模式必须选择正常销售或预售");
        }
        return normalized;
    }
}
