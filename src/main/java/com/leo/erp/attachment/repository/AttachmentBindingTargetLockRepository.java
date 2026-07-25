package com.leo.erp.attachment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AttachmentBindingTargetLockRepository {

    private static final String TARGET_LOCK_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(?, ?))
            """;

    private final JdbcTemplate jdbcTemplate;

    public AttachmentBindingTargetLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String moduleKey, long recordId) {
        jdbcTemplate.query(
                TARGET_LOCK_SQL,
                statement -> {
                    statement.setString(1, moduleKey);
                    statement.setLong(2, recordId);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Failed to acquire attachment binding target lock");
                    }
                    return null;
                }
        );
    }
}
