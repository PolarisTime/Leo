package com.leo.erp.finance.receivablepayable.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ReceivablePayableService {

    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("应收", "应付");
    private static final Set<String> ALLOWED_COUNTERPARTY_TYPES = Set.of("客户", "供应商", "物流商");

    private final ReceivablePayableQueryRepository queryRepository;

    public ReceivablePayableService(ReceivablePayableQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ReceivablePayableResponse> page(PageQuery query, String businessDirection, String counterpartyType, String keyword) {
        String normalizedDirection = validateDirection(businessDirection);
        String normalizedCounterpartyType = validateCounterpartyType(counterpartyType);
        return queryRepository.page(query, normalizedDirection, normalizedCounterpartyType, keyword);
    }

    private String validateDirection(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_DIRECTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "direction 不合法");
        }
        return normalized;
    }

    private String validateCounterpartyType(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_COUNTERPARTY_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "counterpartyType 不合法");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
