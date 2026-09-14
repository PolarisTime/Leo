package com.leo.erp.inventory.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.inventory.service.InventoryBalanceQueryService;
import com.leo.erp.inventory.service.InventoryTransactionQueryService;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@Tag(name = "库存台账")
@RequestMapping(ApiVersion.V2_PREFIX + "/inventory")
public class V2InventoryController {

    private final InventoryBalanceQueryService balanceQueryService;
    private final InventoryTransactionQueryService transactionQueryService;

    public V2InventoryController(InventoryBalanceQueryService balanceQueryService,
                                 InventoryTransactionQueryService transactionQueryService) {
        this.balanceQueryService = balanceQueryService;
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping("/balances")
    @Operation(summary = "分页查询库存余额（按物料/仓库/批次聚合）")
    public PageResponse<InventoryBalanceResponse> balances(
            @BindPageQuery(sortFieldKey = "inventory-balance") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId) {
        return balanceQueryService.page(query, keyword, warehouseId, materialId);
    }

    @GetMapping("/transactions")
    @Operation(summary = "分页查询库存事务流水")
    public PageResponse<InventoryTransactionResponse> transactions(
            @BindPageQuery(sortFieldKey = "inventory-transaction") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return transactionQueryService.page(
                query, keyword, materialId, warehouseId, transactionType, startDate, endDate);
    }
}
