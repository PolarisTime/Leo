package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBalanceQueryService 分页与过滤参数装配测试。
 */
@ExtendWith(MockitoExtension.class)
class InventoryBalanceQueryServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private InventoryBalanceQueryService service;

    @Test
    void page_shouldAssemblePageResponseAndBindFilters() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(3L);
        InventoryBalanceResponse row = new InventoryBalanceResponse(
                100L, "M001", "宝钢", "钢", "Φ20", "12m", "吨",
                3L, "库房B", "B001",
                12L, new BigDecimal("36000.00"), new BigDecimal("3000.00"));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        PageResponse<InventoryBalanceResponse> result =
                service.page(new PageQuery(0, 2, null, null), "m001", 3L, 100L);

        assertThat(result.content()).containsExactly(row);
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.currentPage()).isZero();
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(result.hasMore()).isTrue();

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("materialId")).isEqualTo(100L);
        assertThat(params.getValue("warehouseId")).isEqualTo(3L);
        assertThat(params.getValue("keyword")).isEqualTo("%m001%");
        assertThat(params.getValue("limit")).isEqualTo(2);
        assertThat(params.getValue("offset")).isEqualTo(0L);
    }

    @Test
    void page_shouldNotBindOptionalFiltersWhenAbsent() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        PageResponse<InventoryBalanceResponse> result =
                service.page(new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasMore()).isFalse();

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().hasValue("materialId")).isFalse();
        assertThat(captor.getValue().hasValue("warehouseId")).isFalse();
        assertThat(captor.getValue().hasValue("keyword")).isFalse();
    }
}
