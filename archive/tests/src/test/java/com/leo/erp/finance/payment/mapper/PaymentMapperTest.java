package com.leo.erp.finance.payment.mapper;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapperImpl();

    @Test
    void shouldMapPaymentToResponse() {
        Payment entity = new Payment();
        entity.setId(1L);
        entity.setPaymentNo("P001");
        entity.setBusinessType("供应商");
        entity.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        entity.setCounterpartyName("供应商A");
        entity.setSourceStatementId(100L);
        entity.setSourcePurchaseOrderId(200L);
        entity.setPurchaseOrderNo("PO-200");
        entity.setSupplierCode("SUP-200");
        entity.setSupplierName("供应商快照");
        entity.setSettlementCompanyId(300L);
        entity.setSettlementCompanyName("结算主体快照");
        entity.setPaymentDate(LocalDate.of(2026, 3, 20));
        entity.setPayType("银行转账");
        entity.setAmount(new BigDecimal("15000.00"));
        entity.setStatus("已审核");
        entity.setOperatorName("王五");
        entity.setRemark("备注");

        PaymentResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.paymentNo()).isEqualTo("P001");
        assertThat(response.businessType()).isEqualTo("供应商");
        assertThat(response.paymentPurpose()).isEqualTo(PaymentPurposes.PURCHASE_PREPAYMENT);
        assertThat(response.counterpartyName()).isEqualTo("供应商A");
        assertThat(response.sourceStatementId()).isEqualTo(100L);
        assertThat(response.sourcePurchaseOrderId()).isEqualTo(200L);
        assertThat(response.purchaseOrderNo()).isEqualTo("PO-200");
        assertThat(response.supplierCode()).isEqualTo("SUP-200");
        assertThat(response.supplierName()).isEqualTo("供应商快照");
        assertThat(response.settlementCompanyId()).isEqualTo(300L);
        assertThat(response.settlementCompanyName()).isEqualTo("结算主体快照");
        assertThat(response.paymentDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(response.payType()).isEqualTo("银行转账");
        assertThat(response.amount()).isEqualByComparingTo("15000.00");
        assertThat(response.status()).isEqualTo("已审核");
        assertThat(response.operatorName()).isEqualTo("王五");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Payment entity = new Payment();
        entity.setId(1L);
        entity.setPaymentNo("P001");
        entity.setBusinessType("供应商");
        entity.setCounterpartyName("供应商A");
        entity.setPaymentDate(LocalDate.of(2026, 3, 20));
        entity.setPayType("银行转账");
        entity.setAmount(new BigDecimal("15000.00"));
        entity.setStatus("已审核");
        entity.setOperatorName("王五");

        PaymentResponse response = mapper.toResponse(entity);

        assertThat(response.sourceStatementId()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
