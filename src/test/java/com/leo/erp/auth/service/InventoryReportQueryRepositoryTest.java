package com.leo.erp.report.inventory.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportQueryRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final InventoryReportQueryRepository repository = new InventoryReportQueryRepository(jdbcTemplate);

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    @Test
    void pageReturnsEmptyPageWhenTotalIsZero() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsEmptyPageWhenTotalIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(null);

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsRowsWhenTotalIsPositive() {
        InventoryReportResponse row = new InventoryReportResponse(
                1L, "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓", "B-001", 10, "件", new BigDecimal("5.250"), "吨", new BigDecimal("0.525")
        );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().materialCode()).isEqualTo("M-001");
    }

    @Test
    void pageWithKeywordAppliesLikeFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), "test", null, null);

        verify(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Number.class)
        );
    }

    @Test
    void pageWithWarehouseNameAppliesEqualityFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, "warehouse1", null);

        verify(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Number.class)
        );
    }

    @Test
    void pageWithCategoryAppliesEqualityFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, "category1");

        verify(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Number.class)
        );
    }

    @Test
    void pageWithAllFiltersCombinesWhereClauses() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "materialCode", "asc"), "test", "warehouse1", "category1");

        verify(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Number.class)
        );
    }

    @Test
    void pageSortsByBrand() {
        InventoryReportResponse row = new InventoryReportResponse(
                1L, "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓", "B-001", 10, "件", new BigDecimal("5.250"), "吨", null
        );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, "brand", "desc"), null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void pageSortsByCategory() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "category", "asc"), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByWarehouseName() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "warehouseName", "desc"), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByQuantity() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "quantity", "asc"), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByWeightTon() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "weightTon", "desc"), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByDefaultWhenSortByIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, "asc"), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeAllDoesNotAddFilter() {
        DataScopeContext.set(1L, "inventory-report", "all");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeSelfAddsOwnerFilter() {
        DataScopeContext.set(1L, "inventory-report", "self");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeEmptyOwnerUserIdsReturnsEmptyResult() {
        DataScopeContext.set(1L, "inventory-report", "self", Set.of());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void listReturnsEmptyListWhenNoData() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void listReturnsRowsWithData() {
        InventoryReportResponse row = new InventoryReportResponse(
                1L, "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓", "B-001", 10, "件", new BigDecimal("5.250"), "吨", new BigDecimal("0.525")
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().materialCode()).isEqualTo("M-001");
    }

    @Test
    void listWithAllFiltersAppliesWhereClauses() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.list(new PageQuery(0, 10, "brand", "desc"), "test", "warehouse1", "category1");

        verify(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void listWithDataScopeSelfAddsOwnerFilter() {
        DataScopeContext.set(1L, "inventory-report", "self");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.list(new PageQuery(0, 10, null, null), null, null, null);

        verify(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void listWithDataScopeEmptyOwnerUserIdsAddsFalseCondition() {
        DataScopeContext.set(1L, "inventory-report", "self", Set.of());
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void pageHandlesNullSortDirection() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "materialCode", null), null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }
}