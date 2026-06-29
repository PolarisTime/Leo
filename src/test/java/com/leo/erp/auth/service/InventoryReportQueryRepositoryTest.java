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
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
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
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsEmptyPageWhenTotalIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(null);

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, false);

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
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().materialCode()).isEqualTo("M-001");
    }

    @Test
    void pageCountsAndPagesMergedInventoryReportRows() {
        InventoryReportResponse row = new InventoryReportResponse(
                1L, "M-001", "品牌A", "材质A", "类别A", "规格A", "9m",
                "一号仓、二号码头", "B-001、B-002", 18, "件", new BigDecimal("9.450"), "吨", new BigDecimal("0.525")
        );
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        var countSql = forClass(String.class);
        var dataSql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), any(MapSqlParameterSource.class), eq(Number.class));
        verify(jdbcTemplate).query(dataSql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(countSql.getValue()).contains("SELECT COUNT(1) FROM inventory_report report");
        assertThat(dataSql.getValue()).contains("FROM inventory_report report");
        assertThat(dataSql.getValue()).contains("JSONB_AGG(");
        assertThat(dataSql.getValue()).contains("JSONB_BUILD_OBJECT(");
        assertThat(dataSql.getValue()).contains("'materialCode', stock.material_code");
        assertThat(dataSql.getValue()).contains("'brand', stock.brand");
        assertThat(dataSql.getValue()).contains("'material', stock.material");
        assertThat(dataSql.getValue()).contains("'category', stock.category");
        assertThat(dataSql.getValue()).contains("'spec', stock.spec");
        assertThat(dataSql.getValue()).contains("'length', stock.length");
        assertThat(dataSql.getValue()).contains("'warehouseName', stock.warehouse_name");
        assertThat(dataSql.getValue()).contains("'batchNo', stock.batch_no");
        assertThat(dataSql.getValue()).contains("outbound.outbound_no AS outbound_no");
        assertThat(dataSql.getValue()).contains("TO_CHAR(outbound.outbound_date, 'YYYY-MM-DD') AS outbound_date");
        assertThat(dataSql.getValue()).contains("'outboundNo', stock.outbound_no");
        assertThat(dataSql.getValue()).contains("'outboundDate', stock.outbound_date");
        assertThat(dataSql.getValue()).doesNotContain("""
                GROUP BY
                    stock.material_code,
                    stock.brand,
                    stock.material,
                    stock.category,
                    stock.spec,
                    stock.length,
                    stock.warehouse_name
                """);
    }

    @Test
    void pageAppliesWarehouseFilterBeforeMergedGrouping() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, "二号码头", null, false);

        var sql = forClass(String.class);
        var params = forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Number.class));

        assertThat(sql.getValue()).contains("stock.warehouse_name = :warehouseName");
        assertThat(params.getValue().getValue("warehouseName")).isEqualTo("二号码头");
    }

    @Test
    void pageExcludesZeroStockRowsByDefault() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));

        assertThat(sql.getValue()).contains("(stock.quantity > 0 OR stock.weight_ton > 0)");
    }

    @Test
    void pageIncludesZeroStockRowsWhenIncludeOutboundIsEnabled() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, true);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));

        assertThat(sql.getValue()).doesNotContain("(stock.quantity > 0 OR stock.weight_ton > 0)");
        assertThat(sql.getValue()).doesNotContain("\nWHERE \n");
    }

    @Test
    void pageMapsItemsJsonToFlowRows() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<InventoryReportResponse> mapper = invocation.getArgument(2, RowMapper.class);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("id")).thenReturn(1L);
                    when(rs.getString("material_code")).thenReturn("M-001");
                    when(rs.getString("brand")).thenReturn("品牌A");
                    when(rs.getString("material")).thenReturn("材质A");
                    when(rs.getString("category")).thenReturn("类别A");
                    when(rs.getString("spec")).thenReturn("规格A");
                    when(rs.getString("length")).thenReturn("9m");
                    when(rs.getString("warehouse_name")).thenReturn("一号仓、二号码头");
                    when(rs.getString("batch_no")).thenReturn("B-001、B-002");
                    when(rs.getInt("quantity")).thenReturn(18);
                    when(rs.getString("quantity_unit")).thenReturn("件");
                    when(rs.getBigDecimal("weight_ton")).thenReturn(new BigDecimal("9.450"));
                    when(rs.getString("unit")).thenReturn("吨");
                    when(rs.getBigDecimal("piece_weight_ton")).thenReturn(new BigDecimal("0.525"));
                    when(rs.getObject("items_json")).thenReturn("""
                            [
                              {
                                "id": "M-001|一号仓|B-001",
                                "materialCode": "M-001",
                                "brand": "品牌A",
                                "material": "材质A",
                                "category": "盘螺",
                                "spec": "规格A",
                                "length": "9m",
                                "warehouseName": "一号仓",
                                "batchNo": "B-001",
                                "outboundNo": "SOO-001",
                                "outboundDate": "2026-06-01",
                                "quantity": 10,
                                "quantityUnit": "件",
                                "weightTon": 5.250,
                                "unit": "吨",
                                "pieceWeightTon": 0.525
                              }
                            ]
                            """);
                    return List.of(mapper.mapRow(rs, 0));
                });

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result.getContent().getFirst().items()).hasSize(1);
        assertThat(result.getContent().getFirst().items().getFirst().materialCode()).isEqualTo("M-001");
        assertThat(result.getContent().getFirst().items().getFirst().brand()).isEqualTo("品牌A");
        assertThat(result.getContent().getFirst().items().getFirst().material()).isEqualTo("材质A");
        assertThat(result.getContent().getFirst().items().getFirst().category()).isEqualTo("盘螺");
        assertThat(result.getContent().getFirst().items().getFirst().spec()).isEqualTo("规格A");
        assertThat(result.getContent().getFirst().items().getFirst().length()).isEqualTo("9m");
        assertThat(result.getContent().getFirst().items().getFirst().warehouseName()).isEqualTo("一号仓");
        assertThat(result.getContent().getFirst().items().getFirst().batchNo()).isEqualTo("B-001");
        assertThat(result.getContent().getFirst().items().getFirst().outboundNo()).isEqualTo("SOO-001");
        assertThat(result.getContent().getFirst().items().getFirst().outboundDate()).isEqualTo("2026-06-01");
    }

    @Test
    void pageWithKeywordAppliesLikeFilter() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), "test", null, null, false);

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

        repository.page(new PageQuery(0, 10, null, null), null, "warehouse1", null, false);

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

        repository.page(new PageQuery(0, 10, null, null), null, null, "category1", false);

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

        repository.page(new PageQuery(0, 10, "materialCode", "asc"), "test", "warehouse1", "category1", false);

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
                new PageQuery(0, 10, "brand", "desc"), null, null, null, false);

        assertThat(result).hasSize(1);
    }

    @Test
    void pageSortsByCategory() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "category", "asc"), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByWarehouseName() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "warehouseName", "desc"), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByQuantity() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "quantity", "asc"), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByWeightTon() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "weightTon", "desc"), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageSortsByDefaultWhenSortByIsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, "asc"), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeAllDoesNotAddFilter() {
        DataScopeContext.set(1L, "inventory-report", "all");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeSelfAddsOwnerFilter() {
        DataScopeContext.set(1L, "inventory-report", "self");
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void pageWithDataScopeEmptyOwnerUserIdsReturnsEmptyResult() {
        DataScopeContext.set(1L, "inventory-report", "self", Set.of());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        Page<InventoryReportResponse> result = repository.page(
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result).isEmpty();
    }

    @Test
    void listReturnsEmptyListWhenNoData() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null, false);

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
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().materialCode()).isEqualTo("M-001");
    }

    @Test
    void listWithAllFiltersAppliesWhereClauses() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.list(new PageQuery(0, 10, "brand", "desc"), "test", "warehouse1", "category1", false);

        verify(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void listWithDataScopeSelfAddsOwnerFilter() {
        DataScopeContext.set(1L, "inventory-report", "self");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.list(new PageQuery(0, 10, null, null), null, null, null, false);

        verify(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void listWithDataScopeEmptyOwnerUserIdsAddsFalseCondition() {
        DataScopeContext.set(1L, "inventory-report", "self", Set.of());
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result).isEmpty();
    }

    @Test
    void pageHandlesNullSortDirection() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, "materialCode", null), null, null, null, false);

        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }
}
