package com.leo.erp.statement.customer.service;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapperImpl;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementResponseAssemblerTest {

    private final CustomerStatementResponseAssembler assembler =
            new CustomerStatementResponseAssembler(new CustomerStatementMapperImpl());

    @Test
    void shouldAssembleDetailResponseWithItems() {
        CustomerStatement statement = statement();
        CustomerStatementItem item = item();
        statement.setItems(List.of(item));

        CustomerStatementResponse response = assembler.toDetailResponse(statement);

        assertThat(response.statementNo()).isEqualTo("KHDZ-001");
        assertThat(response.customerCode()).isEqualTo("C-001");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isEqualTo("SO-001");
        assertThat(response.items().get(0).sourceSalesOrderItemId()).isEqualTo(201L);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo("1000.00");
    }

    private CustomerStatement statement() {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setStatementNo("KHDZ-001");
        statement.setCustomerCode("C-001");
        statement.setCustomerName("客户甲");
        statement.setProjectId(100L);
        statement.setProjectName("项目A");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 31));
        statement.setSalesAmount(new BigDecimal("1000.00"));
        statement.setReceiptAmount(BigDecimal.ZERO);
        statement.setClosingAmount(new BigDecimal("1000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");
        return statement;
    }

    private CustomerStatementItem item() {
        CustomerStatementItem item = new CustomerStatementItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourceNo("SO-001");
        item.setSourceSalesOrderItemId(201L);
        item.setMaterialCode("M-001");
        item.setBrand("品牌A");
        item.setCategory("螺纹");
        item.setMaterial("盘螺");
        item.setSpec("HRB400");
        item.setLength("12");
        item.setUnit("吨");
        item.setBatchNo("LOT-001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }
}
