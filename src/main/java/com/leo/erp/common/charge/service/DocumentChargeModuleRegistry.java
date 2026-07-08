package com.leo.erp.common.charge.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Map;
import java.util.Set;

public final class DocumentChargeModuleRegistry {

    private static final Map<String, String> SETTLEMENT_DIRECTIONS = Map.of(
            "purchase-order", "PAYABLE",
            "purchase-inbound", "PAYABLE",
            "sales-order", "RECEIVABLE",
            "sales-outbound", "RECEIVABLE",
            "freight-bill", "PAYABLE"
    );

    private DocumentChargeModuleRegistry() {
    }

    public static String requireSupported(String moduleKey) {
        String normalized = normalize(moduleKey);
        if (normalized == null || !SETTLEMENT_DIRECTIONS.containsKey(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持费用明细");
        }
        return normalized;
    }

    public static String settlementDirection(String moduleKey) {
        return SETTLEMENT_DIRECTIONS.get(requireSupported(moduleKey));
    }

    public static Set<String> supportedModuleKeys() {
        return SETTLEMENT_DIRECTIONS.keySet();
    }

    private static String normalize(String moduleKey) {
        if (moduleKey == null) {
            return null;
        }
        String normalized = moduleKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
