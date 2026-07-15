package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.sales.order.repository.SalesOrderSourceCandidateQueryRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class SalesOrderSourceCandidateService {

    private final SalesOrderSourceCandidateQueryRepository repository;

    public SalesOrderSourceCandidateService(SalesOrderSourceCandidateQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSourceCandidateResponse> page(
            String keyword,
            Long supplierId,
            Long settlementCompanyId,
            LocalDate startDate,
            LocalDate endDate,
            Long currentSalesOrderId,
            PageQuery query
    ) {
        return repository.page(
                keyword,
                supplierId,
                settlementCompanyId,
                startDate,
                endDate,
                currentSalesOrderId,
                query
        );
    }
}
