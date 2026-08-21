package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillSalesOrderCandidateService;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateResponse;
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

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/freight-bills")
public class V2FreightBillController {

    private final FreightBillService service;
    private final FreightBillSalesOrderCandidateService candidateService;
    private final FreightBillSalesOrderCandidateResponseAssembler candidateResponseAssembler;

    public V2FreightBillController(FreightBillService service,
                                   FreightBillSalesOrderCandidateService candidateService,
                                   FreightBillSalesOrderCandidateResponseAssembler candidateResponseAssembler) {
        this.service = service;
        this.candidateService = candidateService;
        this.candidateResponseAssembler = candidateResponseAssembler;
    }

    @GetMapping("/sales-order-candidates")
    public PageResponse<FreightBillSalesOrderCandidateResponse> salesOrderCandidates(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentRecordId) {
        return candidateResponseAssembler.toPageResponse(candidateService.page(
                query,
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, null, startDate, endDate)
                        .withIdentity(customerId, projectId, null, null, currentRecordId)
        ));
    }

    @GetMapping("/search")
    public java.util.List<FreightBillResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return service.search(keyword != null ? keyword : "", Math.min(limit, 500));
    }

    @GetMapping
    public PageResponse<FreightBillResponse> page(@BindPageQuery(sortFieldKey = "freight-bill") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long carrierId, @RequestParam(required = false) String carrierCode, @RequestParam(required = false) String carrierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, carrierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, null, carrierId, null),
                carrierCode
        ));
    }

    @GetMapping("/{id}")
    public FreightBillResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @DomainEventAudited
    @V2Created
    public ResponseEntity<FreightBillResponse> create(@Valid @RequestBody FreightBillRequest request) {
        return V2ResponseSupport.created("/freight-bills", service.create(request));
    }

    @PutMapping("/{id}")
    @DomainEventAudited
    public FreightBillResponse update(@PathVariable Long id, @Valid @RequestBody FreightBillRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public FreightBillResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }
}
