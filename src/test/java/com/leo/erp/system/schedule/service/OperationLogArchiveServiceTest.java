package com.leo.erp.system.schedule.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCreateArchiveWhenNoExpiredLogs() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
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
    void shouldCreateArchiveAndDeleteExpiredLogs() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 1, 0, 0);
        Timestamp opTime = Timestamp.valueOf("2026-04-15 10:30:00");

        List<ResultSet> batch1 = List.of(
                createMockRow(1L, "LOG001", 100L, "张三", "zhangsan",
                        "系统管理", "system", "创建", "BIZ001", 200L,
                        "POST", "/api/test", "127.0.0.1", "成功", opTime, "JWT", "备注1"),
                createMockRow(2L, "LOG002", 101L, "李四", "lisi",
                        "财务管理", "finance", "修改", "BIZ002", 201L,
                        "PUT", "/api/finance", "192.168.1.1", "成功", opTime, "SESSION", "备注2")
        );

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch1.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(2);

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(cutoff, tempDir, 1000);

        assertThat(result.archivedRows()).isEqualTo(2);
        assertThat(result.deletedRows()).isEqualTo(2);
        assertThat(result.file()).isNotNull();
        assertThat(Files.exists(result.file())).isTrue();
        assertThat(result.file().toString()).endsWith(".csv.gz");
        assertThat(result.cutoff()).isEqualTo(cutoff);
        verify(jdbcTemplate).update(eq("DELETE FROM sys_operation_log WHERE operation_time < ?"), any(Timestamp.class));
    }

    @Test
    void shouldNormalizeBatchSize_whenTooSmall() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of());

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        service.archiveBefore(LocalDateTime.of(2026, 5, 1, 0, 0), tempDir, 10);

        verify(jdbcTemplate).query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class));
    }

    @Test
    void shouldDeleteTempFileOnIOException() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        Timestamp opTime = Timestamp.valueOf("2026-04-15 10:30:00");
        List<ResultSet> batch = List.of(
                createMockRow(1L, "LOG001", 100L, "张三", "zhangsan",
                        "系统管理", "system", "创建", "BIZ001", 200L,
                        "POST", "/api/test", "127.0.0.1", "成功", opTime, "JWT", "备注1")
        );

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                });

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        Path nonExistentDir = tempDir.resolve("nonexistent").resolve("deep").resolve("path");

        assertThatThrownBy(() -> service.archiveBefore(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                nonExistentDir,
                1000
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldEscapeCsvValues() {
        assertThat(OperationLogArchiveService.csvValue("plain")).isEqualTo("plain");
        assertThat(OperationLogArchiveService.csvValue("a,b")).isEqualTo("\"a,b\"");
        assertThat(OperationLogArchiveService.csvValue("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(OperationLogArchiveService.csvValue("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(OperationLogArchiveService.csvValue("a\rb")).isEqualTo("\"a\rb\"");
        assertThat(OperationLogArchiveService.csvValue(null)).isEmpty();
        assertThat(OperationLogArchiveService.csvValue("no special")).isEqualTo("no special");
    }

    @Test
    void shouldReturnCorrectCsvHeaders() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 1, 0, 0);
        Timestamp opTime = Timestamp.valueOf("2026-04-15 10:30:00");

        List<ResultSet> batch = List.of(
                createMockRow(1L, "LOG001", 100L, "张三", "zhangsan",
                        "系统管理", "system", "创建", "BIZ001", 200L,
                        "POST", "/api/test", "127.0.0.1", "成功", opTime, "JWT", "备注1")
        );

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(1);

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(cutoff, tempDir, 1000);

        assertThat(result.archivedRows()).isEqualTo(1);
        assertThat(result.deletedRows()).isEqualTo(1);
    }

    @Test
    void shouldHandleMultipleBatches() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 1, 0, 0);
        Timestamp opTime1 = Timestamp.valueOf("2026-04-15 10:30:00");
        Timestamp opTime2 = Timestamp.valueOf("2026-04-16 10:30:00");

        List<ResultSet> batch1 = List.of(
                createMockRow(1L, "LOG001", 100L, "张三", "zhangsan",
                        "系统管理", "system", "创建", "BIZ001", 200L,
                        "POST", "/api/test", "127.0.0.1", "成功", opTime1, "JWT", "备注1")
        );
        List<ResultSet> batch2 = List.of(
                createMockRow(2L, "LOG002", 101L, "李四", "lisi",
                        "财务管理", "finance", "修改", "BIZ002", 201L,
                        "PUT", "/api/finance", "192.168.1.1", "成功", opTime2, "SESSION", "备注2")
        );

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch1.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch2.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(2);

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(cutoff, tempDir, 100);

        assertThat(result.archivedRows()).isEqualTo(2);
        assertThat(result.deletedRows()).isEqualTo(2);
    }

    @Test
    void shouldHandleNullOperatorIdAndRecordId() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 1, 0, 0);
        Timestamp opTime = Timestamp.valueOf("2026-04-15 10:30:00");

        List<ResultSet> batch = List.of(
                createMockRow(1L, "LOG001", null, "系统", "system",
                        "系统管理", "system", "创建", "BIZ001", null,
                        "POST", "/api/test", "127.0.0.1", "成功", opTime, "JWT", null)
        );

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(1);

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(cutoff, tempDir, 1000);

        assertThat(result.archivedRows()).isEqualTo(1);
    }

    @Test
    void shouldCreateArchiveDirectoryIfNotExists() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of());

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        Path newDir = tempDir.resolve("new-archive-dir");
        service.archiveBefore(LocalDateTime.of(2026, 5, 1, 0, 0), newDir, 1000);

        assertThat(Files.exists(newDir)).isTrue();
    }

    private ResultSet createMockRow(long id, String logNo, Long operatorId, String operatorName,
                                  String loginName, String moduleName, String moduleKey,
                                  String actionType, String businessNo, Long recordId,
                                  String requestMethod, String requestPath, String clientIp,
                                  String resultStatus, Timestamp operationTime, String authType,
                                  String remark) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("log_no")).thenReturn(logNo);
        when(rs.getLong("operator_id")).thenReturn(operatorId != null ? operatorId : 0L);
        when(rs.getString("operator_name")).thenReturn(operatorName);
        when(rs.getString("login_name")).thenReturn(loginName);
        when(rs.getString("module_name")).thenReturn(moduleName);
        when(rs.getString("module_key")).thenReturn(moduleKey);
        when(rs.getString("action_type")).thenReturn(actionType);
        when(rs.getString("business_no")).thenReturn(businessNo);
        when(rs.getLong("record_id")).thenReturn(recordId != null ? recordId : 0L);
        when(rs.getString("request_method")).thenReturn(requestMethod);
        when(rs.getString("request_path")).thenReturn(requestPath);
        when(rs.getString("client_ip")).thenReturn(clientIp);
        when(rs.getString("result_status")).thenReturn(resultStatus);
        when(rs.getTimestamp("operation_time")).thenReturn(operationTime);
        when(rs.getString("auth_type")).thenReturn(authType);
        when(rs.getString("remark")).thenReturn(remark);
        when(rs.wasNull()).thenReturn(operatorId == null || recordId == null);
        return rs;
    }
}
