package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupplierStatementServiceTest {

    @Test
    void shouldPersistRequestedPaymentAmountWithoutImmediateSync() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        StatementSettlementSyncService syncService = mock(StatementSettlementSyncService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(SupplierStatement.class))).thenAnswer(invocation -> {
            SupplierStatement statement = invocation.getArgument(0);
            return new SupplierStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getSourceInboundNos(),
                    statement.getSupplierName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getPurchaseAmount(),
                    statement.getPaymentAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                syncService,
                mock(WorkflowTransitionGuard.class)
        );

        SupplierStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.purchaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.paymentAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldRejectOverPaymentAmount() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1200.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购金额不能低于已付款金额");
    }

    private SupplierStatementRequest buildRequest(BigDecimal paymentAmount) {
        return new SupplierStatementRequest(
                "GYDZ-001",
                "IN-001",
                "供应商甲",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 6),
                new BigDecimal("1000.00"),
                paymentAmount,
                null,
                "待确认",
                "备注",
                List.of(new SupplierStatementItemRequest(
                        "IN-001",
                        "M-001",
                        "品牌A",
                        "螺纹",
                        "盘螺",
                        "HRB400",
                        "12",
                        "吨",
                        "LOT-001",
                        1,
                        "件",
                        new BigDecimal("1.000"),
                        1,
                        new BigDecimal("1.000"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                ))
        );
    }
}
