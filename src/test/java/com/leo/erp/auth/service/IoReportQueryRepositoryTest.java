package com.leo.erp.report.io.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IoReportQueryRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final IoReportQueryRepository repository = new IoReportQueryRepository(jdbcTemplate);

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    @Test
    void pageReturnsEmptyPageWhenTotalIsZero() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        Page<IoReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsEmptyPageWhenTotalIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(null);

        Page<IoReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsRowsWhenTotalIsPositive() {
        IoReportResponse row = new IoReportResponse(
                1L, LocalDate.of(2024, 6, 1), "采购入库", "PO-001",
                "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓", "B-001", 10, 0, "件",
                new BigDecimal("5.250"), BigDecimal.ZERO, "吨", "备注"
        );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        Page<IoReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().materialCode()).isEqualTo("M-001");
        assertThat(result.getContent().getFirst().businessType()).isEqualTo("采购入库");
    }

    @Test
    void pageWithKeywordAppliesLikeFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), "test", null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithBusinessTypeAppliesEqualityFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, "采购入库", null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithStartDateAppliesGreaterThanOrEqualFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, LocalDate.of(2024, 1, 1), null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithEndDateAppliesLessThanOrEqualFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, LocalDate.of(2024, 12, 31));

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithAllFiltersCombinesWhereClauses() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(
                new PageQuery(0, 10, "businessType", "asc"),
                "test", "采购入库",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByBusinessType() {
        IoReportResponse row = new IoReportResponse(
                1L, LocalDate.of(2024, 6, 1), "采购入库", "PO-001",
                "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓", "B-001", 10, 0, "件",
                new BigDecimal("5.250"), BigDecimal.ZERO, "吨", "备注"
        );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        Page<IoReportResponse> result = repository.page(
                new PageQuery(0, 10, "businessType", "desc"), null, null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void pageSortsBySourceNo() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "sourceNo", "asc"), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByMaterialCode() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "materialCode", "desc"), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByWarehouseName() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "warehouseName", "asc"), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByDefaultWhenSortByIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, "desc"), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByDefaultWhenSortByIsUnknown() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "unknownField", "asc"), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageHandlesNullSortDirection() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "businessType", null), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithSecondPageAppliesCorrectOffset() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(1, 10, null, null), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeAllDoesNotAddFilter() {
        DataScopeContext.set(1L, "io-report", "all");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeSelfAddsOwnerFilter() {
        DataScopeContext.set(1L, "io-report", "self");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeEmptyOwnerUserIdsReturnsEmptyResult() {
        DataScopeContext.set(1L, "io-report", "self", Set.of());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        Page<IoReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void pageWithCustomOwnerUserIdsAddsInFilter() {
        DataScopeContext.set(1L, "io-report", "self", Set.of(1L, 2L, 3L));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, null);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }
}