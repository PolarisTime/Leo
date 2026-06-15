package com.leo.erp.statement.supplier.service;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapperImpl;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierStatementResponseAssemblerTest {

    private final SupplierStatementResponseAssembler assembler =
            new SupplierStatementResponseAssembler(new SupplierStatementMapperImpl());

    @Test
    void shouldAssembleDetailResponseWithItems() {
        SupplierStatement statement = statement();
        SupplierStatementItem item = item();
        statement.setItems(List.of(item));

        SupplierStatementResponse response = assembler.toDetailResponse(statement);

        assertThat(response.statementNo()).isEqualTo("GYDZ-001");
        assertThat(response.supplierCode()).isEqualTo("S-001");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isEqualTo("RK-001");
        assertThat(response.items().get(0).sourceInboundItemId()).isEqualTo(101L);
        assertThat(response.items().get(0).weightAdjustmentAmount()).isEqualByComparingTo("10.00");
    }

    private SupplierStatement statement() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        statement.setStatementNo("GYDZ-001");
        statement.setSupplierCode("S-001");
        statement.setSupplierName("供应商甲");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 31));
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        statement.setPaymentAmount(BigDecimal.ZERO);
        statement.setClosingAmount(new BigDecimal("1000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");
        return statement;
    }

    private SupplierStatementItem item() {
        SupplierStatementItem item = new SupplierStatementItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourceNo("RK-001");
        item.setSourceInboundItemId(101L);
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
        item.setWeighWeightTon(new BigDecimal("1.010"));
        item.setWeightAdjustmentTon(new BigDecimal("0.010"));
        item.setWeightAdjustmentAmount(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }
}
