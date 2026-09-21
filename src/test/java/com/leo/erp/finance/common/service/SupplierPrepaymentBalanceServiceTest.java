package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 供应商预付款余额校验测试（6.2B）：
 * 预付款在采购入库单“已审核/完成入库”时即抵扣，不再要求来源采购订单“完成采购”。
 */
@ExtendWith(MockitoExtension.class)
class SupplierPrepaymentBalanceServiceTest {

    private static final Long SETTLEMENT_COMPANY_ID = 30L;
    private static final Long SUPPLIER_ID = 40L;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private SupplierLedgerLockService supplierLedgerLockService;

    @InjectMocks
    private SupplierPrepaymentBalanceService service;

    @Test
    void shouldIgnoreNonAuditTransition() {
        Receipt receipt = receipt(new BigDecimal("100.00"));

        service.validateSupplierReceipt(receipt, StatusConstants.DRAFT);

        verifyNoInteractions(jdbcTemplate, supplierLedgerLockService);
    }

    @Test
    void shouldIgnoreNonSupplierPurpose() {
        Receipt receipt = receipt(new BigDecimal("100.00"));
        receipt.setReceiptPurpose(ReceiptPurposes.CUSTOMER_STATEMENT_SETTLEMENT);

        service.validateSupplierReceipt(receipt, StatusConstants.AUDITED);

        verifyNoInteractions(jdbcTemplate, supplierLedgerLockService);
    }

    @Test
    void shouldIgnoreNonPrepaymentRefundSupplierPurpose() {
        Receipt receipt = receipt(new BigDecimal("100.00"));
        receipt.setReceiptPurpose(ReceiptPurposes.SUPPLIER_OTHER_RECEIPT);

        service.validateSupplierReceipt(receipt, StatusConstants.AUDITED);

        verify(supplierLedgerLockService).lock(SETTLEMENT_COMPANY_ID, SUPPLIER_ID);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRejectMissingSupplierIdentity() {
        Receipt receipt = receipt(new BigDecimal("100.00"));
        receipt.setCounterpartyId(null);

        assertThatThrownBy(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少供应商或结算主体身份");
    }

    @Test
    void shouldDeductPrepaymentOnceInboundAudited() {
        // 采购订单未完成采购，但入库单已审核 -> 余额已扣减（可用余额=0），退款被拒
        stubBalance(new BigDecimal("0.00"));
        Receipt receipt = receipt(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过当前预付款余额");
        verify(supplierLedgerLockService).lock(SETTLEMENT_COMPANY_ID, SUPPLIER_ID);
    }

    @Test
    void shouldAllowRefundWithinRemainingBalance() {
        stubBalance(new BigDecimal("150.00"));
        Receipt receipt = receipt(new BigDecimal("100.00"));

        assertThatCode(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTreatNullBalanceAsZero() {
        stubBalance(null);
        Receipt receipt = receipt(BigDecimal.ZERO);

        assertThatCode(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRefundWhenBalanceZero() {
        stubBalance(new BigDecimal("0.00"));
        Receipt receipt = receipt(new BigDecimal("0.01"));

        assertThatThrownBy(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0");
    }

    @Test
    void shouldClampNegativeBalanceToZero() {
        stubBalance(new BigDecimal("-50.00"));
        Receipt receipt = receipt(new BigDecimal("0.01"));

        assertThatThrownBy(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldHandleMaxValueAmountWithoutOverflow() {
        stubBalance(new BigDecimal("9223372036854775807.00"));
        Receipt receipt = receipt(new BigDecimal("9223372036854775807.00"));

        assertThatCode(() -> service.validateSupplierReceipt(receipt, StatusConstants.AUDITED))
                .doesNotThrowAnyException();
    }

    @Test
    void balanceSqlShouldNotRequirePurchaseCompleted() {
        stubBalance(new BigDecimal("100.00"));
        Receipt receipt = receipt(new BigDecimal("10.00"));

        service.validateSupplierReceipt(receipt, StatusConstants.AUDITED);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(BigDecimal.class));
        String sql = sqlCaptor.getValue();
        assertThat(sql).doesNotContain("完成采购");
        assertThat(sql).doesNotContain("source_order.status");
        assertThat(sql).contains("inbound.status IN ('已审核', '完成入库')");
        assertThat(sql).contains("inbound.deleted_flag = FALSE");
    }

    @Test
    void balanceSqlShouldScopeSettlementCompanyAndSupplier() {
        stubBalance(new BigDecimal("100.00"));
        Receipt receipt = receipt(new BigDecimal("10.00"));

        service.validateSupplierReceipt(receipt, StatusConstants.AUDITED);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(anyString(), paramsCaptor.capture(), eq(BigDecimal.class));
        assertThat(paramsCaptor.getValue().getValue("settlementCompanyId")).isEqualTo(SETTLEMENT_COMPANY_ID);
        assertThat(paramsCaptor.getValue().getValue("supplierId")).isEqualTo(SUPPLIER_ID);
    }

    private void stubBalance(BigDecimal balance) {
        lenient().when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(balance);
    }

    private Receipt receipt(BigDecimal amount) {
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RC001");
        receipt.setCounterpartyId(SUPPLIER_ID);
        receipt.setSettlementCompanyId(SETTLEMENT_COMPANY_ID);
        receipt.setReceiptPurpose(ReceiptPurposes.SUPPLIER_PREPAYMENT_REFUND);
        receipt.setAmount(amount);
        return receipt;
    }
}
