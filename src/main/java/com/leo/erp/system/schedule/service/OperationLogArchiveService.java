package com.leo.erp.system.schedule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
public class OperationLogArchiveService {

    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] CSV_HEADERS = {
            "id",
            "logNo",
            "operatorId",
            "operatorName",
            "loginName",
            "moduleName",
            "moduleKey",
            "actionType",
            "businessNo",
            "recordId",
            "requestMethod",
            "requestPath",
            "clientIp",
            "resultStatus",
            "operationTime",
            "authType",
            "remark"
    };

    private final JdbcTemplate jdbcTemplate;

    public OperationLogArchiveService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ArchiveResult archiveBefore(LocalDateTime cutoff, Path archiveDir, int batchSize) throws IOException {
        Files.createDirectories(archiveDir);
        int normalizedBatchSize = Math.max(100, batchSize);
        String timestamp = LocalDateTime.now().format(FILE_TIME_FMT);
        Path tempFile = archiveDir.resolve("operation-log-before-" + cutoff.format(FILE_TIME_FMT) + "-" + timestamp + ".csv.gz.tmp");
        Path archiveFile = archiveDir.resolve("operation-log-before-" + cutoff.format(FILE_TIME_FMT) + "-" + timestamp + ".csv.gz");

        long archivedRows = 0;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(tempFile)),
                StandardCharsets.UTF_8))) {
            writeCsvLine(writer, List.of(CSV_HEADERS));
            Cursor cursor = Cursor.start();
            while (true) {
                List<ArchivedOperationLogRow> rows = fetchBatch(cutoff, cursor, normalizedBatchSize);
                if (rows.isEmpty()) {
                    break;
                }
                for (ArchivedOperationLogRow row : rows) {
                    writeCsvLine(writer, row.toCsvValues());
                    cursor = new Cursor(row.operationTime(), row.id());
                    archivedRows++;
                }
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }

        if (archivedRows == 0) {
            Files.deleteIfExists(tempFile);
            return new ArchiveResult(null, 0, 0, cutoff);
        }

        moveArchiveFile(tempFile, archiveFile);
        int deletedRows = jdbcTemplate.update(
                "DELETE FROM sys_operation_log WHERE operation_time < ?",
                Timestamp.valueOf(cutoff)
        );
        log.info("操作日志归档完成: file={}, archivedRows={}, deletedRows={}, cutoff={}",
                archiveFile, archivedRows, deletedRows, cutoff);
        return new ArchiveResult(archiveFile, archivedRows, deletedRows, cutoff);
    }

    private List<ArchivedOperationLogRow> fetchBatch(LocalDateTime cutoff, Cursor cursor, int batchSize) {
        if (cursor.operationTime() == null) {
            String sql = """
                    SELECT id, log_no, operator_id, operator_name, login_name, module_name,
                           module_key, action_type, business_no, record_id, request_method,
                           request_path, client_ip, result_status, operation_time, auth_type, remark
                    FROM sys_operation_log
                    WHERE operation_time < ?
                    ORDER BY operation_time ASC, id ASC
                    LIMIT ?
                    """;
            return jdbcTemplate.query(
                    sql,
                    ps -> {
                        ps.setTimestamp(1, Timestamp.valueOf(cutoff));
                        ps.setInt(2, batchSize);
                    },
                    (rs, rowNum) -> mapRow(rs)
            );
        }

        String sql = """
                SELECT id, log_no, operator_id, operator_name, login_name, module_name,
                       module_key, action_type, business_no, record_id, request_method,
                       request_path, client_ip, result_status, operation_time, auth_type, remark
                FROM sys_operation_log
                WHERE operation_time < ?
                  AND (operation_time > ? OR (operation_time = ? AND id > ?))
                ORDER BY operation_time ASC, id ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setTimestamp(1, Timestamp.valueOf(cutoff));
                    Timestamp operationTime = Timestamp.valueOf(cursor.operationTime());
                    ps.setTimestamp(2, operationTime);
                    ps.setTimestamp(3, operationTime);
                    ps.setLong(4, cursor.id());
                    ps.setInt(5, batchSize);
                },
                (rs, rowNum) -> mapRow(rs)
        );
    }

    private ArchivedOperationLogRow mapRow(ResultSet rs) throws SQLException {
        return new ArchivedOperationLogRow(
                rs.getLong("id"),
                rs.getString("log_no"),
                nullableLong(rs, "operator_id"),
                rs.getString("operator_name"),
                rs.getString("login_name"),
                rs.getString("module_name"),
                rs.getString("module_key"),
                rs.getString("action_type"),
                rs.getString("business_no"),
                nullableLong(rs, "record_id"),
                rs.getString("request_method"),
                rs.getString("request_path"),
                rs.getString("client_ip"),
                rs.getString("result_status"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("auth_type"),
                rs.getString("remark")
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void moveArchiveFile(Path tempFile, Path archiveFile) throws IOException {
        try {
            Files.move(tempFile, archiveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, archiveFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeCsvLine(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(csvValue(values.get(i)));
        }
        writer.newLine();
    }

    static String csvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record Cursor(LocalDateTime operationTime, long id) {
        static Cursor start() {
            return new Cursor(null, 0L);
        }
    }

    private record ArchivedOperationLogRow(
            long id,
            String logNo,
            Long operatorId,
            String operatorName,
            String loginName,
            String moduleName,
            String moduleKey,
            String actionType,
            String businessNo,
            Long recordId,
            String requestMethod,
            String requestPath,
            String clientIp,
            String resultStatus,
            LocalDateTime operationTime,
            String authType,
            String remark
    ) {
        List<String> toCsvValues() {
            List<String> values = new ArrayList<>(CSV_HEADERS.length);
            values.add(String.valueOf(id));
            values.add(logNo);
            values.add(operatorId == null ? null : String.valueOf(operatorId));
            values.add(operatorName);
            values.add(loginName);
            values.add(moduleName);
            values.add(moduleKey);
            values.add(actionType);
            values.add(businessNo);
            values.add(recordId == null ? null : String.valueOf(recordId));
            values.add(requestMethod);
            values.add(requestPath);
            values.add(clientIp);
            values.add(resultStatus);
            values.add(operationTime == null ? null : operationTime.toString());
            values.add(authType);
            values.add(remark);
            return values;
        }
    }

    public record ArchiveResult(Path file, long archivedRows, int deletedRows, LocalDateTime cutoff) {
    }
}
