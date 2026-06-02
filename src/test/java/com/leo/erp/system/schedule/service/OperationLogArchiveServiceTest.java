package com.leo.erp.system.schedule.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationLogArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCreateArchiveWhenNoExpiredLogs() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                tempDir,
                1000
        );

        assertThat(result.file()).isNull();
        assertThat(result.archivedRows()).isZero();
        assertThat(result.deletedRows()).isZero();
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void shouldEscapeCsvValues() {
        assertThat(OperationLogArchiveService.csvValue("plain")).isEqualTo("plain");
        assertThat(OperationLogArchiveService.csvValue("a,b")).isEqualTo("\"a,b\"");
        assertThat(OperationLogArchiveService.csvValue("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(OperationLogArchiveService.csvValue("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(OperationLogArchiveService.csvValue(null)).isEmpty();
    }

}
