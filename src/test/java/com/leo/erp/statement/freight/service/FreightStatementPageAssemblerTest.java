package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.api.AttachmentQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 列表组装必须带来源物流单运费信息。
 *
 * <p>背景（2026-11-04 生产）：物流对账单详情/编辑直接使用列表行的 items（列表已经带明细，
 * 前端不再请求详情接口），而列表组装当时传入的是空快照表，导致「单据明细」分组头的
 * 运费单价 / 运费总价恒为 0.00（如单据 376357437352976384 显示 0.00 / 0.00，
 * 来源物流单实际是 40.00 元/吨、1616.20 元）。</p>
 */
class FreightStatementPageAssemblerTest {

    @Test
    void shouldResolveSourceBillUnitPriceAndFreightForListRows() {
        AttachmentQuery attachmentQuery = mock(AttachmentQuery.class);
        FreightBillStatementSourceQuery sourceQuery = mock(FreightBillStatementSourceQuery.class);
        FreightStatementViewAssembler viewAssembler =
                new FreightStatementViewAssembler(attachmentQuery, sourceQuery);
        FreightStatementPageAssembler pageAssembler =
                new FreightStatementPageAssembler(viewAssembler);

        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        FreightStatementItem item = new FreightStatementItem();
        item.setId(2L);
        item.setLineNo(1);
        item.setSourceNo("FB-001");
        item.setSourceFreightBillId(3L);
        statement.setItems(List.of(item));

        when(attachmentQuery.listByRecordIds(any(), any())).thenReturn(Map.of());
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(new BillSnapshot(
                3L, "FB-001", 4L, "C-001", "物流商", 5L, "结算主体",
                LocalDate.of(2026, 10, 31),
                new BigDecimal("40.00"), new BigDecimal("1616.20"), "已审核", List.of()
        )));

        Page<FreightStatementView> page = pageAssembler.toViewPage(
                new PageImpl<>(List.of(statement)));

        assertThat(page.getContent()).singleElement().satisfies(view ->
                assertThat(view.items()).singleElement().satisfies(itemView -> {
                    assertThat(itemView.sourceFreightBillUnitPrice())
                            .isEqualByComparingTo("40.00");
                    assertThat(itemView.sourceFreightBillTotalFreight())
                            .isEqualByComparingTo("1616.20");
                }));
    }
}
