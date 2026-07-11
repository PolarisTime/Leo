package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentPrepaymentAllocationUpdateRequest;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPrepaymentAllocationServiceTest {

    @Test
    void shouldPartiallyReplacePaidPurchasePrepaymentAllocationsAndSyncAffectedStatements() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService statementQueryService = mock(SupplierStatementQueryService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
        PaymentAllocationResponseAssembler responseAssembler = mock(PaymentAllocationResponseAssembler.class);
        Payment payment = paidPrepayment("1000.00");
        payment.getItems().add(allocation(payment, 101L, 1, 11L, "200.00"));
        SupplierStatement firstStatement = confirmedStatement(11L, "SUP-001", 31L, "1000.00");
        SupplierStatement secondStatement = confirmedStatement(12L, "SUP-001", 31L, "800.00");
        List<PaymentAllocationResponse> expectedResponse = List.of(mock(PaymentAllocationResponse.class));
        when(paymentRepository.findByIdAndDeletedFlagFalseForUpdate(5L)).thenReturn(Optional.of(payment));
        when(statementQueryService.requireActiveById(11L)).thenReturn(firstStatement);
        when(statementQueryService.requireActiveById(12L)).thenReturn(secondStatement);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                11L, "供应商", StatusConstants.PAID, 5L
        )).thenReturn(new BigDecimal("400.00"));
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                12L, "供应商", StatusConstants.PAID, 5L
        )).thenReturn(new BigDecimal("100.00"));
        when(idGenerator.nextId()).thenReturn(202L);
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(responseAssembler.toResponses(payment)).thenReturn(expectedResponse);
        PaymentPrepaymentAllocationService service = new PaymentPrepaymentAllocationService(
                paymentRepository,
                allocationRepository,
                statementQueryService,
                lockService,
                accessGuard,
                idGenerator,
                settlementSyncService,
                responseAssembler
        );

        List<PaymentAllocationResponse> response = service.replaceAllocations(
                5L,
                new PaymentPrepaymentAllocationUpdateRequest(List.of(
                        new PaymentAllocationRequest(101L, 11L, new BigDecimal("250.00")),
                        new PaymentAllocationRequest(null, 12L, new BigDecimal("300.00"))
                ))
        );

        assertThat(response).isSameAs(expectedResponse);
        assertThat(payment.getItems())
                .extracting(PaymentAllocation::getId)
                .containsExactly(101L, 202L);
        assertThat(payment.getItems())
                .extracting(PaymentAllocation::getSourceStatementId)
                .containsExactly(11L, 12L);
        assertThat(payment.getItems())
                .extracting(PaymentAllocation::getAllocatedAmount)
                .containsExactly(new BigDecimal("250.00"), new BigDecimal("300.00"));
        assertThat(payment.getItems())
                .extracting(PaymentAllocation::getLineNo)
                .containsExactly(1, 2);
        assertThat(payment.getItems()).allSatisfy(item -> assertThat(item.getPayment()).isSameAs(payment));
        verify(lockService).lockStatementSources(List.of(), List.of(11L, 12L), List.of());
        verify(accessGuard).assertCurrentUserCanAccess("supplier-statement", "read", firstStatement);
        verify(accessGuard).assertCurrentUserCanAccess("supplier-statement", "read", secondStatement);
        InOrder persistenceFlow = inOrder(settlementSyncService, paymentRepository, responseAssembler);
        persistenceFlow.verify(settlementSyncService).captureOriginalAllocationState(payment);
        persistenceFlow.verify(paymentRepository).saveAndFlush(payment);
        persistenceFlow.verify(settlementSyncService).syncLinkedStatements(payment);
        persistenceFlow.verify(responseAssembler).toResponses(payment);
    }

    @Test
    void shouldRejectStatementSettlementPayment() {
        Payment payment = paidPrepayment("1000.00");
        payment.setPaymentPurpose(PaymentPurposes.STATEMENT_SETTLEMENT);
        Fixture fixture = new Fixture(payment);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅采购预付款");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectDraftPurchasePrepayment() {
        Payment payment = paidPrepayment("1000.00");
        payment.setStatus(StatusConstants.DRAFT);
        Fixture fixture = new Fixture(payment);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已付款");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectPurchasePrepaymentWithoutSupplierIdentity() {
        Payment payment = paidPrepayment("1000.00");
        payment.setSupplierCode(" ");
        Fixture fixture = new Fixture(payment);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码不能为空");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
    }

    @Test
    void shouldRejectSupplierStatementFromAnotherSupplier() {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        fixture.allowStatement(confirmedStatement(11L, "SUP-002", 31L, "1000.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码")
                .hasMessageContaining("不一致");

        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectSupplierStatementFromAnotherSettlementCompany() {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        fixture.allowStatement(confirmedStatement(11L, "SUP-001", 32L, "1000.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体")
                .hasMessageContaining("不一致");

        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectUnconfirmedSupplierStatement() {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        SupplierStatement statement = confirmedStatement(11L, "SUP-001", 31L, "1000.00");
        statement.setStatus(StatusConstants.PENDING_CONFIRM);
        fixture.allowStatement(statement, BigDecimal.ZERO);

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未确认");

        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectDuplicateSupplierStatement() {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        PaymentPrepaymentAllocationUpdateRequest request = updateRequest(
                new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")),
                new PaymentAllocationRequest(null, 11L, new BigDecimal("200.00"))
        );

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复核销同一供应商对账单");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    void shouldRejectNonPositiveAllocationAmount(String amount) {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        PaymentPrepaymentAllocationUpdateRequest request = updateRequest(
                new PaymentAllocationRequest(null, 11L, new BigDecimal(amount))
        );

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额必须大于0");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
    }

    @Test
    void shouldRejectAllocationTotalAbovePurchasePrepaymentAmount() {
        Fixture fixture = new Fixture(paidPrepayment("100.00"));

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过采购预付款金额");

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
    }

    @Test
    void shouldRejectSupplierStatementCumulativeAmountAbovePurchaseAmount() {
        Fixture fixture = new Fixture(paidPrepayment("1000.00"));
        fixture.allowStatement(
                confirmedStatement(11L, "SUP-001", 31L, "500.00"),
                new BigDecimal("450.00")
        );

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计付款金额不能超过采购金额");

        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectForgedAllocationItemId() {
        Payment payment = paidPrepayment("1000.00");
        payment.getItems().add(allocation(payment, 101L, 1, 11L, "200.00"));
        Fixture fixture = new Fixture(payment);
        fixture.allowStatement(confirmedStatement(11L, "SUP-001", 31L, "1000.00"), BigDecimal.ZERO);
        PaymentPrepaymentAllocationUpdateRequest request = updateRequest(
                new PaymentAllocationRequest(999L, 11L, new BigDecimal("200.00"))
        );

        assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子项ID不存在");

        verify(fixture.paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldClearAllocationsAndSyncPreviouslyLinkedStatement() {
        Payment payment = paidPrepayment("1000.00");
        payment.getItems().add(allocation(payment, 101L, 1, 11L, "200.00"));
        Fixture fixture = new Fixture(payment);
        when(fixture.paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(fixture.responseAssembler.toResponses(payment)).thenReturn(List.of());

        List<PaymentAllocationResponse> response = fixture.service.replaceAllocations(
                5L,
                new PaymentPrepaymentAllocationUpdateRequest(List.of())
        );

        assertThat(response).isEmpty();
        assertThat(payment.getItems()).isEmpty();
        verify(fixture.lockService).lockStatementSources(List.of(), List.of(11L), List.of());
        InOrder persistenceFlow = inOrder(
                fixture.settlementSyncService,
                fixture.paymentRepository,
                fixture.responseAssembler
        );
        persistenceFlow.verify(fixture.settlementSyncService).captureOriginalAllocationState(payment);
        persistenceFlow.verify(fixture.paymentRepository).saveAndFlush(payment);
        persistenceFlow.verify(fixture.settlementSyncService).syncLinkedStatements(payment);
        persistenceFlow.verify(fixture.responseAssembler).toResponses(payment);
    }

    @Test
    void shouldApplyPaymentRecordDataScope() {
        Payment payment = paidPrepayment("1000.00");
        payment.setCreatedBy(7L);
        Fixture fixture = new Fixture(payment);
        DataScopeContext.set(8L, "payment", "self");
        try {
            assertThatThrownBy(() -> fixture.service.replaceAllocations(5L, updateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无数据权限");
        } finally {
            DataScopeContext.clear();
        }

        verify(fixture.lockService, never()).lockStatementSources(any(), any(), any());
    }

    private PaymentPrepaymentAllocationUpdateRequest updateRequest(PaymentAllocationRequest... items) {
        List<PaymentAllocationRequest> requests = items.length == 0
                ? List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("200.00")))
                : List.of(items);
        return new PaymentPrepaymentAllocationUpdateRequest(requests);
    }

    private Payment paidPrepayment(String amount) {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentNo("FK-005");
        payment.setBusinessType("供应商");
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSupplierCode("SUP-001");
        payment.setSupplierName("供应商甲");
        payment.setSettlementCompanyId(31L);
        payment.setSettlementCompanyName("结算主体甲");
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(StatusConstants.PAID);
        payment.setItems(new ArrayList<>());
        return payment;
    }

    private PaymentAllocation allocation(Payment payment,
                                         Long id,
                                         Integer lineNo,
                                         Long statementId,
                                         String amount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(id);
        allocation.setPayment(payment);
        allocation.setLineNo(lineNo);
        allocation.setSourceStatementId(statementId);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        return allocation;
    }

    private SupplierStatement confirmedStatement(Long id,
                                                 String supplierCode,
                                                 Long settlementCompanyId,
                                                 String purchaseAmount) {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(id);
        statement.setStatementNo("GYDZ-" + id);
        statement.setSupplierCode(supplierCode);
        statement.setSupplierName("供应商甲");
        statement.setSettlementCompanyId(settlementCompanyId);
        statement.setSettlementCompanyName("结算主体甲");
        statement.setPurchaseAmount(new BigDecimal(purchaseAmount));
        statement.setStatus(StatusConstants.CONFIRMED);
        return statement;
    }

    private final class Fixture {
        private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        private final PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        private final SupplierStatementQueryService statementQueryService = mock(SupplierStatementQueryService.class);
        private final SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        private final ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        private final PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
        private final PaymentAllocationResponseAssembler responseAssembler = mock(PaymentAllocationResponseAssembler.class);
        private final PaymentPrepaymentAllocationService service = new PaymentPrepaymentAllocationService(
                paymentRepository,
                allocationRepository,
                statementQueryService,
                lockService,
                accessGuard,
                idGenerator,
                settlementSyncService,
                responseAssembler
        );

        private Fixture(Payment payment) {
            when(paymentRepository.findByIdAndDeletedFlagFalseForUpdate(5L)).thenReturn(Optional.of(payment));
        }

        private void allowStatement(SupplierStatement statement, BigDecimal alreadyAllocatedAmount) {
            when(statementQueryService.requireActiveById(statement.getId())).thenReturn(statement);
            when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                    statement.getId(),
                    PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                    StatusConstants.PAID,
                    5L
            )).thenReturn(alreadyAllocatedAmount);
        }
    }
}
