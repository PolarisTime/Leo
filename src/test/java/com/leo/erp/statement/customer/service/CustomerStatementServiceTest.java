package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
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

class CustomerStatementServiceTest {

    @Test
    void shouldPersistRequestedReceiptAmountWithoutImmediateSync() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        StatementSettlementSyncService syncService = mock(StatementSettlementSyncService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement statement = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getSourceOrderNos(),
                    statement.getCustomerName(),
                    statement.getProjectName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getSalesAmount(),
                    statement.getReceiptAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                syncService,
                mock(WorkflowTransitionGuard.class)
        );

        CustomerStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.salesAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.receiptAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldRejectOverReceiptAmount() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);

        CustomerStatementService service = new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(CustomerStatementMapper.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1200.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售金额不能低于已收款金额");
    }

    private CustomerStatementRequest buildRequest(BigDecimal receiptAmount) {
        return new CustomerStatementRequest(
                "KHDZ-001",
                "SO-001",
                "客户甲",
                "项目A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 6),
                new BigDecimal("1000.00"),
                receiptAmount,
                null,
                "待确认",
                "备注",
                List.of(new CustomerStatementItemRequest(
                        "SO-001",
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
