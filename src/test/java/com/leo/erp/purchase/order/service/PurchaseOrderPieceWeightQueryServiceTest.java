package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                .thenAnswer(invocation -> {
                    RowMapper<PieceWeightResponse> mapper = invocation.getArgument(1);
                    return List.of(
                            mapper.mapRow(row(1, "2.037", "SO-001"), 0),
                            mapper.mapRow(row(2, "2.037", ""), 1)
                    );
                });

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
                .thenAnswer(invocation -> {
                    RowMapper<PieceWeightResponse> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row(1, "2.037", "SO-001"), 0));
                });

        List<PieceWeightResponse> result = service.getPieceWeightsBySalesOrderItemId(301L);

        assertThat(result).singleElement().satisfies(pieceWeight ->
                assertThat(pieceWeight.weightTon()).isEqualByComparingTo("2.037")
        );
    }

    private ResultSet row(int pieceNo, String weightTon, String salesOrderNo) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("piece_no")).thenReturn(pieceNo);
        when(rs.getBigDecimal("weight_ton")).thenReturn(new BigDecimal(weightTon));
        when(rs.getString("sales_order_no")).thenReturn(salesOrderNo);
        return rs;
    }
}
