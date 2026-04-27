package com.leo.erp.system.database.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.totp.RequiresTotpVerification;
import com.leo.erp.system.database.service.DatabaseBackupService;
import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.database.web.dto.DatabaseExportDownloadLinkResponse;
import com.leo.erp.system.database.web.dto.DatabaseExportTaskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/system/database")
public class DatabaseBackupController {

    private final DatabaseBackupService backupService;
    private final DatabaseExportTaskService exportTaskService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public DatabaseBackupController(DatabaseBackupService backupService,
                                    DatabaseExportTaskService exportTaskService) {
        this.backupService = backupService;
        this.exportTaskService = exportTaskService;
    }

    @PostMapping("/export-tasks")
    @RequiresPermission(resource = "database", action = "export")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "数据库管理", actionType = "导出备份")
    public ApiResponse<DatabaseExportTaskResponse> createExportTask() {
        return ApiResponse.success("导出任务已提交", toResponse(exportTaskService.createTask()));
    }

    @GetMapping("/export-tasks")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<List<DatabaseExportTaskResponse>> listExportTasks() {
        return ApiResponse.success(exportTaskService.listRecentTasks().stream().map(this::toResponse).toList());
    }

    @GetMapping("/export-tasks/{id}")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<DatabaseExportTaskResponse> getExportTask(@PathVariable Long id) {
        return ApiResponse.success(toResponse(exportTaskService.getTask(id)));
    }

    @PostMapping("/export-tasks/{id}/download-link")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<DatabaseExportDownloadLinkResponse> generateDownloadLink(@PathVariable Long id) {
        DatabaseExportTaskService.DownloadLinkPayload payload = exportTaskService.generateDownloadLink(id);
        String contextPathValue = contextPath == null ? "" : contextPath;
        String downloadUrl = contextPathValue + "/system/database/export-tasks/" + payload.taskId() + "/download?token=" + payload.downloadToken();
        return ApiResponse.success("下载链接已生成", new DatabaseExportDownloadLinkResponse(downloadUrl, payload.expiresAt()));
    }

    @GetMapping("/export-tasks/{id}/download")
    @PublicAccess
    public ResponseEntity<Resource> downloadExportTask(@PathVariable Long id,
                                                       @RequestParam String token) {
        DatabaseExportTaskService.DownloadPayload payload = exportTaskService.getDownloadPayload(id, token);
        FileSystemResource resource = new FileSystemResource(payload.filePath());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String encodedFileName = URLEncoder.encode(payload.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(payload.fileSize() == null ? 0L : payload.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(resource = "database", action = "update")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "数据库管理", actionType = "导入备份")
    public ApiResponse<Void> importBackup(@RequestParam("file") MultipartFile file,
                                          @RequestParam("databaseUsername") String databaseUsername,
                                          @RequestParam("databasePassword") String databasePassword) throws IOException, InterruptedException {
        if (file.isEmpty()) {
            return ApiResponse.failure(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        backupService.importBackup(file, databaseUsername, databasePassword);
        return ApiResponse.success("导入成功", null);
    }

    private DatabaseExportTaskResponse toResponse(com.leo.erp.system.database.domain.entity.DatabaseExportTask task) {
        return new DatabaseExportTaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getStatus(),
                task.getFileName(),
                task.getFileSize(),
                task.getFailureReason(),
                task.getCreatedAt(),
                task.getFinishedAt(),
                task.getExpiresAt(),
                null
        );
    }
}
