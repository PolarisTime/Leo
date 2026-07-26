package com.leo.erp.statement.freight.service;

import com.leo.erp.statement.api.FreightStatementApi;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FreightStatementApiAdapter implements FreightStatementApi {

    private final FreightStatementQueryService queryService;

    public FreightStatementApiAdapter(FreightStatementQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public Optional<Snapshot> findActiveById(Long statementId) {
        return queryService.findActiveById(statementId).map(this::toSnapshot);
    }

    @Override
    public Snapshot requireActiveById(Long statementId) {
        return toSnapshot(queryService.requireActiveById(statementId));
    }

    private Snapshot toSnapshot(FreightStatement statement) {
        return new Snapshot(
                statement.getId(),
                statement.getStatementNo(),
                statement.getCarrierId(),
                statement.getCarrierCode(),
                statement.getCarrierName(),
                statement.getSettlementCompanyId(),
                statement.getSettlementCompanyName(),
                statement.getTotalFreight(),
                statement.getUnpaidAmount(),
                statement.getStatus()
        );
    }
}
