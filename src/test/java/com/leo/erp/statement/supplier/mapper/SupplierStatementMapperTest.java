package com.leo.erp.statement.supplier.mapper;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierStatementMapperTest {

    private final SupplierStatementMapperImpl mapper = new SupplierStatementMapperImpl();

    @Test
    void shouldMapEntityToResponse() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        statement.setStatementNo("GYSDZ-001");
        statement.setSupplierName("供应商甲");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 31));
        statement.setPurchaseAmount(new BigDecimal("20000.00"));
        statement.setPaymentAmount(new BigDecimal("15000.00"));
        statement.setClosingAmount(new BigDecimal("5000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");

        SupplierStatementResponse response = mapper.toResponse(statement);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.statementNo()).isEqualTo("GYSDZ-001");
        assertThat(response.supplierName()).isEqualTo("供应商甲");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(response.purchaseAmount()).isEqualByComparingTo("20000.00");
        assertThat(response.paymentAmount()).isEqualByComparingTo("15000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.status()).isEqualTo("待确认");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(2L);
        statement.setStatementNo("GYSDZ-002");
        statement.setSupplierName("供应商乙");
        statement.setStartDate(LocalDate.of(2026, 6, 1));
        statement.setEndDate(LocalDate.of(2026, 6, 30));
        statement.setPurchaseAmount(new BigDecimal("8000.00"));
        statement.setPaymentAmount(new BigDecimal("8000.00"));
        statement.setClosingAmount(new BigDecimal("0.00"));
        statement.setStatus("已确认");
        statement.setRemark(null);

        SupplierStatementResponse response = mapper.toResponse(statement);

        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }
}
