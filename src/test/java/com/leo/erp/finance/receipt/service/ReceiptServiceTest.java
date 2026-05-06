package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    @Test
    void shouldAllowReceiptWithoutStatementAllocations() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptMapper.toResponse(any(Receipt.class))).thenReturn(new ReceiptResponse(
                1L,
                "SK-001",
                "客户A",
                "项目A",
                null,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                new BigDecimal("100.00"),
                "已收款",
                "财务A",
                null,
                List.of()
        ));

        ReceiptService service = new ReceiptService(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                receiptMapper,
                mock(CustomerStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatCode(() -> service.create(buildRequest(null, "客户A", "项目A", new BigDecimal("100.00"), "已收款", List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOverReceiptAgainstCustomerStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                eq(21L),
                eq(StatusConstants.RECEIVED),
                anyLong()
        )).thenReturn(new BigDecimal("950.00"));

        ReceiptService service = new ReceiptService(
                receiptRepository,
                allocationRepository,
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计收款金额不能超过销售金额");
    }

    @Test
    void shouldRejectAllocationAmountExceedingReceiptAmount() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = new ReceiptService(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("120.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过收款金额");
    }

    @Test
    void shouldRejectCustomerStatementOutsideDataScope() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("customer-statements", "read", statement);

        ReceiptService service = new ReceiptService(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    private ReceiptRequest buildRequest(Long sourceStatementId,
                                        String customerName,
                                        String projectName,
                                        BigDecimal amount,
                                        String status,
                                        List<ReceiptAllocationRequest> items) {
        return new ReceiptRequest(
                "SK-001",
                customerName,
                projectName,
                sourceStatementId,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                amount,
                status,
                "财务A",
                null,
                items
        );
    }
}
