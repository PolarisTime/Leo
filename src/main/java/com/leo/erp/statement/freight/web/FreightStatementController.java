package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "物流对账单")
@RestController
@Validated
@RequestMapping("/freight-statements")
public class FreightStatementController {

    private final FreightStatementService freightStatementService;

    public FreightStatementController(FreightStatementService freightStatementService) {
        this.freightStatementService = freightStatementService;
    }

    @Operation(summary = "搜索物流对账单")
    @GetMapping("/search")
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<java.util.List<FreightStatementResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(freightStatementService.responseSearch(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询物流对账单")
    @GetMapping
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<PageResponse<FreightStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "freight-statement") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String carrierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String signStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        return ApiResponse.success(PageResponse.from(
                freightStatementService.responsePage(query, new PageFilter(keyword, status, periodStart, periodEnd, carrierName, null, null, null, null, null, signStatus, null, null, null, null))
        ));
    }

    @Operation(summary = "分页查询物流对账单候选物流单")
    @GetMapping("/candidate")
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<PageResponse<FreightStatementCandidateResponse>> candidates(
            @BindPageQuery(sortFieldKey = "freight-bill") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(
                freightStatementService.candidatePage(query, keyword)
        ));
    }

    @Operation(summary = "查询物流对账单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<FreightStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(freightStatementService.responseDetail(id));
    }

    @Operation(summary = "创建物流对账单")
    @PostMapping
    @RequiresPermission(resource = "freight-statement", action = "create")
    @OperationLoggable(moduleName = "物流对账单", actionType = "新增", businessNoFields = {"statementNo"})
    public ApiResponse<FreightStatementResponse> create(@Valid @RequestBody FreightStatementRequest request) {
        return ApiResponse.success(
                "创建成功",
                freightStatementService.responseCreate(request)
        );
    }

    @Operation(summary = "更新物流对账单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "update")
    @OperationLoggable(moduleName = "物流对账单", actionType = "编辑", businessNoFields = {"statementNo"})
    public ApiResponse<FreightStatementResponse> update(@PathVariable Long id, @Valid @RequestBody FreightStatementRequest request) {
        return ApiResponse.success(
                "更新成功",
                freightStatementService.responseUpdate(id, request)
        );
    }

    @Operation(summary = "更新物流对账单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "freight-statement", action = "audit")
    public ApiResponse<FreightStatementResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", freightStatementService.responseUpdateStatus(id, request.status()));
    }

    @Operation(summary = "删除物流对账单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "delete")
    @OperationLoggable(moduleName = "物流对账单", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        freightStatementService.delete(id);
        return ApiResponse.success("删除成功");
    }

}
