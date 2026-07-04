package com.leo.erp.system.schedule.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        verify(jdbcTemplate, never()).update(anyString(), any(Timestamp.class));
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
        List<String> lines = readGzipLines(result.file());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).startsWith("id,logNo,operatorId");
        assertThat(lines.get(1)).contains("LOG001", "张三", "BIZ001");
        assertThat(lines.get(2)).contains("LOG002", "李四", "BIZ002");
        verify(jdbcTemplate).update(eq("DELETE FROM sys_operation_log WHERE operation_time < ?"), any(Timestamp.class));
    }

    @Test
    void shouldNormalizeBatchSize_whenTooSmall() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<Integer> limits = new ArrayList<>();
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps);
                    var intCaptor = org.mockito.ArgumentCaptor.forClass(Integer.class);
                    verify(ps).setInt(eq(2), intCaptor.capture());
                    limits.add(intCaptor.getValue());
                    return List.of();
                });

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        service.archiveBefore(LocalDateTime.of(2026, 5, 1, 0, 0), tempDir, 10);

        assertThat(limits).containsExactly(100);
    }

    @Test
    void shouldDeleteTempFileOnRuntimeException() throws Exception {
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
                })
                .thenThrow(new RuntimeException("archive query failed"));

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        Path nonExistentDir = tempDir.resolve("nonexistent").resolve("deep").resolve("path");

        assertThatThrownBy(() -> service.archiveBefore(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                nonExistentDir,
                1000
        )).isInstanceOf(RuntimeException.class);
        assertThat(nonExistentDir).isEmptyDirectory();
    }

    @Test
    void shouldPropagateIOExceptionWhenArchivePathIsFile() throws Exception {
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
                })
                .thenReturn(List.of());

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);
        Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
        Files.writeString(fileInsteadOfDirectory, "content");

        assertThatThrownBy(() -> service.archiveBefore(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                fileInsteadOfDirectory,
                1000
        )).isInstanceOf(IOException.class);
        try (Stream<Path> paths = Files.list(tempDir)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("not-a-directory");
        }
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
        List<Integer> parameterCounts = new ArrayList<>();

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
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps);
                    verify(ps).setTimestamp(eq(1), eq(Timestamp.valueOf(cutoff)));
                    verify(ps).setInt(eq(2), eq(100));
                    parameterCounts.add(2);
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch1.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps);
                    verify(ps).setTimestamp(eq(1), eq(Timestamp.valueOf(cutoff)));
                    verify(ps).setTimestamp(eq(2), eq(opTime1));
                    verify(ps).setTimestamp(eq(3), eq(opTime1));
                    verify(ps).setLong(eq(4), eq(1L));
                    verify(ps).setInt(eq(5), eq(100));
                    parameterCounts.add(5);
                    RowMapper<?> mapper = invocation.getArgument(2);
                    return batch2.stream().map(rs -> {
                        try { return mapper.mapRow(rs, 0); } catch (SQLException e) { throw new RuntimeException(e); }
                    }).toList();
                })
                .thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps);
                    verify(ps).setTimestamp(eq(1), eq(Timestamp.valueOf(cutoff)));
                    verify(ps).setTimestamp(eq(2), eq(opTime2));
                    verify(ps).setTimestamp(eq(3), eq(opTime2));
                    verify(ps).setLong(eq(4), eq(2L));
                    verify(ps).setInt(eq(5), eq(100));
                    parameterCounts.add(5);
                    return List.of();
                });
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(2);

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        OperationLogArchiveService.ArchiveResult result = service.archiveBefore(cutoff, tempDir, 100);

        assertThat(result.archivedRows()).isEqualTo(2);
        assertThat(result.deletedRows()).isEqualTo(2);
        assertThat(parameterCounts).containsExactly(2, 5, 5);
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
        assertThat(readGzipLines(result.file()).get(1)).contains("LOG001,,系统", ",BIZ001,,");
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

    @Test
    void shouldPropagateDeleteFailureAfterArchiveMove() throws Exception {
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
        doThrow(new RuntimeException("delete failed")).when(jdbcTemplate).update(anyString(), any(Timestamp.class));

        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);

        assertThatThrownBy(() -> service.archiveBefore(cutoff, tempDir, 1000))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("delete failed");
        try (Stream<Path> paths = Files.list(tempDir)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".csv.gz")).toList()).hasSize(1);
        }
    }

    @Test
    void shouldFallbackToNonAtomicMoveWhenArchiveTargetProviderDoesNotSupportAtomicMove() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OperationLogArchiveService service = new OperationLogArchiveService(jdbcTemplate);
        Path source = Files.createTempFile(tempDir, "operation-log", ".tmp");
        Files.writeString(source, "csv-content");
        Path zipPath = tempDir.resolve("archive-target.zip");
        URI zipUri = URI.create("jar:" + zipPath.toUri());

        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
            Path target = zipFs.getPath("/operation-log.csv.gz");
            var method = OperationLogArchiveService.class.getDeclaredMethod("moveArchiveFile", Path.class, Path.class);
            method.setAccessible(true);

            method.invoke(service, source, target);

            assertThat(Files.exists(source)).isFalse();
            assertThat(Files.readString(target)).isEqualTo("csv-content");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void archivedRowCsvValuesShouldKeepBlankOperationTimeWhenNull() throws Exception {
        Class<?> rowClass = Class.forName(
                "com.leo.erp.system.schedule.service.OperationLogArchiveService$ArchivedOperationLogRow"
        );
        var constructor = rowClass.getDeclaredConstructor(
                long.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);
        Object row = constructor.newInstance(
                1L,
                "LOG001",
                null,
                "系统",
                "system",
                "系统管理",
                "system",
                "创建",
                "BIZ001",
                null,
                "POST",
                "/api/test",
                "127.0.0.1",
                "成功",
                null,
                "JWT",
                null
        );
        var toCsvValues = rowClass.getDeclaredMethod("toCsvValues");
        toCsvValues.setAccessible(true);

        List<String> values = (List<String>) toCsvValues.invoke(row);

        assertThat(values).hasSize(17);
        assertThat(values.get(14)).isNull();
    }

    private List<String> readGzipLines(Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
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
        when(rs.wasNull()).thenReturn(operatorId == null, recordId == null);
        return rs;
    }
}
