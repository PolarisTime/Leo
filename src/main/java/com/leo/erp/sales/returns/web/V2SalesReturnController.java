package com.leo.erp.sales.returns.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.returns.service.SalesReturnCandidateService;
import com.leo.erp.sales.returns.service.SalesReturnService;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "销售退货单")
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/sales-returns")
public class V2SalesReturnController {

    private final SalesReturnService service;
    private final SalesReturnCandidateService candidateService;

    public V2SalesReturnController(SalesReturnService service,
                                   SalesReturnCandidateService candidateService) {
        this.service = service;
        this.candidateService = candidateService;
    }

    @GetMapping
    @Operation(summary = "分页查询销售退货单")
    public PageResponse<SalesReturnResponse> page(
            @BindPageQuery(sortFieldKey = "sales-return") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, customerName, projectName, null, status, startDate, endDate)
                        .withIdentity(customerId, projectId, null, null, null)
        ));
    }

    @GetMapping("/candidates")
    @Operation(summary = "查询销售退货来源候选（已审核销售出库可退明细）")
    public SalesReturnCandidateResponse candidates(@RequestParam Long salesOutboundId) {
        return candidateService.candidates(salesOutboundId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询销售退货单详情")
    public SalesReturnResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(summary = "创建销售退货单")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<SalesReturnResponse> create(@Valid @RequestBody SalesReturnRequest request) {
        return V2ResponseSupport.created("/sales-returns", service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新销售退货单")
    @DomainEventAudited
    public SalesReturnResponse update(@PathVariable Long id, @Valid @RequestBody SalesReturnRequest request) {
        return service.update(id, request);
    }

    /**
     * 资源型审核端点：创建“审核记录”子资源即完成审核。
     * 携带请求体时先保存再审核，省略时仅审核既有草稿。
     */
    @Operation(summary = "创建销售退货单审核记录（审核既有草稿或保存并审核）")
    @IdempotencyRequired
    @PostMapping("/{id}/audits")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<SalesReturnResponse> createAudit(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SalesReturnRequest request) {
        SalesReturnResponse response = request == null
                ? service.audit(id)
                : service.updateAndAudit(id, request);
        return V2ResponseSupport.created("/sales-returns", response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新销售退货单状态")
    @DomainEventAudited
    public SalesReturnResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除销售退货单")
    @DomainEventAudited
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }
}
