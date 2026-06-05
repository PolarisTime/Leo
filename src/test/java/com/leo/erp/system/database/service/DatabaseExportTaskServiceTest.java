package com.leo.erp.system.database.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.domain.entity.DatabaseExportTask;
import com.leo.erp.system.database.mapper.DatabaseExportTaskMapper;
import com.leo.erp.system.database.repository.DatabaseExportTaskRepository;
import com.leo.erp.system.database.web.dto.DatabaseExportTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseExportTaskServiceTest {

    private DatabaseExportTaskRepository taskRepository;
    private DatabaseBackupService databaseBackupService;
    private DatabaseBackupProperties backupProperties;
    private DatabaseExportTaskMapper exportTaskMapper;
    private SnowflakeIdGenerator snowflakeIdGenerator;
    private DatabaseExportTaskService service;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        taskRepository = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createTask(1L, "DBEXP001", "排队中"));
                    case "existsByStatusInAndDeletedFlagFalse" -> false;
                    case "saveAndFlush" -> {
                        var task = (DatabaseExportTask) args[0];
                        task.setId(1L);
                        yield task;
                    }
                    case "save" -> args[0];
                    case "findByStatusInAndDeletedFlagFalse" -> List.of();
                    case "findTop20ByDeletedFlagFalseOrderByCreatedAtDescIdDesc" -> List.of(createTask(1L, "DBEXP001", "已完成"));
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "consumeDownloadToken" -> 1;
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        databaseBackupService = mock(DatabaseBackupService.class);
        when(databaseBackupService.exportBackup()).thenReturn(java.nio.file.Files.createTempFile("test", ".sql"));
        backupProperties = new DatabaseBackupProperties();
        exportTaskMapper = (DatabaseExportTaskMapper) Proxy.newProxyInstance(
                DatabaseExportTaskMapper.class.getClassLoader(),
                new Class[]{DatabaseExportTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new DatabaseExportTaskResponse(1L, "DBEXP001", "排队中", null, null, null, null, null, null, null);
                    case "toString" -> "DatabaseExportTaskMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        snowflakeIdGenerator = new SnowflakeIdGenerator(1);
        service = new DatabaseExportTaskService(
                taskRepository, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );
    }

    @Test
    void shouldCreateTask_whenNoActiveTasks() {
        var result = service.createTask();
        assertThat(result).isNotNull();
        assertThat(result.taskNo()).contains("DBEXP");
    }

    @Test
    void shouldThrowException_whenActiveTasksExist() {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByStatusInAndDeletedFlagFalse" -> true;
                    case "findByStatusInAndDeletedFlagFalse" -> List.of();
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(svc::createTask)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有数据库导出任务在执行");
    }

    @Test
    void shouldListRecentTasks() {
        var result = service.listRecentTasks();
        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldReturnTask_whenGettingExistingTask() {
        var result = service.getTask(1L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldReturnDownloadLink_whenTaskCompleted() {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCompletedTask(1L));
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "save" -> args[0];
                    case "consumeDownloadToken" -> 1;
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        var result = svc.generateDownloadLink(1L);

        assertThat(result).isNotNull();
        assertThat(result.taskId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnDownloadLinkResponseWithContextPath() throws Exception {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCompletedTask(1L));
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "save" -> args[0];
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );
        setContextPath(svc, "/api");

        var result = svc.generateDownloadLinkResponse(1L);

        assertThat(result.downloadUrl())
                .startsWith("/api/system/database/export-task/1/download?token=");
        assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void shouldThrowException_whenDownloadTaskNotCompleted() {
        assertThatThrownBy(() -> service.generateDownloadLink(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成");
    }

    @Test
    void shouldThrowException_whenGeneratingLinkForMissingFile() {
        var task = createTask(1L, "DBEXP001", "已完成");
        task.setFilePath("/tmp/leo-missing-export-file.sql");
        task.setExpiresAt(LocalDateTime.now().plusDays(1));
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.generateDownloadLink(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份文件不存在");
    }

    @Test
    void shouldThrowException_whenTaskNotFound() {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.getTask(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出任务不存在");
    }

    @Test
    void shouldThrowException_whenTaskExpired() {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createExpiredTask(1L));
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "save" -> args[0];
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.generateDownloadLink(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void shouldReturnDownloadPayloadAndConsumeToken() throws Exception {
        var task = createCompletedTask(1L);
        task.setDownloadToken("token");
        task.setFileName("备份 文件.sql");
        task.setFileSize(123L);
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        var payload = svc.getDownloadPayload(1L, "token");

        assertThat(payload.filePath()).isEqualTo(java.nio.file.Path.of(task.getFilePath()));
        assertThat(payload.fileName()).isEqualTo("备份 文件.sql");
        assertThat(payload.fileSize()).isEqualTo(123L);
    }

    @Test
    void shouldThrowException_whenDownloadLinkExpired() throws Exception {
        var task = createCompletedTask(1L);
        task.setDownloadToken("token");
        task.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.getDownloadPayload(1L, "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下载链接已过期");
    }

    @Test
    void shouldThrowException_whenDownloadTokenIsInvalid() throws Exception {
        var task = createCompletedTask(1L);
        task.setDownloadToken("token");
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.getDownloadPayload(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下载令牌无效");
        assertThatThrownBy(() -> svc.getDownloadPayload(1L, "bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下载令牌无效");
    }

    @Test
    void shouldThrowException_whenDownloadTokenConsumeFails() throws Exception {
        var task = createCompletedTask(1L);
        task.setDownloadToken("token");
        var repo = repositoryReturning(task, 0);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.getDownloadPayload(1L, "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下载令牌无效");
    }

    @Test
    void shouldThrowException_whenDownloadPayloadFileMissing() {
        var task = createTask(1L, "DBEXP001", "已完成");
        task.setDownloadToken("token");
        task.setFilePath("/tmp/leo-missing-download-file.sql");
        task.setExpiresAt(LocalDateTime.now().plusDays(1));
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        assertThatThrownBy(() -> svc.getDownloadPayload(1L, "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份文件不存在");
    }

    @Test
    void shouldReturnDownloadResourceWithEncodedFilenameAndDefaultSize() throws Exception {
        var task = createCompletedTask(1L);
        task.setDownloadToken("token");
        task.setFileName("备份 文件.sql");
        task.setFileSize(null);
        var repo = repositoryReturning(task, 1);
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        var resource = svc.getDownloadResource(1L, "token");

        assertThat(resource.resource().exists()).isTrue();
        assertThat(resource.contentType().toString()).isEqualTo("application/octet-stream");
        assertThat(resource.contentLength()).isZero();
        assertThat(resource.contentDisposition()).contains("%E5%A4%87%E4%BB%BD%20%E6%96%87%E4%BB%B6.sql");
    }

    @Test
    void shouldCleanupExpiredTasksOnlyOnce() throws Exception {
        var expiredFile = Files.createTempFile("expired-export", ".sql");
        var expiredTask = createTask(1L, "DBEXP001", "已完成");
        expiredTask.setFilePath(expiredFile.toString());
        expiredTask.setFileSize(12L);
        expiredTask.setDownloadToken("token");
        expiredTask.setExpiresAt(LocalDateTime.now().minusDays(1));
        var alreadyExpiredTask = createTask(2L, "DBEXP002", "已过期");
        alreadyExpiredTask.setFilePath(Files.createTempFile("already-expired-export", ".sql").toString());
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of(expiredTask, alreadyExpiredTask);
                    case "save" -> args[0];
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        svc.cleanupExpiredTasks();

        assertThat(Files.exists(expiredFile)).isFalse();
        assertThat(expiredTask.getStatus()).isEqualTo("已过期");
        assertThat(expiredTask.getDownloadToken()).isNull();
        assertThat(expiredTask.getFilePath()).isNull();
        assertThat(expiredTask.getFileSize()).isNull();
        assertThat(alreadyExpiredTask.getFilePath()).isNotNull();
    }

    @Test
    void shouldReconcileInterruptedTasks() {
        var repo = (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusInAndDeletedFlagFalse" -> List.of(createTask(1L, "DBEXP001", "执行中"));
                    case "save" -> args[0];
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new DatabaseExportTaskService(
                repo, databaseBackupService, backupProperties, exportTaskMapper, snowflakeIdGenerator
        );

        svc.reconcileInterruptedTasks();
        // no exception means success
    }

    @Test
    void shouldReturnIsExpired_whenExpiresAtBeforeNow() {
        var task = createTask(1L, "DBEXP001", "已完成");
        task.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThat(service.isExpired(task)).isTrue();
    }

    @Test
    void shouldReturnNotExpired_whenExpiresAtAfterNow() {
        var task = createTask(1L, "DBEXP001", "已完成");
        task.setExpiresAt(LocalDateTime.now().plusDays(1));
        assertThat(service.isExpired(task)).isFalse();
    }

    @Test
    void shouldReturnNotExpired_whenExpiresAtIsNull() {
        var task = createTask(1L, "DBEXP001", "已完成");
        assertThat(service.isExpired(task)).isFalse();
    }

    private static DatabaseExportTaskRepository repositoryReturning(DatabaseExportTask task, int consumedRows) {
        return (DatabaseExportTaskRepository) Proxy.newProxyInstance(
                DatabaseExportTaskRepository.class.getClassLoader(),
                new Class[]{DatabaseExportTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(task);
                    case "findByStatusAndExpiresAtBeforeAndDeletedFlagFalse" -> List.of();
                    case "save" -> args[0];
                    case "consumeDownloadToken" -> consumedRows;
                    case "toString" -> "DatabaseExportTaskRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static void setContextPath(DatabaseExportTaskService service, String contextPath) throws Exception {
        var field = DatabaseExportTaskService.class.getDeclaredField("contextPath");
        field.setAccessible(true);
        field.set(service, contextPath);
    }

    private static DatabaseExportTask createTask(Long id, String taskNo, String status) {
        var task = new DatabaseExportTask();
        task.setId(id);
        task.setTaskNo(taskNo);
        task.setStatus(status);
        return task;
    }

    private static DatabaseExportTask createCompletedTask(Long id) throws IOException {
        var tempFile = java.nio.file.Files.createTempFile("test", ".sql");
        var task = createTask(id, "DBEXP001", "已完成");
        task.setFilePath(tempFile.toString());
        task.setExpiresAt(LocalDateTime.now().plusDays(1));
        return task;
    }

    private static DatabaseExportTask createExpiredTask(Long id) throws IOException {
        var tempFile = java.nio.file.Files.createTempFile("test", ".sql");
        var task = createTask(id, "DBEXP001", "已完成");
        task.setFilePath(tempFile.toString());
        task.setExpiresAt(LocalDateTime.now().minusDays(1));
        return task;
    }
}
