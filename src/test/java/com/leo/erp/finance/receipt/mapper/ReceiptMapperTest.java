package com.leo.erp.finance.receipt.mapper;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptMapperTest {

    private final ReceiptMapper mapper = new ReceiptMapperImpl();

    @Test
    void shouldMapReceiptToResponse() {
        Receipt entity = new Receipt();
        entity.setId(1L);
        entity.setReceiptNo("R001");
        entity.setCustomerCode("C001");
        entity.setCustomerName("客户A");
        entity.setProjectId(10L);
        entity.setProjectName("项目A");
        entity.setSourceStatementId(200L);
        entity.setReceiptDate(LocalDate.of(2026, 4, 10));
        entity.setPayType("银行转账");
        entity.setAmount(new BigDecimal("80000.00"));
        entity.setStatus("已收款");
        entity.setOperatorName("赵六");
        entity.setRemark("备注");

        ReceiptResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.receiptNo()).isEqualTo("R001");
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.sourceStatementId()).isEqualTo(200L);
        assertThat(response.receiptDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.payType()).isEqualTo("银行转账");
        assertThat(response.amount()).isEqualByComparingTo("80000.00");
        assertThat(response.status()).isEqualTo("已收款");
        assertThat(response.operatorName()).isEqualTo("赵六");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Receipt entity = new Receipt();
        entity.setId(1L);
        entity.setReceiptNo("R001");
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setReceiptDate(LocalDate.of(2026, 4, 10));
        entity.setPayType("银行转账");
        entity.setAmount(new BigDecimal("80000.00"));
        entity.setStatus("已收款");
        entity.setOperatorName("赵六");

        ReceiptResponse response = mapper.toResponse(entity);

        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.sourceStatementId()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
