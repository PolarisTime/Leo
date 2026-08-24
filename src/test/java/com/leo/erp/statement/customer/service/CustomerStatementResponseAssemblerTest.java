package com.leo.erp.statement.customer.service;

import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class CustomerStatementResponseAssemblerTest {

    @Test
    void toDetailResponse_shouldResolveDeliveryDateFromSourceSalesOrder() {
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderLogisticsSourceQuery sourceQuery = mock(SalesOrderLogisticsSourceQuery.class);
        CustomerStatement statement = new CustomerStatement();
        CustomerStatementItem item = new CustomerStatementItem();
        item.setId(1L);
        item.setLineNo(1);
        item.setSourceNo("SO-001");
        item.setSourceSalesOrderItemId(101L);
        statement.setItems(List.of(item));

        when(mapper.toResponse(statement)).thenReturn(new CustomerStatementResponse(
                10L, "CS-001", null, "客户A", null, "项目A", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, "待确认", false,
                null, List.of(), 20L
        ));
        SalesOrderSourceItemSnapshot sourceItem = new SalesOrderSourceItemSnapshot(
                101L, 1, null, "M-001", "品牌", "类别", "材质", "规格", null,
                "吨", null, null, null, null, null, null, null, null, 1, "吨",
                BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );
        SalesOrderSourceSnapshot sourceOrder = new SalesOrderSourceSnapshot(
                100L, "SO-001", null, null, null, 20L, "客户A", null, "项目A",
                null, null, LocalDate.of(2026, 8, 15), "销售", BigDecimal.ONE,
                BigDecimal.ONE, "完成销售", false, null, List.of(sourceItem)
        );
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(sourceOrder));

        CustomerStatementResponseAssembler assembler =
                new CustomerStatementResponseAssembler(mapper, sourceQuery);

        CustomerStatementResponse response = assembler.toDetailResponse(statement);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).deliveryDate())
                .isEqualTo(LocalDate.of(2026, 8, 15));
    }
}
