package com.leo.erp.report.inventory.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportQueryRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    void pageUsesEffectiveInventoryStatusesAndSeparatesPreOutboundReservations() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        var sql = forClass(String.class);
        var params = forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Number.class));

        assertThat(sql.getValue())
                .contains("inbound.status IN (:effectiveInboundStatuses)")
                .contains("outbound.status IN (:effectiveOutboundStatuses)")
                .contains("outbound.status IN (:reservedOutboundStatuses)")
                .contains("SUM(movement.on_hand_quantity_delta) AS on_hand_quantity")
                .contains("SUM(movement.reserved_quantity_delta) AS reserved_quantity")
                .contains("SUM(stock.on_hand_quantity) - SUM(stock.reserved_quantity) AS available_quantity");
        assertThat(params.getValue().getValue("effectiveInboundStatuses"))
                .isEqualTo(Set.of("已审核", "完成入库"));
        assertThat(params.getValue().getValue("effectiveOutboundStatuses"))
                .isEqualTo(Set.of("已审核"));
        assertThat(params.getValue().getValue("reservedOutboundStatuses"))
                .isEqualTo(Set.of("预出库"));
    }

    @Test
    void pageUsesActualWeighWeightForPurchaseInboundInventory() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));

        assertThat(sql.getValue())
                .contains("COALESCE(item.weigh_weight_ton, item.weight_ton) AS on_hand_weight_delta");
    }

    @Test
    void pageUsesLineWarehouseForOutboundMovements() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, false);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));

        assertThat(sql.getValue())
                .contains("COALESCE(NULLIF(item.warehouse_name, ''), outbound.warehouse_name) AS warehouse_name")
                .doesNotContain("outbound.warehouse_name AS warehouse_name");
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

        String value = sql.getValue();
        int inventoryReportEnd = value.indexOf("SELECT COUNT(1) FROM inventory_report report");
        assertThat(inventoryReportEnd).isPositive();
        assertThat(value.substring(0, inventoryReportEnd))
                .doesNotContain("on_hand_quantity <> 0")
                .doesNotContain("reserved_quantity <> 0");
        assertThat(value.substring(inventoryReportEnd))
                .contains("report.on_hand_quantity <> 0")
                .contains("report.on_hand_weight_ton <> 0")
                .contains("report.reserved_quantity <> 0")
                .contains("report.reserved_weight_ton <> 0");
    }

    @Test
    void pageIncludesZeroStockRowsWhenIncludeOutboundIsEnabled() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(new PageQuery(0, 10, null, null), null, null, null, true);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));

        assertThat(sql.getValue()).doesNotContain("on_hand_quantity <> 0");
        assertThat(sql.getValue()).doesNotContain("reserved_quantity <> 0");
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
                    when(rs.getInt("on_hand_quantity")).thenReturn(18);
                    when(rs.getInt("reserved_quantity")).thenReturn(5);
                    when(rs.getInt("available_quantity")).thenReturn(13);
                    when(rs.getString("quantity_unit")).thenReturn("件");
                    when(rs.getBigDecimal("weight_ton")).thenReturn(new BigDecimal("9.450"));
                    when(rs.getBigDecimal("on_hand_weight_ton")).thenReturn(new BigDecimal("9.450"));
                    when(rs.getBigDecimal("reserved_weight_ton")).thenReturn(new BigDecimal("2.625"));
                    when(rs.getBigDecimal("available_weight_ton")).thenReturn(new BigDecimal("6.825"));
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
        JsonNode json = OBJECT_MAPPER.valueToTree(result.getContent().getFirst());
        assertThat(json.path("quantity").asInt()).isEqualTo(18);
        assertThat(json.path("onHandQuantity").asInt()).isEqualTo(18);
        assertThat(json.path("reservedQuantity").asInt()).isEqualTo(5);
        assertThat(json.path("availableQuantity").asInt()).isEqualTo(13);
        assertThat(json.path("weightTon").decimalValue()).isEqualByComparingTo("9.450");
        assertThat(json.path("onHandWeightTon").decimalValue()).isEqualByComparingTo("9.450");
        assertThat(json.path("reservedWeightTon").decimalValue()).isEqualByComparingTo("2.625");
        assertThat(json.path("availableWeightTon").decimalValue()).isEqualByComparingTo("6.825");
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
    void listBindsAllStockFiltersToSqlParameters() {
        CapturedQuery query = captureListQuery(
                new PageQuery(0, 10, null, null), "  AbC  ", "二号码头", "盘螺", false);

        assertThat(query.sql()).contains("report.on_hand_quantity <> 0");
        assertThat(query.sql()).contains("report.reserved_quantity <> 0");
        assertThat(query.sql()).contains("LOWER(COALESCE(stock.material_code, '')) LIKE :keyword");
        assertThat(query.sql()).contains("LOWER(COALESCE(stock.brand, '')) LIKE :keyword");
        assertThat(query.sql()).contains("LOWER(COALESCE(stock.spec, '')) LIKE :keyword");
        assertThat(query.sql()).contains("LOWER(COALESCE(stock.material, '')) LIKE :keyword");
        assertThat(query.sql()).contains("stock.warehouse_name = :warehouseName");
        assertThat(query.sql()).contains("stock.category = :category");
        assertThat(query.params().getValue("keyword")).isEqualTo("%abc%");
        assertThat(query.params().getValue("warehouseName")).isEqualTo("二号码头");
        assertThat(query.params().getValue("category")).isEqualTo("盘螺");
    }

    @Test
    void listBuildsSqlForSupportedSortFields() {
        assertThat(captureListQuery(
                new PageQuery(0, 10, "category", "asc"), null, null, null, false).sql())
                .contains("ROW_NUMBER() OVER (ORDER BY LOWER(COALESCE(report.category, '')) ASC");
        assertThat(captureListQuery(
                new PageQuery(0, 10, "warehouseName", "desc"), null, null, null, false).sql())
                .contains("ROW_NUMBER() OVER (ORDER BY LOWER(COALESCE(report.warehouse_name, '')) DESC");
        assertThat(captureListQuery(
                new PageQuery(0, 10, "quantity", "asc"), null, null, null, false).sql())
                .contains("ROW_NUMBER() OVER (ORDER BY report.on_hand_quantity ASC");
        assertThat(captureListQuery(
                new PageQuery(0, 10, "weightTon", "desc"), null, null, null, false).sql())
                .contains("ROW_NUMBER() OVER (ORDER BY report.on_hand_weight_ton DESC");
    }

    @Test
    void listAddsDataScopeOwnerFilterForInboundAndOutboundSql() {
        DataScopeContext.set(7L, "inventory-report", "self", Set.of(7L, 8L));

        CapturedQuery query = captureListQuery(new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(query.sql()).contains("AND inbound.created_by IN (:dataScopeOwnerUserIds)");
        assertThat(query.sql()).contains("AND outbound.created_by IN (:dataScopeOwnerUserIds)");
        assertThat(query.params().getValue("dataScopeOwnerUserIds")).isEqualTo(Set.of(7L, 8L));
    }

    @Test
    void listMapsNullItemsJsonToEmptyItems() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<InventoryReportResponse> mapper = invocation.getArgument(2, RowMapper.class);
                    return List.of(mapper.mapRow(resultSetWithItems(null), 0));
                });

        List<InventoryReportResponse> result = repository.list(
                new PageQuery(0, 10, null, null), null, null, null, false);

        assertThat(result.getFirst().items()).isEmpty();
    }

    @Test
    void listRejectsMalformedItemsJson() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<InventoryReportResponse> mapper = invocation.getArgument(2, RowMapper.class);
                    return List.of(mapper.mapRow(resultSetWithItems("not-json"), 0));
                });

        assertThatThrownBy(() -> repository.list(
                new PageQuery(0, 10, null, null), null, null, null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to parse inventory report items");
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

    private CapturedQuery captureListQuery(PageQuery query, String keyword, String warehouseName, String category,
                                           boolean includeOutbound) {
        NamedParameterJdbcTemplate localJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        InventoryReportQueryRepository localRepository = new InventoryReportQueryRepository(localJdbcTemplate);
        when(localJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        localRepository.list(query, keyword, warehouseName, category, includeOutbound);

        var sql = forClass(String.class);
        var params = forClass(MapSqlParameterSource.class);
        verify(localJdbcTemplate).query(sql.capture(), params.capture(), any(RowMapper.class));
        return new CapturedQuery(sql.getValue(), params.getValue());
    }

    private static ResultSet resultSetWithItems(Object itemsJson) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("material_code")).thenReturn("M-001");
        when(rs.getString("brand")).thenReturn("品牌A");
        when(rs.getString("material")).thenReturn("材质A");
        when(rs.getString("category")).thenReturn("类别A");
        when(rs.getString("spec")).thenReturn("规格A");
        when(rs.getString("length")).thenReturn("9m");
        when(rs.getString("warehouse_name")).thenReturn("一号仓");
        when(rs.getString("batch_no")).thenReturn("B-001");
        when(rs.getInt("quantity")).thenReturn(10);
        when(rs.getString("quantity_unit")).thenReturn("件");
        when(rs.getBigDecimal("weight_ton")).thenReturn(new BigDecimal("5.250"));
        when(rs.getString("unit")).thenReturn("吨");
        when(rs.getBigDecimal("piece_weight_ton")).thenReturn(new BigDecimal("0.525"));
        when(rs.getObject("items_json")).thenReturn(itemsJson);
        return rs;
    }

    private record CapturedQuery(String sql, MapSqlParameterSource params) {
    }
}
