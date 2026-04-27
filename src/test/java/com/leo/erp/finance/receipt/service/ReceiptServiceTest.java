package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    @Test
    void shouldRejectMissingCustomerStatementForReceipt() {
        ReceiptService service = new ReceiptService(
                mock(ReceiptRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class),
                mock(StatementSettlementSyncService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(null, "客户A", "项目A", new BigDecimal("100.00"), "已收款")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单必须关联客户对账单");
    }

    @Test
    void shouldRejectOverReceiptAgainstCustomerStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(customerStatementQueryService.findActiveById(21L)).thenReturn(Optional.of(statement));
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(receiptRepository.sumAmountBySourceStatementIdAndStatusExcludingId(
                eq(21L),
                eq(StatementSettlementSyncService.RECEIPT_STATUS_SETTLED),
                anyLong()
        )).thenReturn(new BigDecimal("950.00"));

        ReceiptService service = new ReceiptService(
                receiptRepository,
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(StatementSettlementSyncService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(21L, "客户A", "项目A", new BigDecimal("100.00"), "已收款")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计收款金额不能超过销售金额");
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
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(StatementSettlementSyncService.class),
                resourceRecordAccessGuard
        );

        assertThatThrownBy(() -> service.create(buildRequest(21L, "客户A", "项目A", new BigDecimal("100.00"), "已收款")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    private ReceiptRequest buildRequest(Long sourceStatementId,
                                        String customerName,
                                        String projectName,
                                        BigDecimal amount,
                                        String status) {
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
                null
        );
    }
}
