package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.api.AttachmentQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreightStatementViewAssemblerTest {

    @Test
    void shouldExposeSourceFreightBillTimeForItemSorting() {
        AttachmentQuery attachmentQuery = mock(AttachmentQuery.class);
        FreightBillStatementSourceQuery sourceQuery = mock(FreightBillStatementSourceQuery.class);
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(attachmentQuery, sourceQuery);

        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        FreightStatementItem item = new FreightStatementItem();
        item.setId(2L);
        item.setLineNo(1);
        item.setSourceFreightBillId(3L);
        statement.setItems(List.of(item));

        LocalDate billTime = LocalDate.of(2026, 7, 29);
        when(attachmentQuery.list("freight-statement", 1L)).thenReturn(List.of());
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(new BillSnapshot(
                3L, "FB-001", 4L, "C-001", "物流商", 5L, "结算主体", billTime,
                BigDecimal.ONE, BigDecimal.TEN, "已审核", List.of()
        )));

        FreightStatementView result = assembler.toDetailView(statement);

        assertThat(result.items()).singleElement()
                .extracting("sourceFreightBillTime")
                .isEqualTo(billTime);
    }
}
