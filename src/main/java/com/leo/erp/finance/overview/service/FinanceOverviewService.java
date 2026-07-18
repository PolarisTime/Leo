package com.leo.erp.finance.overview.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.overview.repository.FinanceOverviewFilter;
import com.leo.erp.finance.overview.repository.FinanceOverviewQueryRepository;
import com.leo.erp.finance.overview.web.dto.FinanceOverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class FinanceOverviewService {

    private static final Set<String> DIRECTIONS = Set.of("RECEIVABLE", "PAYABLE");
    private static final Set<String> COUNTERPARTY_TYPES = Set.of("客户", "供应商", "物流商");

    private final FinanceOverviewQueryRepository queryRepository;

    public FinanceOverviewService(FinanceOverviewQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public FinanceOverviewResponse overview(PageQuery query,
                                            Long settlementCompanyId,
                                            LocalDate asOfDate,
                                            String direction,
                                            String counterpartyType,
                                            String keyword,
                                            boolean onlyOpen) {
        if (settlementCompanyId == null || settlementCompanyId <= 0L) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体ID必须为正数");
        }
        String normalizedDirection = normalizeDirection(direction);
        String normalizedCounterpartyType = trimToNull(counterpartyType);
        if (normalizedCounterpartyType != null && !COUNTERPARTY_TYPES.contains(normalizedCounterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "往来方类型不合法");
        }
        if ("RECEIVABLE".equals(normalizedDirection)
                && normalizedCounterpartyType != null
                && !"客户".equals(normalizedCounterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收方向仅支持客户往来方");
        }
        if ("PAYABLE".equals(normalizedDirection) && "客户".equals(normalizedCounterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应付方向不支持客户往来方");
        }

        FinanceOverviewFilter filter = new FinanceOverviewFilter(
                settlementCompanyId,
                asOfDate == null ? LocalDate.now() : asOfDate,
                normalizedDirection,
                normalizedCounterpartyType,
                trimToNull(keyword),
                onlyOpen
        );
        FinanceOverviewQueryRepository.OverviewResult result = queryRepository.overview(filter, query);
        return new FinanceOverviewResponse(
                filter.asOfDate(),
                result.summary(),
                PageResponse.from(result.balances())
        );
    }

    private String normalizeDirection(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!DIRECTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "财务方向不合法");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
