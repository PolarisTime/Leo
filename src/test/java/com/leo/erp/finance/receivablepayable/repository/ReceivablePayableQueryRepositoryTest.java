package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivablePayableQueryRepositoryTest {

    @Test
    void shouldBuildSafePagedSqlAndNormalizeFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 21L;
        jdbcTemplate.rows = List.of(new ReceivablePayableResponse(
                9L,
                "应付",
                "物流商",
                "Acme Logistics",
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "正常",
                "账期内"
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(1, 20, "unexpected", "asc"), "应付", "物流商", null, " AcMe ");

        assertThat(jdbcTemplate.countSql).contains("SELECT COUNT(*)");
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

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private Long total = 0L;
        private List<ReceivablePayableResponse> rows = List.of();
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
