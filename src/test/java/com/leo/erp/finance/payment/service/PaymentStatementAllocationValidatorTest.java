package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentStatementAllocationValidatorTest {

    @Test
    void shouldValidateSupplierStatementAndReturnSupplierCode() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SupplierStatement statement = supplierStatement();
        when(supplierQueryService.requireActiveById(11L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                accessGuard
        );

        PaymentStatementAllocationValidator.ValidatedStatement validatedStatement = validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        );

        assertThat(validatedStatement.counterpartyCode()).isEqualTo("S-001");
        assertThat(validatedStatement.settlementCompanyId()).isEqualTo(1001L);
        assertThat(validatedStatement.settlementCompanyName()).isEqualTo("结算主体A");
        verify(accessGuard).assertCurrentUserCanAccess(
                "supplier-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
    }

    @Test
    void shouldUseSupplierCodeAsIdentityWhenDisplayNameSnapshotDiffers() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(supplierStatement());
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        PaymentStatementAllocationValidator.ValidatedStatement validatedStatement = validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商历史名称"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        );

        assertThat(validatedStatement.counterpartyCode()).isEqualTo("S-001");
    }

    @Test
    void shouldRejectSupplierStatementWithoutStableSupplierCode() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = supplierStatement();
        statement.setSupplierCode(null);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码不能为空");
    }

    @Test
    void shouldRejectSupplierStatementWithoutSettlementCompanyId() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = supplierStatement();
        statement.setSettlementCompanyId(null);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单结算主体不能为空");
    }

    @Test
    void shouldRejectSupplierStatementWithoutSettlementCompanyName() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = supplierStatement();
        statement.setSettlementCompanyName(" ");
        when(supplierQueryService.requireActiveById(11L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单结算主体名称不能为空");
    }

    @Test
    void shouldRejectDuplicateSupplierStatement() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(supplierStatement());
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );
        HashMap<Long, BigDecimal> allocatedAmountMap = new HashMap<>();
        allocatedAmountMap.put(11L, new BigDecimal("50.00"));

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                StatusConstants.DRAFT,
                1L,
                11L,
                new BigDecimal("50.00"),
                allocatedAmountMap,
                2
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一付款单不能重复核销同一供应商对账单");
    }

    @Test
    void shouldRejectSupplierStatementOverPayment() {
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(supplierStatement());
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                11L,
                PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                PaymentAllocationService.PAYMENT_STATUS_SETTLED,
                1L
        )).thenReturn(new BigDecimal("950.00"));
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                allocationRepository,
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, "S-001", "供应商A"),
                PaymentAllocationService.PAYMENT_STATUS_SETTLED,
                1L,
                11L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联供应商对账单累计付款金额不能超过采购金额");
    }

    @Test
    void shouldValidateFreightStatementAndReturnCarrierCode() {
        FreightStatementQueryService freightQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = freightStatement();
        when(freightQueryService.requireActiveById(31L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                mock(SupplierStatementQueryService.class),
                freightQueryService,
                mock(ResourceRecordAccessGuard.class)
        );

        PaymentStatementAllocationValidator.ValidatedStatement validatedStatement = validator.validate(
                paymentRequest(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, "C-001", "物流商A"),
                StatusConstants.DRAFT,
                1L,
                31L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        );

        assertThat(validatedStatement.counterpartyCode()).isEqualTo("C-001");
        assertThat(validatedStatement.settlementCompanyId()).isEqualTo(1001L);
        assertThat(validatedStatement.settlementCompanyName()).isEqualTo("结算主体A");
    }

    @Test
    void shouldRejectFreightStatementWithoutSettlementCompanyId() {
        FreightStatementQueryService freightQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = freightStatement();
        statement.setSettlementCompanyId(null);
        when(freightQueryService.requireActiveById(31L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                mock(SupplierStatementQueryService.class),
                freightQueryService,
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, "C-001", "物流商A"),
                StatusConstants.DRAFT,
                1L,
                31L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单结算主体不能为空");
    }

    @Test
    void shouldRejectFreightStatementWithoutSettlementCompanyName() {
        FreightStatementQueryService freightQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = freightStatement();
        statement.setSettlementCompanyName(null);
        when(freightQueryService.requireActiveById(31L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                mock(SupplierStatementQueryService.class),
                freightQueryService,
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                paymentRequest(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, "C-001", "物流商A"),
                StatusConstants.DRAFT,
                1L,
                31L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单结算主体名称不能为空");
    }

    private PaymentRequest paymentRequest(String businessType, String counterpartyCode, String counterpartyName) {
        return new PaymentRequest(
                "FK-001",
                businessType,
                counterpartyCode,
                counterpartyName,
                null,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of()
        );
    }

    private SupplierStatement supplierStatement() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setSupplierCode("S-001");
        statement.setSettlementCompanyId(1001L);
        statement.setSettlementCompanyName("结算主体A");
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        return statement;
    }

    private FreightStatement freightStatement() {
        FreightStatement statement = new FreightStatement();
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setCarrierCode("C-001");
        statement.setSettlementCompanyId(1001L);
        statement.setSettlementCompanyName("结算主体A");
        statement.setStatus(StatusConstants.AUDITED);
        statement.setTotalFreight(new BigDecimal("500.00"));
        return statement;
    }
}
