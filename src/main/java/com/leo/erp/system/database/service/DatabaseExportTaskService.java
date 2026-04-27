package com.leo.erp.system.database.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.domain.entity.DatabaseExportTask;
import com.leo.erp.system.database.repository.DatabaseExportTaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DatabaseExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseExportTaskService.class);
    private static final String STATUS_PENDING = "排队中";
    private static final String STATUS_RUNNING = "执行中";
    private static final String STATUS_SUCCESS = StatusConstants.COMPLETED;
    private static final String STATUS_FAILED = "失败";
    private static final String STATUS_EXPIRED = "已过期";
    private static final Set<String> ACTIVE_STATUSES = Set.of(STATUS_PENDING, STATUS_RUNNING);
    private static final DateTimeFormatter TASK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private final DatabaseExportTaskRepository taskRepository;
    private final DatabaseBackupService databaseBackupService;
    private final DatabaseBackupProperties backupProperties;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "database-export-task-worker");
        thread.setDaemon(true);
        return thread;
    });

    public DatabaseExportTaskService(DatabaseExportTaskRepository taskRepository,
                                     DatabaseBackupService databaseBackupService,
                                     DatabaseBackupProperties backupProperties,
                                     SnowflakeIdGenerator snowflakeIdGenerator) {
        this.taskRepository = taskRepository;
        this.databaseBackupService = databaseBackupService;
        this.backupProperties = backupProperties;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @PostConstruct
    public void reconcileInterruptedTasks() {
        taskRepository.findByStatusInAndDeletedFlagFalse(List.copyOf(ACTIVE_STATUSES))
                .forEach(task -> {
                    task.setStatus(STATUS_FAILED);
                    task.setFinishedAt(LocalDateTime.now());
                    task.setFailureReason("服务重启导致后台导出任务中断");
                    taskRepository.save(task);
                });
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdownNow();
    }

    public DatabaseExportTask createTask() {
        cleanupExpiredTasks();
        if (taskRepository.existsByStatusInAndDeletedFlagFalse(List.copyOf(ACTIVE_STATUSES))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已有数据库导出任务在执行，请等待完成后再试");
        }

        DatabaseExportTask task = new DatabaseExportTask();
        long id = snowflakeIdGenerator.nextId();
        task.setId(id);
        task.setTaskNo("DBEXP" + LocalDateTime.now().format(TASK_NO_FMT) + (id % 1_000_000));
        task.setStatus(STATUS_PENDING);
        task = taskRepository.saveAndFlush(task);

        Long taskId = task.getId();
        executorService.submit(() -> runTask(taskId));
        return task;
    }

    public List<DatabaseExportTask> listRecentTasks() {
        cleanupExpiredTasks();
        return taskRepository.findTop20ByDeletedFlagFalseOrderByCreatedAtDescIdDesc();
    }

    public DatabaseExportTask getTask(Long id) {
        cleanupExpiredTasks();
        return findTask(id);
    }

    public DownloadLinkPayload generateDownloadLink(Long id) {
        cleanupExpiredTasks();
        DatabaseExportTask task = findTask(id);
        if (!STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前导出任务尚未完成");
        }
        if (isExpired(task)) {
            expireTask(task);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "备份文件已过期，无法生成下载链接");
        }
        Path filePath = Path.of(task.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "备份文件不存在或已被清理");
        }
        task.setDownloadToken(UUID.randomUUID().toString().replace("-", ""));
        taskRepository.save(task);
        return new DownloadLinkPayload(task.getId(), task.getDownloadToken(), task.getExpiresAt());
    }

    @Transactional
    public DownloadPayload getDownloadPayload(Long id, String token) {
        cleanupExpiredTasks();
        DatabaseExportTask task = findTask(id);
        if (!STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前导出任务尚未完成");
        }
        if (task.getExpiresAt() == null || task.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireTask(task);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "下载链接已过期");
        }
        if (token == null || !token.equals(task.getDownloadToken())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "下载令牌无效");
        }
        Path filePath = Path.of(task.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "备份文件不存在或已被清理");
        }
        int affectedRows = taskRepository.consumeDownloadToken(id, token, STATUS_SUCCESS, LocalDateTime.now());
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "下载令牌无效");
        }
        return new DownloadPayload(filePath, task.getFileName(), task.getFileSize());
    }

    public boolean isExpired(DatabaseExportTask task) {
        return task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now());
    }

    public record DownloadPayload(
            Path filePath,
            String fileName,
            Long fileSize
    ) {
    }

    public record DownloadLinkPayload(
            Long taskId,
            String downloadToken,
            LocalDateTime expiresAt
    ) {
    }

    private void runTask(Long taskId) {
        DatabaseExportTask task = findTask(taskId);
        task.setStatus(STATUS_RUNNING);
        taskRepository.save(task);

        Path tempFile = null;
        try {
            Files.createDirectories(Path.of(backupProperties.getStoragePath()));
            tempFile = databaseBackupService.exportBackup(datasourceUsername, datasourcePassword);
            String finalFileName = task.getTaskNo() + ".sql";
            Path targetPath = Path.of(backupProperties.getStoragePath()).resolve(finalFileName);
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            task.setStatus(STATUS_SUCCESS);
            task.setFileName(finalFileName);
            task.setFilePath(targetPath.toAbsolutePath().toString());
            task.setFileSize(Files.size(targetPath));
            task.setDownloadToken(null);
            task.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, backupProperties.getDownloadExpireDays())));
            task.setFinishedAt(LocalDateTime.now());
            task.setFailureReason(null);
            taskRepository.save(task);
            log.info("数据库导出任务完成: taskNo={}, file={}", task.getTaskNo(), targetPath);
        } catch (Exception ex) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException deleteEx) {
                    log.warn("删除临时导出文件失败: {}", tempFile, deleteEx);
                }
            }
            task.setStatus(STATUS_FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setFailureReason(truncate(ex.getMessage(), 500));
            taskRepository.save(task);
            log.error("数据库导出任务失败: taskNo={}", task.getTaskNo(), ex);
        }
    }

    private void cleanupExpiredTasks() {
        taskRepository.findByStatusAndExpiresAtBeforeAndDeletedFlagFalse(STATUS_SUCCESS, LocalDateTime.now())
                .forEach(this::expireTask);
    }

    private void expireTask(DatabaseExportTask task) {
        if (STATUS_EXPIRED.equals(task.getStatus())) {
            return;
        }
        if (task.getFilePath() != null && !task.getFilePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(task.getFilePath()));
            } catch (IOException ex) {
                log.warn("删除过期数据库备份失败: {}", task.getFilePath(), ex);
            }
        }
        task.setStatus(STATUS_EXPIRED);
        task.setDownloadToken(null);
        task.setFilePath(null);
        task.setFileSize(null);
        taskRepository.save(task);
    }

    private DatabaseExportTask findTask(Long id) {
        return taskRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导出任务不存在"));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
