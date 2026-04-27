package com.leo.erp.system.database.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/database")
public class DatabaseStatusController {

    private final DatabaseStatusService statusService;

    public DatabaseStatusController(DatabaseStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    @RequiresPermission(resource = "database", action = "read")
    public ApiResponse<DatabaseStatusResponse> status() {
        return ApiResponse.success(statusService.getStatus());
    }
}
