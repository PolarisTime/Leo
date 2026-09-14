package com.leo.erp.inventory.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.inventory.service.InventoryBackfillService;
import com.leo.erp.inventory.service.InventoryBalanceQueryService;
import com.leo.erp.inventory.service.InventoryTransactionQueryService;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@IdempotencyRequired
@Tag(name = "库存台账")
@RequestMapping(ApiVersion.V2_PREFIX + "/inventory")
public class V2InventoryController {

    private final InventoryBalanceQueryService balanceQueryService;
    private final InventoryTransactionQueryService transactionQueryService;
    private final InventoryBackfillService backfillService;

    public V2InventoryController(InventoryBalanceQueryService balanceQueryService,
                                 InventoryTransactionQueryService transactionQueryService,
                                 InventoryBackfillService backfillService) {
        this.balanceQueryService = balanceQueryService;
        this.transactionQueryService = transactionQueryService;
        this.backfillService = backfillService;
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

    /**
     * 资源型回填端点：创建“库存期初回填任务”。
     * 在单事务内对尚无有效库存事务的已审核/完成来源明细补记账，天然幂等；重复调用只返回 skipped。
     */
    @PostMapping("/backfills")
    @Operation(summary = "创建库存期初回填任务（扫描已审核来源单据补记库存事务，幂等）")
    @V2Created
    public ResponseEntity<InventoryBackfillResponse> createBackfill() {
        return V2ResponseSupport.created("/inventory/backfills", backfillService.backfill());
    }
}
