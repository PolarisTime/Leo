package com.leo.erp.system.operationlog.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.system.operationlog.service.OperationLogService;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @PreAuthorize("@rbac.check('operation-log', 'read')")
    public ApiResponse<PageResponse<OperationLogResponse>> page(
            @BindPageQuery(sortFieldKey = "operation-log") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) String authType
    ) {
        PageFilter filter = new PageFilter(keyword, null, startTime, endTime,
                null, null, null, moduleName, actionType, resultStatus, null, recordId, null, authType);
        return ApiResponse.success(PageResponse.from(operationLogService.page(query, filter)));
    }
}
