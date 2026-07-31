package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/freight-statements")
public class V2FreightStatementController {

    private final FreightStatementService freightStatementService;

    public V2FreightStatementController(FreightStatementService freightStatementService) {
        this.freightStatementService = freightStatementService;
    }

    @Operation(summary = "搜索物流对账单")
    @GetMapping("/search")
    public java.util.List<FreightStatementResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return freightStatementService.responseSearch(
                keyword != null ? keyword : "", Math.min(limit, 500));
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
    public PageResponse<FreightStatementCandidateResponse> candidates(@BindPageQuery(sortFieldKey = "freight-bill") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long carrierId, @RequestParam(required = false) String carrierCode, @RequestParam(required = false) String carrierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentStatementId) {
        return PageResponse.from(freightStatementService.candidatePage(
                query,
                PageFilter.of(keyword, carrierName, settlementCompanyId, null, startDate, endDate)
                        .withIdentity(null, null, null, carrierId, currentStatementId),
                carrierCode
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

    @Operation(summary = "保存并审核物流对账单")
    @PostMapping("/save-and-audit")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<FreightStatementResponse> createAndAudit(@Valid @RequestBody FreightStatementRequest request) {
        return V2ResponseSupport.created(
                "/freight-statements", freightStatementService.responseCreateAndAudit(request));
    }

    @Operation(summary = "更新物流对账单")
    @PutMapping("/{id}")
    @DomainEventAudited
    public FreightStatementResponse update(@PathVariable Long id, @Valid @RequestBody FreightStatementRequest request) {
        return freightStatementService.responseUpdate(id, request);
    }

    @Operation(summary = "保存并审核物流对账单")
    @PutMapping("/{id}/save-and-audit")
    @DomainEventAudited
    public FreightStatementResponse updateAndAudit(@PathVariable Long id, @Valid @RequestBody FreightStatementRequest request) {
        return freightStatementService.responseUpdateAndAudit(id, request);
    }

    @Operation(summary = "更新物流对账单状态")
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public FreightStatementResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return freightStatementService.responseUpdateStatus(id, request.status());
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
