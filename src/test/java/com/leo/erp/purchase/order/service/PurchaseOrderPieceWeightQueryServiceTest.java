package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderPieceWeightQueryServiceTest {

    @Test
    void shouldQueryPieceWeightsByPurchaseOrderItemId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderPieceWeightQueryService service = new PurchaseOrderPieceWeightQueryService(jdbc);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PieceWeightResponse>>any(), eq(7L)))
                .thenReturn(List.of(
                        new PieceWeightResponse(1, new BigDecimal("2.037"), "SO-001"),
                        new PieceWeightResponse(2, new BigDecimal("2.037"), "")
                ));

        List<PieceWeightResponse> result = service.getPieceWeights(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).pieceNo()).isEqualTo(1);
        assertThat(result.get(0).salesOrderNo()).isEqualTo("SO-001");
    }

    @Test
    void shouldQueryPieceWeightsBySalesOrderItemId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderPieceWeightQueryService service = new PurchaseOrderPieceWeightQueryService(jdbc);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PieceWeightResponse>>any(), eq(301L)))
                .thenReturn(List.of(new PieceWeightResponse(1, new BigDecimal("2.037"), "SO-001")));

        List<PieceWeightResponse> result = service.getPieceWeightsBySalesOrderItemId(301L);

        assertThat(result).singleElement().satisfies(pieceWeight ->
                assertThat(pieceWeight.weightTon()).isEqualByComparingTo("2.037")
        );
    }
}
