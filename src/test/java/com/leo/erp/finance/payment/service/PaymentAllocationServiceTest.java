package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAllocationServiceTest {

    @Test
    void shouldClearAllocationsWhenBusinessTypeDoesNotSupportSettlement() {
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        PaymentAllocationService service = new PaymentAllocationService(validator);
        Payment payment = payment("其他业务", new BigDecimal("100.00"));
        PaymentAllocation existing = allocation(11L, 21L, new BigDecimal("80.00"));
        payment.getItems().add(existing);

        PaymentAllocationService.AllocationApplyResult result = service.applyAllocations(
                payment,
                paymentRequest("其他业务", null, new BigDecimal("100.00"), null),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.counterpartyCode()).isNull();
        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.allocationEmpty()).isTrue();
        assertThat(payment.getItems()).isEmpty();
        verify(validator, never()).validate(any(), any(), anyLong(), anyLong(), any(), any(), anyInt());
    }

    @Test
    void shouldRejectSettledStatusWhenBusinessTypeDoesNotSupportSettlement() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));
        Payment payment = payment("其他业务", new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest("其他业务", null, new BigDecimal("100.00"), null),
                StatusConstants.PAID,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须关联供应商或物流商对账单核销");
    }

    @Test
    void shouldRejectAllocationItemsWhenBusinessTypeDoesNotSupportSettlement() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));
        Payment payment = payment("其他业务", new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest("其他业务", null, new BigDecimal("100.00"), List.of(
                        new PaymentAllocationRequest(null, 21L, new BigDecimal("100.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前业务类型不支持对账单核销");
    }

    @Test
    void shouldApplySupplierAllocationFromSourceStatementId() {
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        when(validator.validate(
                any(PaymentRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                eq(21L),
                eq(new BigDecimal("100.00")),
                any(),
                eq(1)
        )).thenReturn(validatedStatement(" SUP-001 "));
        PaymentAllocationService service = new PaymentAllocationService(validator);
        Payment payment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));

        PaymentAllocationService.AllocationApplyResult result = service.applyAllocations(
                payment,
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, 21L, new BigDecimal("100.00"), null),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.counterpartyCode()).isEqualTo("SUP-001");
        assertThat(result.settlementCompanyId()).isEqualTo(1001L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
        assertThat(result.allocationEmpty()).isFalse();
        assertThat(payment.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(101L);
                    assertThat(item.getPayment()).isSameAs(payment);
                    assertThat(item.getLineNo()).isEqualTo(1);
                    assertThat(item.getSourceStatementId()).isEqualTo(21L);
                    assertThat(item.getAllocatedAmount()).isEqualByComparingTo("100.00");
                });
    }

    @Test
    void shouldNotExposeIncompleteNewAllocationsToValidatorQueries() {
        Payment payment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        when(validator.validate(
                any(PaymentRequest.class),
                eq(StatusConstants.PAID),
                eq(1L),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenAnswer(invocation -> {
            assertThat(payment.getItems())
                    .as("validator 查询触发 flush 时不应暴露未完整赋值的新核销行")
                    .noneMatch(item -> item.getPayment() == null
                            || item.getLineNo() == null
                            || item.getSourceStatementId() == null
                            || item.getAllocatedAmount() == null);
            return validatedStatement("SUP-001");
        });
        PaymentAllocationService service = new PaymentAllocationService(validator);

        PaymentAllocationService.AllocationApplyResult result = service.applyAllocations(
                payment,
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, null, new BigDecimal("100.00"), List.of(
                        new PaymentAllocationRequest(null, 21L, new BigDecimal("40.00")),
                        new PaymentAllocationRequest(null, 22L, new BigDecimal("60.00"))
                )),
                StatusConstants.PAID,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getItems()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getPayment()).isSameAs(payment);
            assertThat(item.getLineNo()).isNotNull();
            assertThat(item.getSourceStatementId()).isNotNull();
            assertThat(item.getAllocatedAmount()).isNotNull();
        });
    }

    @Test
    void shouldRejectAllocationTotalGreaterThanPaymentAmount() {
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        when(validator.validate(
                any(PaymentRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenReturn(validatedStatement("SUP-001"));
        PaymentAllocationService service = new PaymentAllocationService(validator);
        Payment payment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, null, new BigDecimal("100.00"), List.of(
                        new PaymentAllocationRequest(null, 21L, new BigDecimal("60.00")),
                        new PaymentAllocationRequest(null, 22L, new BigDecimal("50.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过付款金额");
    }

    @Test
    void shouldRequireCompleteAllocationsForSettledStatus() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));
        Payment payment = payment(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, null, new BigDecimal("100.00"), null),
                StatusConstants.PAID,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须填写核销明细");
    }

    @Test
    void shouldRequireAllocationTotalEqualsPaymentAmountForSettledStatus() {
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        when(validator.validate(
                any(PaymentRequest.class),
                eq(StatusConstants.PAID),
                eq(1L),
                eq(21L),
                eq(new BigDecimal("80.00")),
                any(),
                eq(1)
        )).thenReturn(validatedStatement("CAR-001"));
        PaymentAllocationService service = new PaymentAllocationService(validator);
        Payment payment = payment(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, null, new BigDecimal("100.00"), List.of(
                        new PaymentAllocationRequest(null, 21L, new BigDecimal("80.00"))
                )),
                StatusConstants.PAID,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款金额必须等于核销金额合计");
    }

    @Test
    void shouldValidateExistingAllocationsOnlyWhenSettled() {
        PaymentStatementAllocationValidator validator = mock(PaymentStatementAllocationValidator.class);
        when(validator.validate(
                any(PaymentRequest.class),
                eq(StatusConstants.PAID),
                eq(1L),
                eq(21L),
                eq(new BigDecimal("100.00")),
                any(),
                eq(1)
        )).thenReturn(validatedStatement("SUP-001"));
        PaymentAllocationService service = new PaymentAllocationService(validator);
        Payment draftPayment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));

        service.validateExistingAllocationsForSettlement(draftPayment, StatusConstants.DRAFT);

        verify(validator, never()).validate(any(), any(), anyLong(), anyLong(), any(), any(), anyInt());

        Payment paidPayment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));
        paidPayment.getItems().add(allocation(11L, 21L, new BigDecimal("100.00")));

        service.validateExistingAllocationsForSettlement(paidPayment, StatusConstants.PAID);

        verify(validator).validate(
                any(PaymentRequest.class),
                eq(StatusConstants.PAID),
                eq(1L),
                eq(21L),
                eq(new BigDecimal("100.00")),
                any(),
                eq(1)
        );
    }

    @Test
    void shouldRejectSupplierStatementsFromDifferentSettlementCompanies() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(21L))
                .thenReturn(supplierStatement(21L, 1001L, "结算主体A"));
        when(supplierQueryService.requireActiveById(22L))
                .thenReturn(supplierStatement(22L, 1002L, "结算主体B"));
        PaymentAllocationService service = realAllocationService(
                supplierQueryService,
                mock(FreightStatementQueryService.class)
        );

        assertThatThrownBy(() -> service.applyAllocations(
                payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00")),
                paymentRequest(
                        PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                        "SUP-001",
                        "供应商A",
                        null,
                        new BigDecimal("100.00"),
                        List.of(
                                new PaymentAllocationRequest(null, 21L, new BigDecimal("40.00")),
                                new PaymentAllocationRequest(null, 22L, new BigDecimal("60.00"))
                        )
                ),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一付款单不能核销不同结算主体的对账单");
    }

    @Test
    void shouldRejectFreightStatementsFromDifferentSettlementCompanies() {
        FreightStatementQueryService freightQueryService = mock(FreightStatementQueryService.class);
        when(freightQueryService.requireActiveById(31L))
                .thenReturn(freightStatement(31L, 1001L, "结算主体A"));
        when(freightQueryService.requireActiveById(32L))
                .thenReturn(freightStatement(32L, 1002L, "结算主体B"));
        PaymentAllocationService service = realAllocationService(
                mock(SupplierStatementQueryService.class),
                freightQueryService
        );
        Payment payment = payment(PaymentAllocationService.FREIGHT_PAYMENT_TYPE, new BigDecimal("100.00"));
        payment.setCounterpartyCode("CAR-001");
        payment.setCounterpartyName("物流商A");

        assertThatThrownBy(() -> service.applyAllocations(
                payment,
                paymentRequest(
                        PaymentAllocationService.FREIGHT_PAYMENT_TYPE,
                        "CAR-001",
                        "物流商A",
                        null,
                        new BigDecimal("100.00"),
                        List.of(
                                new PaymentAllocationRequest(null, 31L, new BigDecimal("40.00")),
                                new PaymentAllocationRequest(null, 32L, new BigDecimal("60.00"))
                        )
                ),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一付款单不能核销不同结算主体的对账单");
    }

    @Test
    void shouldAllowStatementsWithSameSettlementCompanyIdAndHistoricalNames() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(21L))
                .thenReturn(supplierStatement(21L, 1001L, "结算主体旧称"));
        when(supplierQueryService.requireActiveById(22L))
                .thenReturn(supplierStatement(22L, 1001L, "结算主体新称"));
        PaymentAllocationService service = realAllocationService(
                supplierQueryService,
                mock(FreightStatementQueryService.class)
        );

        PaymentAllocationService.AllocationApplyResult result = service.applyAllocations(
                payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00")),
                paymentRequest(
                        PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                        "SUP-001",
                        "供应商A",
                        null,
                        new BigDecimal("100.00"),
                        List.of(
                                new PaymentAllocationRequest(null, 21L, new BigDecimal("40.00")),
                                new PaymentAllocationRequest(null, 22L, new BigDecimal("60.00"))
                        )
                ),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
        assertThat(result.settlementCompanyId()).isEqualTo(1001L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体旧称");
    }

    @Test
    void shouldRevalidateSettlementCompanyWhenChangingDraftToPaid() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(21L))
                .thenReturn(supplierStatement(21L, 1001L, "结算主体A"));
        when(supplierQueryService.requireActiveById(22L))
                .thenReturn(supplierStatement(22L, 1002L, "结算主体B"));
        PaymentAllocationService service = realAllocationService(
                supplierQueryService,
                mock(FreightStatementQueryService.class)
        );
        Payment payment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));
        payment.getItems().add(allocation(11L, 21L, new BigDecimal("40.00")));
        payment.getItems().add(allocation(12L, 22L, new BigDecimal("60.00")));

        assertThatThrownBy(() -> service.validateExistingAllocationsForSettlement(
                payment,
                StatusConstants.PAID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一付款单不能核销不同结算主体的对账单");
    }

    @Test
    void shouldRefreshSettlementCompanySnapshotDuringPaidStatusValidation() {
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(21L))
                .thenReturn(supplierStatement(21L, 1001L, "来源结算主体"));
        PaymentAllocationService service = realAllocationService(
                supplierQueryService,
                mock(FreightStatementQueryService.class)
        );
        Payment payment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));
        payment.setSettlementCompanyId(999L);
        payment.setSettlementCompanyName("伪造或过期主体");
        payment.getItems().add(allocation(11L, 21L, new BigDecimal("100.00")));

        service.validateExistingAllocationsForSettlement(payment, StatusConstants.PAID);

        assertThat(payment.getSettlementCompanyId()).isEqualTo(1001L);
        assertThat(payment.getSettlementCompanyName()).isEqualTo("来源结算主体");
    }

    @Test
    void shouldRejectExistingSettledPaymentWithoutSupportedBusinessTypeOrCompleteAllocations() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));
        Payment unsupportedPayment = payment("其他业务", new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.validateExistingAllocationsForSettlement(unsupportedPayment, StatusConstants.PAID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须关联供应商或物流商对账单核销");

        Payment emptyPayment = payment(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.validateExistingAllocationsForSettlement(emptyPayment, StatusConstants.PAID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须填写核销明细");
    }

    @Test
    void shouldNormalizeAndSumAllocations() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));
        List<PaymentAllocationRequest> itemRequests = List.of(
                new PaymentAllocationRequest(11L, 21L, new BigDecimal("40.00"))
        );

        assertThat(service.normalizeAllocationRequests(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, null, new BigDecimal("100.00"), itemRequests)
        )).isSameAs(itemRequests);
        assertThat(service.normalizeAllocationRequests(
                paymentRequest(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE, null, new BigDecimal("100.00"), null)
        )).isEmpty();
        assertThat(service.totalAllocatedAmount(List.of(
                allocation(11L, 21L, new BigDecimal("40.00")),
                allocation(12L, 22L, null)
        ))).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldMergeCounterpartyCode() {
        PaymentAllocationService service = new PaymentAllocationService(mock(PaymentStatementAllocationValidator.class));

        assertThat(service.mergeCounterpartyCode(null, " SUP-001 ")).isEqualTo("SUP-001");
        assertThat(service.mergeCounterpartyCode(" SUP-001 ", null)).isEqualTo("SUP-001");
        assertThat(service.mergeCounterpartyCode(" SUP-001 ", "SUP-001")).isEqualTo("SUP-001");
        assertThatThrownBy(() -> service.mergeCounterpartyCode("SUP-001", "SUP-002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一付款单不能核销不同往来单位编码的对账单");
    }

    private Payment payment(String businessType, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo("FK-001");
        payment.setBusinessType(businessType);
        payment.setCounterpartyCode("SUP-001");
        payment.setCounterpartyName("供应商A");
        payment.setPaymentDate(LocalDate.of(2026, 4, 26));
        payment.setPayType("银行转账");
        payment.setAmount(amount);
        payment.setStatus(StatusConstants.DRAFT);
        payment.setOperatorName("财务A");
        payment.setItems(new ArrayList<>());
        return payment;
    }

    private PaymentRequest paymentRequest(String businessType,
                                          Long sourceStatementId,
                                          BigDecimal amount,
                                          List<PaymentAllocationRequest> items) {
        return paymentRequest(
                businessType,
                "SUP-001",
                "供应商A",
                sourceStatementId,
                amount,
                items
        );
    }

    private PaymentRequest paymentRequest(String businessType,
                                          String counterpartyCode,
                                          String counterpartyName,
                                          Long sourceStatementId,
                                          BigDecimal amount,
                                          List<PaymentAllocationRequest> items) {
        return new PaymentRequest(
                "FK-001",
                businessType,
                counterpartyCode,
                counterpartyName,
                sourceStatementId,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                amount,
                StatusConstants.DRAFT,
                "财务A",
                null,
                items
        );
    }

    private PaymentAllocationService realAllocationService(
            SupplierStatementQueryService supplierQueryService,
            FreightStatementQueryService freightQueryService
    ) {
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                freightQueryService,
                mock(ResourceRecordAccessGuard.class)
        );
        return new PaymentAllocationService(validator);
    }

    private PaymentStatementAllocationValidator.ValidatedStatement validatedStatement(String counterpartyCode) {
        return new PaymentStatementAllocationValidator.ValidatedStatement(
                counterpartyCode,
                1001L,
                "结算主体A"
        );
    }

    private SupplierStatement supplierStatement(Long id,
                                                Long settlementCompanyId,
                                                String settlementCompanyName) {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(id);
        statement.setSupplierCode("SUP-001");
        statement.setSupplierName("供应商A");
        statement.setSettlementCompanyId(settlementCompanyId);
        statement.setSettlementCompanyName(settlementCompanyName);
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        return statement;
    }

    private FreightStatement freightStatement(Long id,
                                              Long settlementCompanyId,
                                              String settlementCompanyName) {
        FreightStatement statement = new FreightStatement();
        statement.setId(id);
        statement.setCarrierCode("CAR-001");
        statement.setCarrierName("物流商A");
        statement.setSettlementCompanyId(settlementCompanyId);
        statement.setSettlementCompanyName(settlementCompanyName);
        statement.setStatus(StatusConstants.AUDITED);
        statement.setTotalFreight(new BigDecimal("1000.00"));
        return statement;
    }

    private PaymentAllocation allocation(Long id, Long sourceStatementId, BigDecimal allocatedAmount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(id);
        allocation.setSourceStatementId(sourceStatementId);
        allocation.setAllocatedAmount(allocatedAmount);
        return allocation;
    }
}
