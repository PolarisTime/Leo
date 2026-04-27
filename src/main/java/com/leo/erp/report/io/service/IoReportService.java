package com.leo.erp.report.io.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.report.io.repository.IoReportQueryRepository;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class IoReportService {

    private static final Set<String> ALLOWED_BUSINESS_TYPES = Set.of("采购入库", "销售出库");

    private final IoReportQueryRepository ioReportQueryRepository;

    public IoReportService(IoReportQueryRepository ioReportQueryRepository) {
        this.ioReportQueryRepository = ioReportQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<IoReportResponse> page(PageQuery query, String keyword, String businessType, LocalDate startDate, LocalDate endDate) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedBusinessType = normalizeBusinessType(businessType);
        validateDateRange(startDate, endDate);
        return ioReportQueryRepository.page(query, normalizedKeyword, normalizedBusinessType, startDate, endDate);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBusinessType(String businessType) {
        if (businessType == null || businessType.isBlank()) {
            return null;
        }
        String normalized = businessType.trim();
        if (!ALLOWED_BUSINESS_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "businessType 不合法");
        }
        return normalized;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startDate 不能晚于 endDate");
        }
    }
}
