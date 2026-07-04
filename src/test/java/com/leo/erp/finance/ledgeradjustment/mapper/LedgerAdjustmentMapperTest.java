package com.leo.erp.finance.ledgeradjustment.mapper;

import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAdjustmentMapperTest {

    private final LedgerAdjustmentMapper mapper = new LedgerAdjustmentMapperImpl();

    @Test
    void shouldMapAdjustmentToResponse() {
        LedgerAdjustment entity = new LedgerAdjustment();
        entity.setId(1L);
        entity.setAdjustmentNo("LA-001");
        entity.setDirection("应收");
        entity.setCounterpartyType("客户");
        entity.setCounterpartyCode("C-001");
        entity.setCounterpartyName("客户A");
        entity.setProjectId(11L);
        entity.setProjectName("项目A");
        entity.setAdjustmentDate(LocalDate.of(2026, 6, 1));
        entity.setAmount(new BigDecimal("100.00"));
        entity.setAdjustmentType("坏账");
        entity.setEffect("减少余额");
        entity.setStatus("已审核");
        entity.setOperatorName("财务A");
        entity.setRemark("备注");

        LedgerAdjustmentResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.adjustmentNo()).isEqualTo("LA-001");
        assertThat(response.counterpartyCode()).isEqualTo("C-001");
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo("已审核");
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
