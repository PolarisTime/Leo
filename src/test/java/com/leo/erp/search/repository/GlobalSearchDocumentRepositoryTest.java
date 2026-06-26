package com.leo.erp.search.repository;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchDocumentRepositoryTest {

    @Test
    void searchBuildsModuleAccessClausesWithOwnerFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

        repository.search(
                "CG_100%",
                null,
                20,
                List.of(
                        GlobalSearchModuleAccess.all("sales-order"),
                        new GlobalSearchModuleAccess("purchase-order", Set.of(1L, 2L))
                )
        );

        assertThat(jdbcTemplate.sql).contains("module_key = :moduleKey0");
        assertThat(jdbcTemplate.sql).contains("(module_key = :moduleKey1 AND created_by IN (:ownerUserIds1))");
        assertThat(jdbcTemplate.sql).contains("primary_no ILIKE :pattern ESCAPE '!'");
        assertThat(jdbcTemplate.params.getValue("moduleKey0")).isEqualTo("sales-order");
        assertThat(jdbcTemplate.params.getValue("moduleKey1")).isEqualTo("purchase-order");
        assertThat(jdbcTemplate.params.getValue("ownerUserIds1")).isEqualTo(Set.of(1L, 2L));
        assertThat(jdbcTemplate.params.getValue("pattern")).isEqualTo("%CG!_100!%%");
        assertThat(jdbcTemplate.params.getValue("prefixPattern")).isEqualTo("CG!_100!%%");
    }

    @Test
    void searchUsesRecordIdMatchForTrackId() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

        repository.search(
                "317340436135936000",
                317340436135936000L,
                10,
                List.of(GlobalSearchModuleAccess.all("sales-order"))
        );

        assertThat(jdbcTemplate.sql).contains("record_id = :trackId");
        assertThat(jdbcTemplate.params.getValue("trackId")).isEqualTo(317340436135936000L);
        assertThat(jdbcTemplate.params.getValue("limit")).isEqualTo(10);
    }

    @Test
    void searchSkipsModulesWithEmptyOwnerScope() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

        List<GlobalSearchDocument> results = repository.search(
                "CG",
                null,
                20,
                List.of(new GlobalSearchModuleAccess("purchase-order", Set.of()))
        );

        assertThat(results).isEmpty();
        assertThat(jdbcTemplate.sql).isNull();
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {
        private String sql;
        private MapSqlParameterSource params;

        private RecordingNamedParameterJdbcTemplate() {
            super(dataSource());
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.params = (MapSqlParameterSource) paramSource;
            return List.of();
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
