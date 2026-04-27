package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class InventoryReportService {

    private static final Set<String> ALLOWED_WAREHOUSE_NAMES = Set.of("一号库", "二号库");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("螺纹钢", "盘螺", "线材");

    private final InventoryReportQueryRepository inventoryReportQueryRepository;

    public InventoryReportService(InventoryReportQueryRepository inventoryReportQueryRepository) {
        this.inventoryReportQueryRepository = inventoryReportQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<InventoryReportResponse> page(PageQuery query, String keyword, String warehouseName, String category) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedWarehouseName = normalizeFilterValue(warehouseName, "warehouseName", ALLOWED_WAREHOUSE_NAMES);
        String normalizedCategory = normalizeFilterValue(category, "category", ALLOWED_CATEGORIES);
        return inventoryReportQueryRepository.page(query, normalizedKeyword, normalizedWarehouseName, normalizedCategory);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFilterValue(String value, String fieldName, Set<String> allowedValues) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法");
        }
        return normalized;
    }
}
