package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                mock(SalesOrderRepository.class),
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
                mock(SalesOrderRepository.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1200.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售金额不能低于已收款金额");
    }

    @Test
    void shouldMarkDeletedStatementStatusWhenDeleting() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setStatementNo("KHDZ-DELETE-001");
        statement.setStatus("待确认");
        statement.setDeletedFlag(Boolean.FALSE);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatementService service = new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        assertThat(statement.getDeletedFlag()).isTrue();
        assertThat(statement.getStatus()).isEqualTo("已删除");
        verify(repository).save(argThat(saved ->
                Boolean.TRUE.equals(saved.getDeletedFlag()) && "已删除".equals(saved.getStatus())
        ));
    }

    @Test
    void shouldExposePendingFinalizeSalesOrdersAsStatementCandidates() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        StatementSettlementSyncService syncService = mock(StatementSettlementSyncService.class);

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(1L);
        salesOrder.setOrderNo("SO-001");
        salesOrder.setCustomerName("客户甲");
        salesOrder.setProjectName("项目A");
        salesOrder.setDeliveryDate(LocalDate.of(2026, 5, 6));
        salesOrder.setSalesName("张三");
        salesOrder.setStatus("待完善");
        salesOrder.setTotalWeight(new BigDecimal("1.000"));
        salesOrder.setTotalAmount(new BigDecimal("1000.00"));

        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(salesOrder))
        );

        CustomerStatementService service = new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                salesOrderRepository,
                syncService,
                mock(WorkflowTransitionGuard.class)
        );

        List<CustomerStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), "")
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).orderNo()).isEqualTo("SO-001");
        assertThat(candidates.get(0).status()).isEqualTo("待完善");
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
