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
    void shouldThrowException_whenDownloadTaskNotCompleted() {
        assertThatThrownBy(() -> service.generateDownloadLink(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成");
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
