package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentApplyServiceTest {

    @Test
    void shouldRejectLegacySupplierStatementSettlement() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentApplyService service = service(allocationService);

        assertThatThrownBy(() -> service.apply(payment(), request(
                "供应商",
                null,
                null,
                null,
                "供应商A"
        ), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商付款已统一为总额付款");

        verify(allocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void shouldRejectLegacyPurchasePrepayment() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentApplyService service = service(allocationService);

        assertThatThrownBy(() -> service.apply(payment(), request(
                "供应商",
                201L,
                PaymentPurposes.PURCHASE_PREPAYMENT,
                901L,
                "供应商A"
        ), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款已统一为供应商总额付款形成的预付款余额");

        verify(allocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void shouldApplySupplierTotalPaymentFromMasterData() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        CompanySettingRepository companyRepository = mock(CompanySettingRepository.class);
        PaymentApplyService service = service(allocationService);
        service.setSupplierDependencies(supplierRepository, companyRepository);
        Supplier supplier = new Supplier();
        supplier.setId(201L);
        supplier.setSupplierCode("SUP-001");
        supplier.setSupplierName("供应商A");
        CompanySetting company = new CompanySetting();
        company.setId(301L);
        company.setCompanyName("结算主体A");
        when(supplierRepository.findByIdAndDeletedFlagFalse(201L)).thenReturn(Optional.of(supplier));
        when(companyRepository.findByIdAndDeletedFlagFalse(301L)).thenReturn(Optional.of(company));

        Payment entity = payment();
        service.apply(entity, new PaymentRequest(
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
        ), () -> 100L);

        assertThat(entity.getCounterpartyId()).isEqualTo(201L);
        assertThat(entity.getCounterpartyCode()).isEqualTo("SUP-001");
        assertThat(entity.getCounterpartyName()).isEqualTo("供应商A");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(301L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算主体A");
        assertThat(entity.getSourceStatementId()).isNull();
        assertThat(entity.getSourcePurchaseOrderId()).isNull();
        assertThat(entity.getItems()).isEmpty();
        verify(allocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void shouldKeepFreightStatementSettlement() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentApplyService service = service(allocationService);
        PaymentRequest request = request(
                "物流商",
                401L,
                PaymentPurposes.STATEMENT_SETTLEMENT,
                null,
                "物流商A"
        );
        when(allocationService.applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any()))
                .thenReturn(new PaymentAllocationService.AllocationApplyResult(
                        "物流商",
                        401L,
                        "CAR-001",
                        301L,
                        "结算主体A",
                        new BigDecimal("700.00"),
                        false
                ));

        Payment entity = payment();
        service.apply(entity, request, () -> 100L);

        assertThat(entity.getPaymentPurpose()).isEqualTo(PaymentPurposes.STATEMENT_SETTLEMENT);
        assertThat(entity.getCounterpartyId()).isEqualTo(401L);
        assertThat(entity.getSettlementCompanyId()).isEqualTo(301L);
        verify(allocationService).applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any());
    }

    private PaymentApplyService service(PaymentAllocationService allocationService) {
        return new PaymentApplyService(
                mock(WorkflowTransitionGuard.class),
                allocationService,
                mock(PaymentSettlementSyncService.class),
                mock(PaymentPurchasePrepaymentService.class)
        );
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setItems(new ArrayList<>());
        return payment;
    }

    private PaymentRequest request(String businessType,
                                   Long counterpartyId,
                                   String paymentPurpose,
                                   Long sourcePurchaseOrderId,
                                   String counterpartyName) {
        return new PaymentRequest(
                "FK-001",
                businessType,
                counterpartyId,
                paymentPurpose,
                null,
                counterpartyName,
                null,
                sourcePurchaseOrderId,
                null,
                null,
                null,
                null,
                null,
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
