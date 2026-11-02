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

    @Test
    void shouldExposeDistinctSourceBillNosInItemOrder() {
        AttachmentQuery attachmentQuery = mock(AttachmentQuery.class);
        FreightBillStatementSourceQuery sourceQuery = mock(FreightBillStatementSourceQuery.class);
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(attachmentQuery, sourceQuery);

        FreightStatement statement = new FreightStatement();
        statement.setId(10L);
        statement.setItems(List.of(
                item(1, "FB-001"),
                item(2, "FB-001"),
                item(3, "FB-002"),
                item(4, null),
                item(5, "  ")
        ));

        FreightStatementView result = assembler.toView(statement, List.of());

        // 列表「关联物流单」列依赖该字段：去重、保持明细顺序、忽略空值/空白
        assertThat(result.sourceBillNos()).containsExactly("FB-001", "FB-002");
    }

    /** 来源物流单被软删（实时反查为空）时，仍按落库快照返回日期，避免详情显示「-」。 */
    @Test
    void shouldPreferPersistedSourceFreightBillTimeWhenLiveSourceBillMissing() {
        AttachmentQuery attachmentQuery = mock(AttachmentQuery.class);
        FreightBillStatementSourceQuery sourceQuery = mock(FreightBillStatementSourceQuery.class);
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(attachmentQuery, sourceQuery);

        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        FreightStatementItem item = new FreightStatementItem();
        item.setId(2L);
        item.setLineNo(1);
        item.setSourceFreightBillId(3L);
        item.setSourceFreightBillTime(LocalDate.of(2026, 7, 29));
        statement.setItems(List.of(item));

        when(attachmentQuery.list("freight-statement", 1L)).thenReturn(List.of());
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of());

        FreightStatementView result = assembler.toDetailView(statement);

        assertThat(result.items()).singleElement()
                .extracting("sourceFreightBillTime")
                .isEqualTo(LocalDate.of(2026, 7, 29));
    }

    /** 快照与实时反查都缺失时返回 null（历史行兜底），不得抛错。 */
    @Test
    void shouldReturnNullSourceFreightBillTimeWhenBothSnapshotAndLiveMissing() {
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

        when(attachmentQuery.list("freight-statement", 1L)).thenReturn(List.of());
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of());

        FreightStatementView result = assembler.toDetailView(statement);

        assertThat(result.items()).singleElement()
                .extracting("sourceFreightBillTime")
                .isNull();
    }

    private FreightStatementItem item(int lineNo, String sourceNo) {
        FreightStatementItem item = new FreightStatementItem();
        item.setLineNo(lineNo);
        item.setSourceNo(sourceNo);
        return item;
    }
}
