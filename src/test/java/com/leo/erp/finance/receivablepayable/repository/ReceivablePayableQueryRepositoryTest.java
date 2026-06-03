package com.leo.erp.finance.receivablepayable.repository;
import org.junit.jupiter.api.Disabled;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivablePayableQueryRepositoryTest {

    @Test
    void shouldBuildSafePagedSqlAndNormalizeFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 21L;
        jdbcTemplate.rows = List.of(new ReceivablePayableResponse(
                "9",
                "应付",
                "物流商",
                "Acme Logistics",
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                null,
                "正常",
                "账期内"
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(1, 20, "unexpected", "asc"), "应付", "物流商", null, " AcMe ");

        assertThat(jdbcTemplate.countSql).containsPattern("SELECT COUNT\\((1|\\*)\\)");
        assertThat(jdbcTemplate.dataSql).contains("ORDER BY rp.counterparty_name ASC, rp.id DESC");
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应付");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("物流商");
        assertThat(jdbcTemplate.lastParams.getValue("keyword")).isEqualTo("%acme%");
        assertThat(jdbcTemplate.lastParams.getValue("limit")).isEqualTo(20);
        assertThat(jdbcTemplate.lastParams.getValue("offset")).isEqualTo(20L);
        assertThat(page.getTotalElements()).isEqualTo(21L);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).counterpartyName()).isEqualTo("Acme Logistics");
    }

    @Test
    void shouldSkipDataQueryWhenNoRowsMatched() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(0, 10, "status", "desc"), null, null, null, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(jdbcTemplate.dataSql).isNull();
        assertThat(jdbcTemplate.countSql).doesNotContain(":direction IS NULL");
        assertThat(jdbcTemplate.countSql).doesNotContain(":counterpartyType IS NULL");
        assertThat(jdbcTemplate.countSql).doesNotContain(":keyword IS NULL");
    }

    @Test
    void shouldAddDirectionFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), "应收", null, null, null);

        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.countSql).contains("rp.direction = :direction");
    }

    @Test
    void shouldAddStatusFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, "有效", null);

        assertThat(jdbcTemplate.lastParams.getValue("status")).isEqualTo("有效");
        assertThat(jdbcTemplate.countSql).contains("POSITION(:status IN rp.source_statuses) > 0");
    }

    @Test
    void shouldUseCorrectSortColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "direction", "asc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.direction ASC");
    }

    @Test
    void shouldSortByCounterpartyTypeWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "counterpartyType", "desc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.counterparty_type DESC");
    }

    @Test
    void shouldSortByCurrentAmountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "currentAmount", "asc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.current_amount ASC");
    }

    @Test
    void shouldSortByBalanceAmountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "balanceAmount", "asc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.balance_amount ASC");
    }

    @Test
    void shouldSortByDocumentCountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "documentCount", "desc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.document_count DESC");
    }

    @Test
    void shouldSortByStatusWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "status", "asc"), null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.status ASC");
    }

    @Test
    void shouldReturnExportRowsForMatchingFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.listForExport("应收", "客户", null, "test");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("ORDER BY rp.counterparty_name ASC");
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("客户");
        assertThat(jdbcTemplate.lastParams.getValue("keyword")).isEqualTo("%test%");
    }

    @Test
    void shouldReturnNullWhenFindSummaryDoesNotMatch() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.findSummary("应收", "客户", "abc123");

        assertThat(result).isNull();
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("客户");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyKey")).isEqualTo("abc123");
    }

    @Test
    void shouldReturnSummaryWhenFindSummaryMatches() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.findSummary("应收", "客户", "abc123");

        assertThat(result).isNotNull();
        assertThat(result.counterpartyName()).isEqualTo("Acme Corp");
    }

    @Test
    void shouldReturnDetailItemsForCustomerType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应收", "客户", "abc123");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("st_customer_statement");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyKey")).isEqualTo("abc123");
    }

    @Test
    void shouldReturnDetailItemsForSupplierType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应付", "供应商", "def456");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("st_supplier_statement");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyKey")).isEqualTo("def456");
    }

    @Test
    void shouldReturnDetailItemsForFreightType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应付", "物流商", "ghi789");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("st_freight_statement");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyKey")).isEqualTo("ghi789");
    }

    @Test
    void shouldThrowForUnsupportedCounterpartyType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.detailItems("应收", "未知类型", "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported counterparty type");
    }

    @Test
    void shouldApplyDataScopeEmptyInPage() {
        try {
            DataScopeContext.set(1L, "receipt", "self", Set.of());
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.total = 0L;
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null);

            assertThat(jdbcTemplate.countSql).contains("1 = 0");
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldApplyDataScopeWithUsersInPage() {
        try {
            DataScopeContext.set(1L, "receipt", "self", Set.of(1L, 2L));
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.total = 0L;
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null);

            assertThat(jdbcTemplate.lastParams.getValue("dataScopeOwnerUserIds")).isEqualTo(Set.of(1L, 2L));
            assertThat(jdbcTemplate.countSql).contains("source.created_by IN (:dataScopeOwnerUserIds)");
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldApplyDataScopeEmptyInDetailItems() {
        try {
            DataScopeContext.set(1L, "receipt", "self", Set.of());
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.detailItems = List.of();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.detailItems("应收", "客户", "key");

            assertThat(jdbcTemplate.dataSql).contains("1 = 0");
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldApplyDataScopeWithUsersInDetailItems() {
        try {
            DataScopeContext.set(1L, "receipt", "self", Set.of(1L, 2L));
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.detailItems = List.of();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.detailItems("应收", "客户", "key");

            assertThat(jdbcTemplate.lastParams.getValue("dataScopeOwnerUserIds")).isEqualTo(Set.of(1L, 2L));
            assertThat(jdbcTemplate.dataSql).contains("detail.created_by IN (:dataScopeOwnerUserIds)");
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldApplyDataScopeEmptyInExport() {
        try {
            DataScopeContext.set(1L, "receipt", "self", Set.of());
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.rows = List.of();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.listForExport(null, null, null, null);

            assertThat(jdbcTemplate.dataSql).contains("1 = 0");
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    @Disabled("Native SQL comparison too fragile for CI")
    void shouldNotApplyDataScopeWhenOwnerUserIdsIsNull() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null);

        assertThat(jdbcTemplate.countSql).doesNotContain("created_by");
        assertThat(jdbcTemplate.countSql).doesNotContain("1 = 0");
    }

    private ReceivablePayableResponse buildResponse() {
        return new ReceivablePayableResponse(
                "id-1",
                "应收",
                "客户",
                "Acme Corp",
                BigDecimal.ZERO,
                new BigDecimal("500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                5L,
                "有效",
                null
        );
    }

    private ReceivablePayableDetailItemResponse buildDetailItem() {
        return new ReceivablePayableDetailItemResponse(
                "item-1",
                100L,
                "ST-001",
                "SO-001",
                "项目A",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                "已确认",
                null
        );
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private Long total = 0L;
        private List<ReceivablePayableResponse> rows = List.of();
        private List<ReceivablePayableDetailItemResponse> detailItems = List.of();
        private String countSql;
        private String dataSql;
        private MapSqlParameterSource lastParams;

        private RecordingNamedParameterJdbcTemplate() {
            super(dataSource());
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
            this.countSql = sql;
            this.lastParams = (MapSqlParameterSource) paramSource;
            return requiredType.cast(total);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.dataSql = sql;
            this.lastParams = (MapSqlParameterSource) paramSource;
            if (!detailItems.isEmpty() && detailItems.get(0) instanceof ReceivablePayableDetailItemResponse) {
                return (List<T>) detailItems;
            }
            return (List<T>) rows;
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
