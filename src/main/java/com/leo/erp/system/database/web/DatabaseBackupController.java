package com.leo.erp.system.database.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.totp.RequiresTotpVerification;
import com.leo.erp.system.database.service.DatabaseBackupService;
import com.leo.erp.system.database.service.DatabaseExportDownloadResource;
import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.database.web.dto.DatabaseExportDownloadLinkResponse;
import com.leo.erp.system.database.web.dto.DatabaseExportTaskResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@Validated
@RequestMapping("/system/databases")
public class DatabaseBackupController {

    private final DatabaseBackupService backupService;
    private final DatabaseExportTaskService exportTaskService;

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
        return ApiResponse.success("导出任务已提交", exportTaskService.createTask());
    }

    @GetMapping("/export-tasks")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<List<DatabaseExportTaskResponse>> listExportTasks() {
        return ApiResponse.success(exportTaskService.listRecentTasks());
    }

    @GetMapping("/export-task/{id}")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<DatabaseExportTaskResponse> getExportTask(@PathVariable @Positive Long id) {
        return ApiResponse.success(exportTaskService.getTask(id));
    }

    @PostMapping("/export-task/{id}/download-link")
    @RequiresPermission(resource = "database", action = "export")
    public ApiResponse<DatabaseExportDownloadLinkResponse> generateDownloadLink(@PathVariable @Positive Long id) {
        return ApiResponse.success("下载链接已生成", exportTaskService.generateDownloadLinkResponse(id));
    }

    @GetMapping("/export-task/{id}/download")
    @PublicAccess
    public ResponseEntity<Resource> downloadExportTask(@PathVariable @Positive Long id,
                                                       @RequestParam @NotBlank(message = "下载令牌不能为空") String token) {
        DatabaseExportDownloadResource payload = exportTaskService.getDownloadResource(id, token);
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .contentLength(payload.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, payload.contentDisposition())
                .body(payload.resource());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(resource = "database", action = "update")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "数据库管理", actionType = "导入备份")
    public ApiResponse<Void> importBackup(@RequestParam("file") MultipartFile file,
                                          @RequestParam("databaseUsername") @NotBlank(message = "数据库用户名不能为空") String databaseUsername,
                                          @RequestParam("databasePassword") @NotBlank(message = "数据库密码不能为空") String databasePassword) throws IOException, InterruptedException {
        backupService.importBackup(file, databaseUsername, databasePassword);
        return ApiResponse.success("导入成功");
    }
}
