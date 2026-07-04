package com.leo.erp.search.repository;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Test
    void searchReturnsEmptyWithoutQueryWhenModuleAccessesMissing() {
        RecordingNamedParameterJdbcTemplate nullAccessJdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository nullAccessRepository = new GlobalSearchDocumentRepository(nullAccessJdbcTemplate);

        assertThat(nullAccessRepository.search("CG", null, 20, null)).isEmpty();
        assertThat(nullAccessJdbcTemplate.sql).isNull();

        RecordingNamedParameterJdbcTemplate emptyAccessJdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository emptyAccessRepository = new GlobalSearchDocumentRepository(emptyAccessJdbcTemplate);

        assertThat(emptyAccessRepository.search("CG", null, 20, List.of())).isEmpty();
        assertThat(emptyAccessJdbcTemplate.sql).isNull();
    }

    @Test
    void searchSkipsInvalidModuleKeysBeforeQuerying() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

        List<GlobalSearchDocument> results = repository.search(
                "CG",
                null,
                20,
                List.of(
                        new GlobalSearchModuleAccess(null, Set.of(1L)),
                        GlobalSearchModuleAccess.all("  ")
                )
        );

        assertThat(results).isEmpty();
        assertThat(jdbcTemplate.sql).isNull();
    }

    @Test
    void searchUsesEmptyLikePatternsForMissingKeywords() {
        for (String keyword : new String[]{null, "  "}) {
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

            repository.search(
                    keyword,
                    null,
                    5,
                    List.of(GlobalSearchModuleAccess.all("sales-order"))
            );

            assertThat(jdbcTemplate.params.getValue("keyword")).isEqualTo(keyword);
            assertThat(jdbcTemplate.params.getValue("pattern")).isEqualTo("%%");
            assertThat(jdbcTemplate.params.getValue("prefixPattern")).isEqualTo("%");
            assertThat(jdbcTemplate.params.getValue("limit")).isEqualTo(5);
        }
    }

    @Test
    void searchMapsRowsToDocuments() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        Map<String, Object> row = Map.of(
                "module_key", "sales-order",
                "record_id", 42L,
                "primary_no", "SO-42",
                "summary", "Sales order summary",
                "matched_by_track_id", true
        );
        jdbcTemplate.returnRows(List.of(row));
        GlobalSearchDocumentRepository repository = new GlobalSearchDocumentRepository(jdbcTemplate);

        List<GlobalSearchDocument> results = repository.search(
                "SO-42",
                42L,
                1,
                List.of(GlobalSearchModuleAccess.all("sales-order"))
        );

        assertThat(results).containsExactly(new GlobalSearchDocument(
                "sales-order",
                42L,
                "SO-42",
                "Sales order summary",
                true
        ));
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {
        private String sql;
        private MapSqlParameterSource params;
        private List<Map<String, Object>> rows = List.of();

        private RecordingNamedParameterJdbcTemplate() {
            super(dataSource());
        }

        private void returnRows(List<Map<String, Object>> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.params = (MapSqlParameterSource) paramSource;
            List<T> results = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                results.add(map(rowMapper, rows.get(index), index));
            }
            return results;
        }

        private static <T> T map(RowMapper<T> rowMapper, Map<String, Object> row, int rowNum) {
            try {
                return rowMapper.mapRow(resultSet(row), rowNum);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> row.get((String) args[0]);
                        case "getLong" -> ((Number) row.get((String) args[0])).longValue();
                        case "getBoolean" -> row.get((String) args[0]);
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
