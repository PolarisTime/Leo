package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.service.FreightBillSalesOrderCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
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

@RestController
@Validated
@RequestMapping("/freight-bills")
public class FreightBillController {

    private final FreightBillService service;
    private final FreightBillSalesOrderCandidateService candidateService;
    public FreightBillController(FreightBillService service,
                                 FreightBillSalesOrderCandidateService candidateService) {
        this.service = service;
        this.candidateService = candidateService;
    }

    @GetMapping("/sales-order-candidates")
    @PreAuthorize("@rbac.check('sales-order', 'read')")
    public ApiResponse<PageResponse<SalesOrderResponse>> salesOrderCandidates(
            @BindPageQuery(sortFieldKey = "sales-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long currentRecordId
    ) {
        return ApiResponse.success(PageResponse.from(candidateService.page(
                query,
                PageFilter.of(keyword, null, null, null, null, null)
                        .withIdentity(null, null, null, null, currentRecordId)
        )));
    }

    @GetMapping("/search")
    @PreAuthorize("@rbac.check('freight-bill', 'read')")
    public ApiResponse<java.util.List<FreightBillResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @PreAuthorize("@rbac.check('freight-bill', 'read')")
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

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.check('freight-bill', 'read')")
    public ApiResponse<FreightBillResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@rbac.check('freight-bill', 'create')")
    @DomainEventAudited
    public ApiResponse<FreightBillResponse> create(@Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PostMapping("/save-and-audit")
    @PreAuthorize("@rbac.check('freight-bill', 'create')")
    @DomainEventAudited
    public ApiResponse<FreightBillResponse> createAndAudit(@Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("保存并审核成功", service.createAndAudit(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.check('freight-bill', 'update')")
    @DomainEventAudited
    public ApiResponse<FreightBillResponse> update(@PathVariable Long id, @Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @PutMapping("/{id}/save-and-audit")
    @PreAuthorize("@rbac.check('freight-bill', 'update')")
    @DomainEventAudited
    public ApiResponse<FreightBillResponse> updateAndAudit(@PathVariable Long id,
                                                           @Valid @RequestBody FreightBillRequest request) {
        return ApiResponse.success("保存并审核成功", service.updateAndAudit(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rbac.check('freight-bill', 'audit')")
    @DomainEventAudited
    public ApiResponse<FreightBillResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.check('freight-bill', 'delete')")
    @DomainEventAudited
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
