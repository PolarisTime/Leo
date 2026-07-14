package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.finance.purchaseflow.service.SupplierLedgerLockService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void shouldCreateDraftSupplierTotalPaymentWithoutBusinessAllocations() {
        Fixture fixture = new Fixture();

        PaymentResponse response = fixture.service.create(fixture.supplierPaymentRequest());

        assertThat(response).isNotNull();
        verify(fixture.paymentRepository).save(any(Payment.class));
        verify(fixture.ledgerLockService, never()).lock(any(), any());
    }

    @Test
    void shouldRejectSupplierTotalPaymentWithBusinessSource() {
        Fixture fixture = new Fixture();
        PaymentRequest source = fixture.supplierPaymentRequest();
        PaymentRequest request = new PaymentRequest(
                source.paymentNo(),
                source.businessType(),
                source.counterpartyId(),
                source.paymentPurpose(),
                source.counterpartyCode(),
                source.counterpartyName(),
                11L,
                null,
                null,
                null,
                null,
                source.settlementCompanyId(),
                source.settlementCompanyName(),
                source.paymentDate(),
                source.payType(),
                source.amount(),
                source.status(),
                source.operatorName(),
                source.remark(),
                List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能关联采购或对账明细");

        verify(fixture.paymentRepository, never()).save(any());
    }

    @Test
    void shouldRejectLegacySupplierStatementPaymentCreation() {
        Fixture fixture = new Fixture();
        PaymentRequest source = fixture.supplierPaymentRequest();
        PaymentRequest request = new PaymentRequest(
                source.paymentNo(),
                source.businessType(),
                source.counterpartyId(),
                PaymentPurposes.STATEMENT_SETTLEMENT,
                source.counterpartyCode(),
                source.counterpartyName(),
                null,
                null,
                null,
                null,
                null,
                source.settlementCompanyId(),
                source.settlementCompanyName(),
                source.paymentDate(),
                source.payType(),
                source.amount(),
                source.status(),
                source.operatorName(),
                source.remark(),
                List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商付款已统一为总额付款");

        verify(fixture.paymentRepository, never()).save(any());
    }

    @Test
    void shouldRejectLegacyPurchasePrepaymentCreation() {
        Fixture fixture = new Fixture();
        PaymentRequest source = fixture.supplierPaymentRequest();
        PaymentRequest request = new PaymentRequest(
                source.paymentNo(),
                source.businessType(),
                source.counterpartyId(),
                PaymentPurposes.PURCHASE_PREPAYMENT,
                source.counterpartyCode(),
                source.counterpartyName(),
                null,
                91L,
                "PO-091",
                "SUP-001",
                "供应商A",
                source.settlementCompanyId(),
                source.settlementCompanyName(),
                source.paymentDate(),
                source.payType(),
                source.amount(),
                source.status(),
                source.operatorName(),
                source.remark(),
                List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款已统一为供应商总额付款形成的预付款余额");

        verify(fixture.paymentRepository, never()).save(any());
    }

    @Test
    void shouldLockSupplierLedgerWhenAuditingSupplierTotalPayment() {
        Fixture fixture = new Fixture();
        Payment payment = fixture.payment(PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT);
        fixture.stubExisting(payment);

        fixture.service.updateStatus(payment.getId(), StatusConstants.AUDITED);

        verify(fixture.ledgerLockService).lock(301L, 201L);
        verify(fixture.paymentRepository).save(payment);
        assertThat(payment.getStatus()).isEqualTo(StatusConstants.AUDITED);
    }

    @Test
    void shouldKeepLegacySupplierPaymentsReadOnly() {
        Fixture fixture = new Fixture();
        Payment statementPayment = fixture.payment(PaymentPurposes.STATEMENT_SETTLEMENT, StatusConstants.DRAFT);
        fixture.stubExisting(statementPayment);

        assertThatThrownBy(() -> fixture.service.updateStatus(
                statementPayment.getId(),
                StatusConstants.AUDITED
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅供历史查询");

        verify(fixture.ledgerLockService, never()).lock(any(), any());
        verify(fixture.paymentRepository, never()).save(statementPayment);
    }

    @Test
    void shouldRejectDeletingLegacyPurchasePrepayment() {
        Fixture fixture = new Fixture();
        Payment prepayment = fixture.payment(PaymentPurposes.PURCHASE_PREPAYMENT, StatusConstants.DRAFT);
        fixture.stubExisting(prepayment);

        assertThatThrownBy(() -> fixture.service.delete(prepayment.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅供历史查询");

        assertThat(prepayment.isDeletedFlag()).isFalse();
        verify(fixture.paymentRepository, never()).save(prepayment);
    }

    @Test
    void shouldRejectEditingAuditedPayment() {
        Fixture fixture = new Fixture();
        Payment payment = fixture.payment(PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.AUDITED);
        fixture.stubExisting(payment);

        assertThatThrownBy(() -> fixture.service.update(payment.getId(), fixture.supplierPaymentRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");

        verify(fixture.paymentRepository, never()).save(payment);
    }

    @Test
    void shouldRejectDeletingAuditedPayment() {
        Fixture fixture = new Fixture();
        Payment payment = fixture.payment(PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.AUDITED);
        fixture.stubExisting(payment);

        assertThatThrownBy(() -> fixture.service.delete(payment.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能删除");

        assertThat(payment.isDeletedFlag()).isFalse();
    }

    @Test
    void shouldRejectDuplicatePaymentNumber() {
        Fixture fixture = new Fixture();
        when(fixture.paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.create(fixture.supplierPaymentRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款单号已存在");

        verify(fixture.paymentRepository, never()).save(any());
    }

    private static final class Fixture {
        private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
        private final SupplierLedgerLockService ledgerLockService = mock(SupplierLedgerLockService.class);
        private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
        private final CompanySettingRepository companyRepository = mock(CompanySettingRepository.class);
        private final PaymentService service;

        private Fixture() {
            SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
            PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
            PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
            PaymentApplyService applyService = new PaymentApplyService(
                    mock(WorkflowTransitionGuard.class),
                    allocationService,
                    settlementSyncService,
                    mock(PaymentPurchasePrepaymentService.class)
            );
            applyService.setSupplierDependencies(supplierRepository, companyRepository);
            service = new PaymentService(
                    paymentRepository,
                    idGenerator,
                    paymentMapper,
                    applyService,
                    allocationService,
                    mock(PaymentAllocationResponseAssembler.class),
                    settlementSyncService,
                    mock(SourceAllocationLockService.class),
                    mock(PaymentPurchasePrepaymentService.class)
            );
            service.setSupplierLedgerLockService(ledgerLockService);
            when(idGenerator.nextId()).thenReturn(1001L);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));
            Supplier supplier = new Supplier();
            supplier.setId(201L);
            supplier.setSupplierCode("SUP-001");
            supplier.setSupplierName("供应商A");
            CompanySetting company = new CompanySetting();
            company.setId(301L);
            company.setCompanyName("结算主体A");
            when(supplierRepository.findByIdAndDeletedFlagFalse(201L)).thenReturn(Optional.of(supplier));
            when(companyRepository.findByIdAndDeletedFlagFalse(301L)).thenReturn(Optional.of(company));
        }

        private void stubExisting(Payment payment) {
            when(paymentRepository.findByIdAndDeletedFlagFalseForUpdate(payment.getId()))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.findByIdAndDeletedFlagFalse(payment.getId()))
                    .thenReturn(Optional.of(payment));
        }

        private Payment payment(String purpose, String status) {
            Payment payment = new Payment();
            payment.setId(1L);
            payment.setPaymentNo("FK-001");
            payment.setBusinessType("供应商");
            payment.setCounterpartyType("供应商");
            payment.setCounterpartyId(201L);
            payment.setCounterpartyCode("SUP-001");
            payment.setCounterpartyName("供应商A");
            payment.setPaymentPurpose(purpose);
            payment.setSettlementCompanyId(301L);
            payment.setSettlementCompanyName("结算主体A");
            payment.setPaymentDate(LocalDate.of(2026, 7, 14));
            payment.setPayType("银行转账");
            payment.setAmount(new BigDecimal("700.00"));
            payment.setStatus(status);
            payment.setOperatorName("财务A");
            payment.setItems(new ArrayList<>());
            return payment;
        }

        private PaymentRequest supplierPaymentRequest() {
            return new PaymentRequest(
                    "FK-001",
                    "供应商",
                    201L,
                    PaymentPurposes.SUPPLIER_PAYMENT,
                    "SUP-001",
                    "供应商A",
                    null,
                    null,
                    null,
                    null,
                    null,
                    301L,
                    "结算主体A",
                    LocalDate.of(2026, 7, 14),
                    "银行转账",
                    new BigDecimal("700.00"),
                    StatusConstants.DRAFT,
                    "财务A",
                    null,
                    List.of()
            );
        }
    }
}
