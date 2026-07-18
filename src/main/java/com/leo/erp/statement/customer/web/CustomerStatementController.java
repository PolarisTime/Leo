package com.leo.erp.statement.customer.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "客户对账单")
@RestController
@Validated
@RequestMapping("/customer-statements")
public class CustomerStatementController {

    private final CustomerStatementService customerStatementService;

    public CustomerStatementController(CustomerStatementService customerStatementService) {
        this.customerStatementService = customerStatementService;
    }

    @Operation(summary = "搜索客户对账单")
    @GetMapping("/search")
    public ApiResponse<java.util.List<CustomerStatementResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(
                customerStatementService.search(keyword != null ? keyword : "", Math.min(limit, 500))
        );
    }

    @Operation(summary = "分页查询客户对账单")
    @GetMapping
    public ApiResponse<PageResponse<CustomerStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "customer-statement") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        return ApiResponse.success(PageResponse.from(
                customerStatementService.page(
                        query,
                        PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status,
                                        periodStart, periodEnd)
                                .withIdentity(customerId, projectId, null, null, null)
                )
        ));
    }

    @Operation(summary = "分页查询客户对账单候选销售订单")
    @GetMapping("/candidates")
    public ApiResponse<PageResponse<CustomerStatementCandidateResponse>> candidates(
            @BindPageQuery(sortFieldKey = "sales-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currentStatementId
    ) {
        return ApiResponse.success(PageResponse.from(
                customerStatementService.candidatePage(
                        query,
                        PageFilter.of(keyword, customerName, projectName, settlementCompanyId, null,
                                        startDate, endDate)
                                .withIdentity(customerId, projectId, null, null, currentStatementId)
                )
        ));
    }

    @Operation(summary = "查询客户对账单详情")
    @GetMapping("/{id}")
    public ApiResponse<CustomerStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(customerStatementService.detail(id));
    }

    @Operation(summary = "创建客户对账单")
    @PostMapping
    public ApiResponse<CustomerStatementResponse> create(@Valid @RequestBody CustomerStatementRequest request) {
        return ApiResponse.success("创建成功", customerStatementService.create(request));
    }

    @Operation(summary = "更新客户对账单")
    @PutMapping("/{id}")
    public ApiResponse<CustomerStatementResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerStatementRequest request) {
        return ApiResponse.success("更新成功", customerStatementService.update(id, request));
    }

    @Operation(summary = "更新客户对账单状态")
    @PatchMapping("/{id}/status")
    public ApiResponse<CustomerStatementResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", customerStatementService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除客户对账单")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerStatementService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
