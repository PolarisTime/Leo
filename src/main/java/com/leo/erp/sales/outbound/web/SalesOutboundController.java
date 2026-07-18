package com.leo.erp.sales.outbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@Validated
@RequestMapping("/sales-outbounds")
@Tag(name = "销售出库")
public class SalesOutboundController {

    private final SalesOutboundService service;

    public SalesOutboundController(SalesOutboundService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @Operation(summary = "搜索销售出库")
    public ApiResponse<java.util.List<SalesOutboundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @Operation(summary = "分页查询销售出库")
    public ApiResponse<PageResponse<SalesOutboundResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-outbound") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(
                        query,
                        PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(customerId, projectId, null, null, null)
                )
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询销售出库详情")
    public ApiResponse<SalesOutboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @Operation(summary = "创建销售出库")
    @DomainEventAudited
    public ApiResponse<SalesOutboundResponse> create(@Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新销售出库")
    @DomainEventAudited
    public ApiResponse<SalesOutboundResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新销售出库状态")
    @DomainEventAudited
    public ApiResponse<SalesOutboundResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除销售出库")
    @DomainEventAudited
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
