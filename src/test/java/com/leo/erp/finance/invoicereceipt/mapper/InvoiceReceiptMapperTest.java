package com.leo.erp.finance.invoicereceipt.mapper;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReceiptMapperTest {

    private final InvoiceReceiptMapper mapper = new InvoiceReceiptMapperImpl();

    @Test
    void shouldMapInvoiceReceiptToResponse() {
        InvoiceReceipt entity = new InvoiceReceipt();
        entity.setId(1L);
        entity.setReceiveNo("R001");
        entity.setInvoiceNo("INV001");
        entity.setSupplierName("供应商A");
        entity.setInvoiceTitle("发票抬头");
        entity.setInvoiceDate(LocalDate.of(2026, 1, 15));
        entity.setInvoiceType("增值税专用发票");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setStatus("已确认");
        entity.setOperatorName("张三");
        entity.setRemark("备注");

        InvoiceReceiptResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.receiveNo()).isEqualTo("R001");
        assertThat(response.invoiceNo()).isEqualTo("INV001");
        assertThat(response.supplierName()).isEqualTo("供应商A");
        assertThat(response.invoiceTitle()).isEqualTo("发票抬头");
        assertThat(response.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(response.invoiceType()).isEqualTo("增值税专用发票");
        assertThat(response.amount()).isEqualByComparingTo("10000.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("1300.00");
        assertThat(response.status()).isEqualTo("已确认");
        assertThat(response.operatorName()).isEqualTo("张三");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        InvoiceReceipt entity = new InvoiceReceipt();
        entity.setId(1L);
        entity.setReceiveNo("R001");
        entity.setInvoiceNo("INV001");
        entity.setSupplierName("供应商A");
        entity.setInvoiceDate(LocalDate.of(2026, 1, 15));
        entity.setInvoiceType("增值税专用发票");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setStatus("已确认");
        entity.setOperatorName("张三");

        InvoiceReceiptResponse response = mapper.toResponse(entity);

        assertThat(response.invoiceTitle()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
