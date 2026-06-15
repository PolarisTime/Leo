package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.mapper.InvoiceReceiptMapper;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceReceiptResponseAssemblerTest {

    @Test
    void shouldAppendItemResponsesToDetailResponse() {
        InvoiceReceipt entity = entity();
        InvoiceReceiptMapper mapper = mock(InvoiceReceiptMapper.class);
        when(mapper.toResponse(entity)).thenReturn(new InvoiceReceiptResponse(
                1L,
                "SP-001",
                "INV-001",
                "供应商A",
                "发票抬头",
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                new BigDecimal("1000.00"),
                new BigDecimal("130.00"),
                "草稿",
                "财务A",
                "备注",
                List.of()
        ));

        InvoiceReceiptResponseAssembler assembler = new InvoiceReceiptResponseAssembler(mapper);

        InvoiceReceiptResponse response = assembler.toDetailResponse(entity);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourcePurchaseOrderItemId()).isEqualTo(201L);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldDelegateSummaryResponseToMapper() {
        InvoiceReceipt entity = entity();
        InvoiceReceiptResponse summary = new InvoiceReceiptResponse(
                1L, "SP-001", "INV-001", "供应商A", "发票抬头",
                LocalDate.of(2026, 4, 26), "增值税专票",
                BigDecimal.ZERO, BigDecimal.ZERO, "草稿", "财务A", null, List.of()
        );
        InvoiceReceiptMapper mapper = mock(InvoiceReceiptMapper.class);
        when(mapper.toResponse(entity)).thenReturn(summary);

        assertThat(new InvoiceReceiptResponseAssembler(mapper).toSummaryResponse(entity)).isSameAs(summary);
    }

    private InvoiceReceipt entity() {
        InvoiceReceipt entity = new InvoiceReceipt();
        entity.setId(1L);
        entity.setItems(new ArrayList<>(List.of(item())));
        return entity;
    }

    private InvoiceReceiptItem item() {
        InvoiceReceiptItem item = new InvoiceReceiptItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourceNo("PO-001");
        item.setSourcePurchaseOrderItemId(201L);
        item.setMaterialCode("M-1");
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName("仓库A");
        item.setBatchNo("B-001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.300"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.300"));
        item.setUnitPrice(new BigDecimal("3333.33"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }
}
