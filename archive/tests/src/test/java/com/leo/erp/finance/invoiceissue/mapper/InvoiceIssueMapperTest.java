package com.leo.erp.finance.invoiceissue.mapper;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssueMapperTest {

    private final InvoiceIssueMapper mapper = new InvoiceIssueMapperImpl();

    @Test
    void shouldMapInvoiceIssueToResponse() {
        InvoiceIssue entity = new InvoiceIssue();
        entity.setId(1L);
        entity.setIssueNo("I001");
        entity.setInvoiceNo("INV001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setInvoiceDate(LocalDate.of(2026, 1, 15));
        entity.setInvoiceType("增值税专用发票");
        entity.setAmount(new BigDecimal("50000.00"));
        entity.setTaxAmount(new BigDecimal("6500.00"));
        entity.setStatus("已确认");
        entity.setOperatorName("李四");
        entity.setRemark("备注");

        InvoiceIssueResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.issueNo()).isEqualTo("I001");
        assertThat(response.invoiceNo()).isEqualTo("INV001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(response.invoiceType()).isEqualTo("增值税专用发票");
        assertThat(response.amount()).isEqualByComparingTo("50000.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("6500.00");
        assertThat(response.status()).isEqualTo("已确认");
        assertThat(response.operatorName()).isEqualTo("李四");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        InvoiceIssue entity = new InvoiceIssue();
        entity.setId(1L);
        entity.setIssueNo("I001");
        entity.setInvoiceNo("INV001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setInvoiceDate(LocalDate.of(2026, 1, 15));
        entity.setInvoiceType("增值税专用发票");
        entity.setAmount(new BigDecimal("50000.00"));
        entity.setTaxAmount(new BigDecimal("6500.00"));
        entity.setStatus("已确认");
        entity.setOperatorName("李四");

        InvoiceIssueResponse response = mapper.toResponse(entity);

        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
