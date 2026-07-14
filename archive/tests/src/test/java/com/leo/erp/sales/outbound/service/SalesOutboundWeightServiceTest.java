package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOutboundWeightServiceTest {

    @Test
    void shouldFallbackToRequestedPieceWeightWhenSourceIsMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(null, 3, new BigDecimal("1.234567891"), null);

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, null, null, 1);

        assertThat(weightTon).isEqualByComparingTo("3.70370367");
        verifyNoInteractions(jdbc);
    }

    @Test
    void shouldScaleExplicitWeightTonWhenFallbackIsUsed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(null, 3, new BigDecimal("9.999"), new BigDecimal("5.123456789"));

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, null, null, 1);

        assertThat(weightTon).isEqualByComparingTo("5.12345679");
        verifyNoInteractions(jdbc);
    }

    @Test
    void shouldFallbackWhenSourceQuantityIsMissingOrNonPositive() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);

        BigDecimal missingQuantityWeight = service.resolveOutboundWeightTon(
                request(9001L, null, new BigDecimal("1.250"), null),
                null,
                9001L,
                1
        );
        BigDecimal zeroQuantityWeight = service.resolveOutboundWeightTon(
                request(9001L, 0, new BigDecimal("1.250"), null),
                null,
                9001L,
                2
        );

        assertThat(missingQuantityWeight).isEqualByComparingTo("0.00000000");
        assertThat(zeroQuantityWeight).isEqualByComparingTo("0.00000000");
        verifyNoInteractions(jdbc);
    }

    @Test
    void shouldSumSourcePieceWeightsWhenEnoughRecordsExist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 2, new BigDecimal("9.999"), null);
        whenQueryWeights(jdbc, 9001L, List.of(
                new BigDecimal("2.222222225"),
                new BigDecimal("1.111111115"),
                new BigDecimal("0.500000000")
        ));

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, null, 9001L, 1);

        assertThat(weightTon).isEqualByComparingTo("3.33333334");
    }

    @Test
    void shouldReadPieceWeightsThroughJdbcRowMapper() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 1, new BigDecimal("9.999"), null);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getBigDecimal("weight_ton")).thenReturn(new BigDecimal("2.50000000"));
        when(jdbc.query(
                anyString(),
                ArgumentMatchers.<RowMapper<BigDecimal>>any(),
                eq(9001L)
        )).thenAnswer(invocation -> {
            RowMapper<BigDecimal> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, null, 9001L, 1);

        assertThat(weightTon).isEqualByComparingTo("2.50000000");
    }

    @Test
    void shouldUseSourceOrderAverageWeightWhenPieceWeightRecordsAreInsufficient() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 3, new BigDecimal("9.999"), null);
        SalesOrderItem sourceItem = sourceItem(5, new BigDecimal("10.000"));
        whenQueryWeights(jdbc, 9001L, List.of(new BigDecimal("2.00000000")));

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, sourceItem, 9001L, 1);

        assertThat(weightTon).isEqualByComparingTo("6.00000000");
    }

    @Test
    void shouldUseRoundedAverageWhenSourceWeightCannotBeRepresentedExactly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 2, new BigDecimal("9.999"), null);
        SalesOrderItem sourceItem = sourceItem(3, new BigDecimal("1.00000000"));
        whenQueryWeights(jdbc, 9001L, List.of(new BigDecimal("0.40000000")));

        BigDecimal weightTon = service.resolveOutboundWeightTon(request, sourceItem, 9001L, 1);

        assertThat(weightTon).isEqualByComparingTo("0.66666666");
    }

    @Test
    void shouldRejectWhenSourceOrderItemIsMissingAndPieceWeightRecordsAreInsufficient() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 2, new BigDecimal("9.999"), null);
        whenQueryWeights(jdbc, 9001L, List.of(new BigDecimal("1.00000000")));

        assertThatThrownBy(() -> service.resolveOutboundWeightTon(request, null, 9001L, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第7行来源销售订单明细重量不可用");
    }

    @Test
    void shouldRejectWhenSourceOrderItemQuantityOrWeightIsUnavailable() {
        assertSourceBackedWeightUnavailable(sourceItem(null, new BigDecimal("1.00000000")));
        assertSourceBackedWeightUnavailable(sourceItem(0, new BigDecimal("1.00000000")));
        assertSourceBackedWeightUnavailable(sourceItem(3, null));
    }

    private void assertSourceBackedWeightUnavailable(SalesOrderItem sourceItem) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SalesOutboundWeightService service = new SalesOutboundWeightService(jdbc);
        SalesOutboundItemRequest request = request(9001L, 2, new BigDecimal("9.999"), null);
        whenQueryWeights(jdbc, 9001L, List.of(new BigDecimal("1.00000000")));

        assertThatThrownBy(() -> service.resolveOutboundWeightTon(request, sourceItem, 9001L, 4))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第4行来源销售订单明细重量不可用");
    }

    private void whenQueryWeights(JdbcTemplate jdbc, Long sourceSalesOrderItemId, List<BigDecimal> weights) {
        when(jdbc.query(
                anyString(),
                ArgumentMatchers.<RowMapper<BigDecimal>>any(),
                eq(sourceSalesOrderItemId)
        )).thenReturn(weights);
    }

    private SalesOrderItem sourceItem(Integer quantity, BigDecimal weightTon) {
        SalesOrderItem item = new SalesOrderItem();
        item.setQuantity(quantity);
        item.setWeightTon(weightTon);
        return item;
    }

    private SalesOutboundItemRequest request(Long sourceSalesOrderItemId,
                                             Integer quantity,
                                             BigDecimal pieceWeightTon,
                                             BigDecimal weightTon) {
        return new SalesOutboundItemRequest(
                "SO-001",
                sourceSalesOrderItemId,
                "M1",
                "宝钢",
                "盘螺",
                "HRB400",
                "10",
                null,
                "吨",
                "一号库",
                "B1",
                quantity,
                "件",
                pieceWeightTon,
                1,
                weightTon,
                new BigDecimal("3000.00"),
                null
        );
    }
}
