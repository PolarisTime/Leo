package com.leo.erp.search.repository;

import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GlobalSearchDocumentRepository {
    private static final RowMapper<GlobalSearchDocument> ROW_MAPPER = (rs, rowNum) -> new GlobalSearchDocument(
            rs.getString("module_key"),
            rs.getLong("record_id"),
            rs.getString("primary_no"),
            rs.getString("summary"),
            rs.getBoolean("matched_by_track_id")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GlobalSearchDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GlobalSearchDocument> search(String keyword,
                                             Long trackId,
                                             int limit,
                                             List<String> moduleKeys) {
        if (moduleKeys == null || moduleKeys.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("trackId", trackId, Types.BIGINT)
                .addValue("pattern", "%" + escapeLike(keyword) + "%")
                .addValue("prefixPattern", escapeLike(keyword) + "%")
                .addValue("moduleKeys", moduleKeys)
                .addValue("limit", limit);
        String matchWhereSql = trackId == null
                ? "(primary_no ILIKE :pattern ESCAPE '!' OR search_text ILIKE :pattern ESCAPE '!')"
                : "record_id = :trackId";

        String sql = """
                SELECT module_key,
                       record_id,
                       primary_no,
                       summary,
                       (record_id = :trackId) AS matched_by_track_id
                FROM global_search_document
                WHERE deleted_flag = FALSE
                  AND module_key IN (:moduleKeys)
                  AND (%s)
                ORDER BY
                    CASE
                        WHEN :trackId IS NOT NULL AND record_id = :trackId THEN 0
                        WHEN lower(primary_no) = lower(:keyword) THEN 1
                        WHEN primary_no ILIKE :prefixPattern ESCAPE '!' THEN 2
                        ELSE 3
                    END,
                    updated_at DESC NULLS LAST,
                    record_id DESC
                LIMIT :limit
                """.formatted(matchWhereSql);

        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    private String escapeLike(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
