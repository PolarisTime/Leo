package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceIssueResponseAssemblerTest {

    @Test
    void shouldAppendItemResponsesToDetailResponse() {
        InvoiceIssue entity = entity();
        InvoiceIssueMapper mapper = mock(InvoiceIssueMapper.class);
        when(mapper.toResponse(entity)).thenReturn(new InvoiceIssueResponse(
                1L,
                "KP-001",
                "INV-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                new BigDecimal("1000.00"),
                new BigDecimal("130.00"),
                "草稿",
                "财务A",
                "备注",
                List.of()
        ));

        InvoiceIssueResponseAssembler assembler = new InvoiceIssueResponseAssembler(mapper);

        InvoiceIssueResponse response = assembler.toDetailResponse(entity);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceSalesOrderItemId()).isEqualTo(101L);
        assertThat(response.items().get(0).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldDelegateSummaryResponseToMapper() {
        InvoiceIssue entity = entity();
        InvoiceIssueResponse summary = new InvoiceIssueResponse(
                1L, "KP-001", "INV-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "增值税专票",
                BigDecimal.ZERO, BigDecimal.ZERO, "草稿", "财务A", null, List.of()
        );
        InvoiceIssueMapper mapper = mock(InvoiceIssueMapper.class);
        when(mapper.toResponse(entity)).thenReturn(summary);

        assertThat(new InvoiceIssueResponseAssembler(mapper).toSummaryResponse(entity)).isSameAs(summary);
    }

    private InvoiceIssue entity() {
        InvoiceIssue entity = new InvoiceIssue();
        entity.setId(1L);
        entity.setItems(new ArrayList<>(List.of(item())));
        return entity;
    }

    private InvoiceIssueItem item() {
        InvoiceIssueItem item = new InvoiceIssueItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourceNo("SO-001");
        item.setSourceSalesOrderItemId(101L);
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
