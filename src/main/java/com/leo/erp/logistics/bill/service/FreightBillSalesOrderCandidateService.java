package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.logistics.bill.repository.FreightBillSalesOrderCandidateQueryRepository;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FreightBillSalesOrderCandidateService {

    private final SalesOrderLogisticsSourceQuery salesOrderSourceQuery;
    private final FreightBillSalesOrderCandidateQueryRepository candidateQueryRepository;

    public FreightBillSalesOrderCandidateService(
            SalesOrderLogisticsSourceQuery salesOrderSourceQuery,
            FreightBillSalesOrderCandidateQueryRepository candidateQueryRepository
    ) {
        this.salesOrderSourceQuery = salesOrderSourceQuery;
        this.candidateQueryRepository = candidateQueryRepository;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<SalesOrderSourceSnapshot> page(PageQuery query, PageFilter filter) {
        Page<Long> candidateIds = candidateQueryRepository.pageIds(query, filter);
        Map<Long, SalesOrderSourceSnapshot> candidateById = salesOrderSourceQuery
                .findByOrderIds(candidateIds.getContent())
                .stream()
                .collect(Collectors.toMap(SalesOrderSourceSnapshot::id, snapshot -> snapshot));
        List<SalesOrderSourceSnapshot> candidates = candidateIds.getContent().stream()
                .map(candidateById::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageResponse<>(
                candidates,
                candidateIds.getTotalElements(),
                candidateIds.getTotalPages(),
                candidateIds.getNumber(),
                candidateIds.getSize(),
                candidateIds.hasNext()
        );
    }
}
