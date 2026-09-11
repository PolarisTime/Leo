package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidatesCriteria;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.statement.freight.web.dto.FreightStatementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "物流对账单")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/freight-statements")
public class V2FreightStatementController {

    private final FreightStatementService freightStatementService;

    public V2FreightStatementController(FreightStatementService freightStatementService) {
        this.freightStatementService = freightStatementService;
    }

    @Operation(summary = "分页查询物流对账单")
    @GetMapping
    public PageResponse<FreightStatementResponse> page(@BindPageQuery(sortFieldKey = "freight-statement") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long carrierId, @RequestParam(required = false) String carrierCode, @RequestParam(required = false) String carrierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return PageResponse.from(freightStatementService.responsePage(
                query,
                pageFilter(keyword, carrierId, carrierName, settlementCompanyId, status, periodStart, periodEnd),
                carrierCode
        ));
    }

    @Operation(summary = "汇总物流对账单")
    @GetMapping("/summary")
    public FreightStatementSummaryResponse summary(@RequestParam(required = false) String keyword, @RequestParam(required = false) Long carrierId, @RequestParam(required = false) String carrierCode, @RequestParam(required = false) String carrierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return freightStatementService.summary(
                pageFilter(keyword, carrierId, carrierName, settlementCompanyId, status, periodStart, periodEnd),
                carrierCode
        );
    }

    @Operation(summary = "分页查询物流对账单候选物流单")
    @GetMapping("/candidates")
    public PageResponse<FreightStatementCandidateResponse> candidates(@BindPageQuery(sortFieldKey = "freight-bill") PageQuery query, @ModelAttribute FreightStatementCandidatesCriteria criteria) {
        return PageResponse.from(freightStatementService.candidatePage(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getCarrierName(), criteria.getSettlementCompanyId(), null, criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(null, null, null, criteria.getCarrierId(), criteria.getCurrentStatementId()),
                criteria.getCarrierCode()
        ));
    }

    @Operation(summary = "查询物流对账单详情")
    @GetMapping("/{id}")
    public FreightStatementResponse detail(@PathVariable Long id) {
        return freightStatementService.responseDetail(id);
    }

    @Operation(summary = "创建物流对账单")
    @PostMapping
    @DomainEventAudited
    @V2Created
    public ResponseEntity<FreightStatementResponse> create(@Valid @RequestBody FreightStatementRequest request) {
        return V2ResponseSupport.created(
                "/freight-statements", freightStatementService.responseCreate(request));
    }

    @Operation(summary = "更新物流对账单")
    @PutMapping("/{id}")
    @DomainEventAudited
    public FreightStatementResponse update(@PathVariable Long id, @Valid @RequestBody FreightStatementRequest request) {
        return freightStatementService.responseUpdate(id, request);
    }

    @Operation(summary = "更新物流对账单状态")
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public FreightStatementResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return freightStatementService.responseUpdateStatus(id, request.status());
    }

    /**
     * 资源型审核端点：创建“审核记录”子资源即把物流对账单推进到已审核。
     * 子资源无独立可回读路径，Location 指向父资源 {@code GET /freight-statements/{id}}。
     * 反审核仍使用 {@code PATCH /freight-statements/{id}/status}。
     */
    @Operation(summary = "创建物流对账单审核记录（审核）")
    @IdempotencyRequired
    @PostMapping("/{id}/audits")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<FreightStatementResponse> createAudit(@PathVariable Long id) {
        return V2ResponseSupport.created(
                "/freight-statements", freightStatementService.responseUpdateStatus(id, StatusConstants.AUDITED));
    }

    @Operation(summary = "删除物流对账单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        freightStatementService.delete(id);
        return V2ResponseSupport.noContent();
    }

    private PageFilter pageFilter(String keyword,
                                  Long carrierId,
                                  String carrierName,
                                  Long settlementCompanyId,
                                  String status,
                                  LocalDate periodStart,
                                  LocalDate periodEnd) {
        return new PageFilter(keyword, status, periodStart, periodEnd,
                carrierName, null, null, null, null, null, null, null, null, null,
                settlementCompanyId)
                .withIdentity(null, null, null, carrierId, null);
    }
}
