package com.leo.erp.finance.receivablepayable.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.receivablepayable.service.ReceivablePayableService;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/receivables-payables")
public class ReceivablePayableController {

    private final ReceivablePayableService receivablePayableService;

    public ReceivablePayableController(ReceivablePayableService receivablePayableService) {
        this.receivablePayableService = receivablePayableService;
    }

    @GetMapping
    @RequiresPermission(resource = "receivable-payable", action = "read")
    public ApiResponse<PageResponse<ReceivablePayableResponse>> page(
            @BindPageQuery(sortFieldKey = "receivables-payables", directionParam = "sortDirection") PageQuery query,
            @RequestParam(name = "direction", required = false) String businessDirection,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(receivablePayableService.page(query, businessDirection, counterpartyType, keyword)));
    }
}
