package com.leo.erp.report.io.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IoReportQueryRepositoryRowMapperTest {

    @Test
    void pageMapsRowsFromResultSet() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.resultSetRows = List.of(Map.ofEntries(
                Map.entry("id", 3L),
                Map.entry("source_document_id", 30L),
                Map.entry("material_id", 300L),
                Map.entry("warehouse_id", 3000L),
                Map.entry("business_date", LocalDate.of(2026, 5, 2)),
                Map.entry("business_type", "采购入库"),
                Map.entry("source_no", "PI-001"),
                Map.entry("material_code", "M-001"),
                Map.entry("brand", "品牌A"),
                Map.entry("material", "材质A"),
                Map.entry("category", "类别A"),
                Map.entry("spec", "规格A"),
                Map.entry("length", "9m"),
                Map.entry("warehouse_name", "一号仓"),
                Map.entry("batch_no", "B-001"),
                Map.entry("in_quantity", 10),
                Map.entry("out_quantity", 0),
                Map.entry("quantity_unit", "件"),
                Map.entry("in_weight_ton", new BigDecimal("5.250")),
                Map.entry("out_weight_ton", BigDecimal.ZERO),
                Map.entry("unit", "吨"),
                Map.entry("remark", "入库备注")
        ));
        IoReportQueryRepository repository = new IoReportQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(0, 10, "sourceNo", "asc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("LOWER(COALESCE(report.source_no, '')) ASC");
        assertThat(jdbcTemplate.dataSql).doesNotContain("ROW_NUMBER()", "paged.");
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(3L);
            assertThat(row.sourceDocumentId()).isEqualTo(30L);
            assertThat(row.materialId()).isEqualTo(300L);
            assertThat(row.warehouseId()).isEqualTo(3000L);
            assertThat(row.businessDate()).isEqualTo(LocalDate.of(2026, 5, 2));
            assertThat(row.businessType()).isEqualTo("采购入库");
            assertThat(row.sourceNo()).isEqualTo("PI-001");
            assertThat(row.materialCode()).isEqualTo("M-001");
            assertThat(row.brand()).isEqualTo("品牌A");
            assertThat(row.material()).isEqualTo("材质A");
            assertThat(row.category()).isEqualTo("类别A");
            assertThat(row.spec()).isEqualTo("规格A");
            assertThat(row.length()).isEqualTo("9m");
            assertThat(row.warehouseName()).isEqualTo("一号仓");
            assertThat(row.batchNo()).isEqualTo("B-001");
            assertThat(row.inQuantity()).isEqualTo(10);
            assertThat(row.outQuantity()).isZero();
            assertThat(row.quantityUnit()).isEqualTo("件");
            assertThat(row.inWeightTon()).isEqualByComparingTo("5.250");
            assertThat(row.outWeightTon()).isEqualByComparingTo("0");
            assertThat(row.unit()).isEqualTo("吨");
            assertThat(row.remark()).isEqualTo("入库备注");
        });
    }

    @Test
    void pageBuildsMaterialCodeAndWarehouseSortExpressionsWhenRowsExist() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.resultSetRows = List.of(row());
        IoReportQueryRepository repository = new IoReportQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "materialCode", "desc"), null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("LOWER(COALESCE(report.material_code, '')) DESC");
        assertThat(jdbcTemplate.dataSql).doesNotContain("paged.material_code");

        repository.page(new PageQuery(0, 10, "warehouseName", "asc"), null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("LOWER(COALESCE(report.warehouse_name, '')) ASC");
        assertThat(jdbcTemplate.dataSql).doesNotContain("paged.warehouse_name");
    }

    private Map<String, Object> row() {
        return Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("source_document_id", 10L),
                Map.entry("material_id", 100L),
                Map.entry("warehouse_id", 1000L),
                Map.entry("business_date", LocalDate.of(2026, 5, 2)),
                Map.entry("business_type", "销售出库"),
                Map.entry("source_no", "SO-001"),
                Map.entry("material_code", "M-002"),
                Map.entry("brand", "品牌B"),
                Map.entry("material", "材质B"),
                Map.entry("category", "类别B"),
                Map.entry("spec", "规格B"),
                Map.entry("length", "12m"),
                Map.entry("warehouse_name", "二号仓"),
                Map.entry("batch_no", "B-002"),
                Map.entry("in_quantity", 0),
                Map.entry("out_quantity", 8),
                Map.entry("quantity_unit", "件"),
                Map.entry("in_weight_ton", BigDecimal.ZERO),
                Map.entry("out_weight_ton", new BigDecimal("4.000")),
                Map.entry("unit", "吨"),
                Map.entry("remark", "出库备注")
        );
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private Long total = 0L;
        private List<Map<String, Object>> resultSetRows = List.of();
        private String dataSql;

        private RecordingNamedParameterJdbcTemplate() {
            super(dataSource());
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
            return requiredType.cast(total);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.dataSql = sql;
            return resultSetRows.stream()
                    .map(row -> map(rowMapper, row))
                    .toList();
        }

        private <T> T map(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            Map<String, Object> values = new HashMap<>(row);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLong" -> ((Number) values.get((String) args[0])).longValue();
                        case "getObject" -> ((Class<?>) args[1]).cast(values.get((String) args[0]));
                        case "getString" -> (String) values.get((String) args[0]);
                        case "getInt" -> ((Number) values.get((String) args[0])).intValue();
                        case "getBigDecimal" -> (BigDecimal) values.get((String) args[0]);
                        case "wasNull" -> false;
                        case "toString" -> "ResultSetStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private static DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class[]{DataSource.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> "DataSourceStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
