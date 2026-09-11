package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentApplyServiceTest {

    private static final LongSupplier NEXT_ID = () -> 1L;
    private static final Long SOURCE_STATEMENT_ID = 66L;

    @Mock
    private PaymentAllocationService paymentAllocationService;

    @Mock
    private PaymentSettlementSyncService settlementSyncService;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private CarrierQuery carrierQuery;

    @Mock
    private CompanySettingRepository companySettingRepository;

    @Mock
    private CompanySetting company;

    private PaymentApplyService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplyService(
                paymentAllocationService,
                settlementSyncService,
                supplierQuery,
                carrierQuery,
                companySettingRepository
        );
    }

    private PaymentRequest directSupplierRequest(String purpose, String status, String businessType,
                                                 Long counterpartyId, BigDecimal amount,
                                                 Long sourceStatementId, List<PaymentAllocationRequest> items) {
        return new PaymentRequest(
                "PAY001", businessType, counterpartyId, purpose, "S001", "供应商A",
                sourceStatementId, null, null, null, null, 1L, "主体A",
                2L, LocalDate.of(2026, 9, 1), "银行转账", amount, status, "操作员", "备注",
                items, false);
    }

    private PaymentRequest statementSettlementRequest(String status, String businessType,
                                                      Long counterpartyId, BigDecimal amount,
                                                      Long sourceStatementId) {
        return new PaymentRequest(
                "PAY001", businessType, counterpartyId, PaymentPurposes.STATEMENT_SETTLEMENT,
                "F001", "物流商A", sourceStatementId, null, null, null, null, null, null,
                2L, LocalDate.of(2026, 9, 1), "银行转账", amount, status, "操作员", "备注",
                null, false);
    }

    private PaymentAllocationService.AllocationApplyResult allocation(String counterpartyType,
                                                                     Long counterpartyId,
                                                                     String counterpartyCode,
                                                                     BigDecimal total) {
        return new PaymentAllocationService.AllocationApplyResult(
                counterpartyType, counterpartyId, counterpartyCode, 1L, "主体A", total, false);
    }

    @Test
    void apply_shouldRejectInvalidPurpose() {
        Payment entity = new Payment();
        PaymentRequest request = new PaymentRequest(
                "PAY001", "供应商", 5L, "NOT_A_PURPOSE", "S001", "供应商A",
                null, null, null, null, null, 1L, "主体A",
                2L, LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), StatusConstants.DRAFT,
                "操作员", "备注", null, false);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款用途不合法");
    }

    @Test
    void apply_shouldRejectInvalidStatus() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.COMPLETED, "供应商", 5L,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款单状态不合法");
    }

    @Test
    void apply_shouldRejectNonDraftStatusOnCreate() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.AUDITED, "供应商", 5L,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新建付款单只能保存为草稿");

        verify(supplierQuery, never()).findActiveById(any());
    }

    @Test
    void apply_shouldRejectStatusChangeOnExistingEntity() {
        Payment entity = new Payment();
        entity.setStatus(StatusConstants.DRAFT);
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.AUDITED, "供应商", 5L,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改付款单状态");
    }

    @Test
    void apply_shouldRejectPurchasePrepayment() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.PURCHASE_PREPAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款已统一为供应商总额付款形成的预付款余额");
    }

    @Test
    void apply_shouldRejectUnsupportedBusinessTypeInDirectPayment() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "客户", 5L,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款往来类型必须为供应商或物流商");
    }

    @Test
    void apply_shouldRejectDirectPaymentWithNonPositiveAmount() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款金额必须大于0");
    }

    @Test
    void apply_shouldRejectDirectPaymentWithoutCounterpartyId() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", null,
                new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("往来方ID不能为空");
    }

    @Test
    void apply_shouldRejectDirectPaymentWithStatementLinkage() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.00"), SOURCE_STATEMENT_ID, null);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简单付款单不能关联采购或对账核销明细");

        verify(supplierQuery, never()).findActiveById(any());
    }

    @Test
    void apply_shouldRejectDirectPaymentWithAllocationItems() {
        Payment entity = new Payment();
        List<PaymentAllocationRequest> items = List.of(
                new PaymentAllocationRequest(null, SOURCE_STATEMENT_ID, SOURCE_STATEMENT_ID, new BigDecimal("100.00"))
        );
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.00"), null, items);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简单付款单不能关联采购或对账核销明细");
    }

    @Test
    void apply_shouldRejectSupplierNameMismatch() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.00"), null, null);
        when(supplierQuery.findActiveById(5L)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(5L, "S001", "另一个供应商")));

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商名称与ID不一致");
    }

    @Test
    void apply_shouldRejectSettlementCompanyNameMismatch() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.00"), null, null);
        when(supplierQuery.findActiveById(5L)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(5L, "S001", "供应商A")));
        when(companySettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(company));
        when(company.getCompanyName()).thenReturn("别的主体");

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体名称与ID不一致");
    }

    @Test
    void apply_shouldApplyDirectSupplierPayment() {
        Payment entity = new Payment();
        PaymentRequest request = directSupplierRequest(
                PaymentPurposes.SUPPLIER_PAYMENT, StatusConstants.DRAFT, "供应商", 5L,
                new BigDecimal("100.555"), null, null);
        when(supplierQuery.findActiveById(5L)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(5L, "S001", "供应商A")));
        when(companySettingRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(company));
        when(company.getId()).thenReturn(1L);
        when(company.getCompanyName()).thenReturn("主体A");

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getPaymentPurpose()).isEqualTo(PaymentPurposes.SUPPLIER_PAYMENT);
        assertThat(entity.getBusinessType()).isEqualTo("供应商");
        assertThat(entity.getCounterpartyType()).isEqualTo("供应商");
        assertThat(entity.getCounterpartyId()).isEqualTo(5L);
        assertThat(entity.getCounterpartyName()).isEqualTo("供应商A");
        assertThat(entity.getCounterpartyCode()).isEqualTo("S001");
        assertThat(entity.getSupplierCode()).isEqualTo("S001");
        assertThat(entity.getSupplierName()).isEqualTo("供应商A");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(1L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("主体A");
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getAmount()).isEqualByComparingTo("100.56");
        assertThat(entity.getAmount().scale()).isEqualTo(2);
        assertThat(entity.getItems()).isEmpty();
        verify(settlementSyncService).captureOriginalAllocationState(entity);
    }

    @Test
    void apply_shouldRejectSupplierBusinessTypeForStatementSettlement() {
        Payment entity = new Payment();
        PaymentRequest request = statementSettlementRequest(
                StatusConstants.DRAFT, "供应商", null, new BigDecimal("100.00"), SOURCE_STATEMENT_ID);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商付款已统一为总额付款，不允许关联供应商对账单");

        verify(paymentAllocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void apply_shouldApplyStatementSettlement() {
        Payment entity = new Payment();
        PaymentRequest request = statementSettlementRequest(
                StatusConstants.DRAFT, "物流商", null, new BigDecimal("100.00"), null);
        when(paymentAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(allocation("物流商", 9L, "F001", new BigDecimal("100.00")));
        when(paymentAllocationService.mergeCounterpartyCode("F001", "F001")).thenReturn("F001");
        when(settlementSyncService.resolveLegacySourceStatementId(entity)).thenReturn(SOURCE_STATEMENT_ID);

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getCounterpartyType()).isEqualTo("物流商");
        assertThat(entity.getCounterpartyId()).isEqualTo(9L);
        assertThat(entity.getCounterpartyCode()).isEqualTo("F001");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(1L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("主体A");
        assertThat(entity.getSourceStatementId()).isEqualTo(SOURCE_STATEMENT_ID);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void apply_shouldRejectCounterpartyIdMismatchInStatementSettlement() {
        Payment entity = new Payment();
        PaymentRequest request = statementSettlementRequest(
                StatusConstants.DRAFT, "物流商", 5L, new BigDecimal("100.00"), null);
        when(paymentAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(allocation("物流商", 9L, "F001", new BigDecimal("100.00")));

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款单往来方ID与来源对账单不一致");
    }
}
