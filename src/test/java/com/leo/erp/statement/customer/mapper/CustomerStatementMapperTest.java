package com.leo.erp.statement.customer.mapper;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementMapperTest {

    private final CustomerStatementMapperImpl mapper = new CustomerStatementMapperImpl();

    @Test
    void shouldMapEntityToResponse() {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setStatementNo("KHDZ-001");
        statement.setCustomerCode("C-001");
        statement.setCustomerName("客户甲");
        statement.setProjectId(100L);
        statement.setProjectName("项目A");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 31));
        statement.setSalesAmount(new BigDecimal("10000.00"));
        statement.setReceiptAmount(new BigDecimal("8000.00"));
        statement.setClosingAmount(new BigDecimal("2000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");

        CustomerStatementResponse response = mapper.toResponse(statement);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.statementNo()).isEqualTo("KHDZ-001");
        assertThat(response.customerCode()).isEqualTo("C-001");
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.projectId()).isEqualTo(100L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(response.salesAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.receiptAmount()).isEqualByComparingTo("8000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.status()).isEqualTo("待确认");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(2L);
        statement.setStatementNo("KHDZ-002");
        statement.setCustomerCode(null);
        statement.setCustomerName("客户乙");
        statement.setProjectId(null);
        statement.setProjectName("项目B");
        statement.setStartDate(LocalDate.of(2026, 6, 1));
        statement.setEndDate(LocalDate.of(2026, 6, 30));
        statement.setSalesAmount(new BigDecimal("5000.00"));
        statement.setReceiptAmount(new BigDecimal("5000.00"));
        statement.setClosingAmount(new BigDecimal("0.00"));
        statement.setStatus("已确认");
        statement.setRemark(null);

        CustomerStatementResponse response = mapper.toResponse(statement);

        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }
}
