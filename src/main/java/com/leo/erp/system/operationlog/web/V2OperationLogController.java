package com.leo.erp.system.operationlog.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.service.OperationLogFilter;
import com.leo.erp.system.operationlog.service.OperationLogService;
import com.leo.erp.system.operationlog.service.OperationLogSummaryFilter;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import com.leo.erp.system.operationlog.web.dto.OperationLogSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/operation-logs")
public class V2OperationLogController {

    private final OperationLogService operationLogService;

    public V2OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(
            summary = "分页查询操作日志",
            description = "既有参数保持兼容; 新增 operatorName/businessNo/category/minDurationMs/statusCode; "
                    + "keyword 仍跨 logNo/operatorName/loginName/businessNo/requestPath/remark"
    )
    @GetMapping
    @RequirePermission(PermissionCodes.OPERATION_LOGS_READ)
    public PageResponse<OperationLogResponse> page(
            @BindPageQuery(sortFieldKey = "operation-log") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minDurationMs,
            @RequestParam(required = false) Integer statusCode) {
        OperationLogFilter filter = new OperationLogFilter(
                keyword, moduleName, actionType, resultStatus, authType, operatorName, businessNo,
                category, minDurationMs, statusCode, recordId, startTime, endTime);
        return PageResponse.from(operationLogService.page(query, filter));
    }

    @Operation(
            summary = "操作日志概览统计",
            description = "返回总量/成功/失败/平均耗时、按天趋势与失败模块 Top5; "
                    + "参数复用分页筛选的子集, 默认最近 7 天, 最多 31 天"
    )
    @GetMapping("/summary")
    @RequirePermission(PermissionCodes.OPERATION_LOGS_READ)
    public OperationLogSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String operatorName) {
        return operationLogService.summary(new OperationLogSummaryFilter(
                startTime, endTime, moduleName, actionType, resultStatus, category, operatorName));
    }
}
