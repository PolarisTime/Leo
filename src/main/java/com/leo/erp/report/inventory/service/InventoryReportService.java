package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class InventoryReportService {

    private final InventoryReportQueryRepository inventoryReportQueryRepository;

    public InventoryReportService(InventoryReportQueryRepository inventoryReportQueryRepository) {
        this.inventoryReportQueryRepository = inventoryReportQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<InventoryReportResponse> page(PageQuery query, String keyword, String warehouseName, String category) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedWarehouseName = trimToNull(warehouseName);
        String normalizedCategory = trimToNull(category);
        return inventoryReportQueryRepository.page(query, normalizedKeyword, normalizedWarehouseName, normalizedCategory);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
