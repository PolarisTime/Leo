package com.leo.erp.statement.freight.service;

import com.leo.erp.logistics.api.FreightBillStatementReferenceQuery;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FreightBillStatementReferenceAdapter implements FreightBillStatementReferenceQuery {

    private final FreightStatementRepository freightStatementRepository;

    public FreightBillStatementReferenceAdapter(FreightStatementRepository freightStatementRepository) {
        this.freightStatementRepository = freightStatementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveStatementIds(Long sourceFreightBillId) {
        if (sourceFreightBillId == null) {
            return List.of();
        }
        return List.copyOf(
                freightStatementRepository.findActiveStatementIdsBySourceFreightBillId(sourceFreightBillId)
        );
    }
}
