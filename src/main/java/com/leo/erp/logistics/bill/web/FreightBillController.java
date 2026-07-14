package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.service.FreightBillOutboundCommandService;
import com.leo.erp.logistics.bill.web.dto.FreightBillImportCandidateResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/freight-bills")
public class FreightBillController {

    private final FreightBillService service;
    private final FreightBillOutboundCommandService outboundCommandService;

    @Autowired
    public FreightBillController(FreightBillService service,
                                 FreightBillOutboundCommandService outboundCommandService) {
        this.service = service;
        this.outboundCommandService = outboundCommandService;
    }

    public FreightBillController(FreightBillService service) {
        this(service, null);
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "freight-bill", action = "read")
    public ApiResponse<java.util.List<FreightBillResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "freight-bill", action = "read")
    public ApiResponse<PageResponse<FreightBillResponse>> page(
            @BindPageQuery(sortFieldKey = "freight-bill") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long carrierId,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String carrierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(
                        query,
                        PageFilter.of(keyword, carrierName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(null, null, null, carrierId, null),
                        carrierCode
                )
        ));
    }

    @GetMapping("/import-candidates")
    @Deprecated(forRemoval = false)
    @RequiresPermission(resource = "sales-outbound", action = "read")
    public ApiResponse<PageResponse<FreightBillImportCandidateResponse>> importCandidates(
            @BindPageQuery(sortFieldKey = "sales-outbound") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currentBillId
    ) {
        PageFilter filter = PageFilter.of(
                keyword,
                customerName,
                projectName,
                settlementCompanyId,
                status,
                startDate,
                endDate
        ).withIdentity(customerId, projectId, null, null, currentBillId);
        return ApiResponse.success(PageResponse.from(service.importCandidates(query, filter)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "read")
    public ApiResponse<FreightBillResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "freight-bill", action = "create")
    public ApiResponse<FreightBillResponse> create(@Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PostMapping("/{id}/sales-outbound")
    @RequiresPermission(resource = "sales-outbound", action = "create")
    public ApiResponse<com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse> createSalesOutbound(
            @PathVariable Long id
    ) {
        if (outboundCommandService == null) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "销售出库生成服务不可用"
            );
        }
        return ApiResponse.success("销售出库草稿生成成功", outboundCommandService.createOutbound(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "update")
    public ApiResponse<FreightBillResponse> update(@PathVariable Long id, @Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "freight-bill", action = "audit")
    public ApiResponse<FreightBillResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "freight-bill", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
