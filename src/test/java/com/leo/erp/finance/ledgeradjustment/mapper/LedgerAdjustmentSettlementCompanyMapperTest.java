package com.leo.erp.finance.ledgeradjustment.mapper;

import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAdjustmentSettlementCompanyMapperTest {

    @Test
    void shouldMapSettlementCompanySnapshotToResponse() {
        LedgerAdjustment adjustment = new LedgerAdjustment();
        adjustment.setSettlementCompanyId(31L);
        adjustment.setSettlementCompanyName("结算主体A");

        var response = new LedgerAdjustmentMapperImpl().toResponse(adjustment);

        assertThat(response.settlementCompanyId()).isEqualTo(31L);
        assertThat(response.settlementCompanyName()).isEqualTo("结算主体A");
    }
}
