package com.leo.erp.inventory.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.inventory.service.InventoryBackfillService;
import com.leo.erp.inventory.service.InventoryBalanceMaintenanceService;
import com.leo.erp.inventory.service.InventoryBalanceQueryService;
import com.leo.erp.inventory.service.InventoryTransactionQueryService;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceMismatchResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceRebuildResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
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
    private final InventoryBalanceMaintenanceService maintenanceService;

    public V2InventoryController(InventoryBalanceQueryService balanceQueryService,
                                 InventoryTransactionQueryService transactionQueryService,
                                 InventoryBackfillService backfillService,
                                 InventoryBalanceMaintenanceService maintenanceService) {
        this.balanceQueryService = balanceQueryService;
        this.transactionQueryService = transactionQueryService;
        this.backfillService = backfillService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/balances")
    @Operation(summary = "分页查询库存余额（按物料/仓库/批次聚合）")
    @RequirePermission(PermissionCodes.INVENTORY_READ)
    public PageResponse<InventoryBalanceResponse> balances(
            @BindPageQuery(sortFieldKey = "inventory-balance") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId) {
        return balanceQueryService.page(query, keyword, warehouseId, materialId);
    }

    @GetMapping("/transactions")
    @Operation(summary = "分页查询库存事务流水")
    @RequirePermission(PermissionCodes.INVENTORY_READ)
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
    @RequirePermission(PermissionCodes.INVENTORY_BACKFILL)
    public ResponseEntity<InventoryBackfillResponse> createBackfill() {
        return V2ResponseSupport.created("/inventory/backfills", backfillService.backfill());
    }

    /**
     * 资源型重建端点：创建“库存余额重建任务”。
     * 从 inv_transaction 账本按 (materialId, warehouseId) 覆盖写回 inv_balance 并清理过期维度。
     * 重建事务持有 {@code inv:backfill} 全局排他锁，与所有库存记账事务（同一键的事务级共享锁）互斥：
     * 重建会等待在途记账提交，并在执行期间阻塞新的库存写。因此<b>不能</b>视为可随时执行的轻量操作，
     * 应在业务低峰触发。
     */
    @PostMapping("/balance-rebuilds")
    @Operation(
            summary = "创建库存余额重建任务（从账本重算快照，与记账互斥）",
            description = "同步执行：按 (materialId, warehouseId) 从账本聚合覆盖写回 inv_balance，"
                    + "并删除账本已无对应维度的过期行。结果本身幂等（重复执行得到同一账本聚合值），"
                    + "但重建持有 inv:backfill 排他锁、与并发记账互斥，执行期间会阻塞库存写路径，"
                    + "应在业务低峰调用；按本控制器的写接口统一要求携带 X-Idempotency-Key，"
                    + "权限与期初回填同为 inventory:backfill。")
    @V2Created
    @RequirePermission(PermissionCodes.INVENTORY_BACKFILL)
    public ResponseEntity<InventoryBalanceRebuildResponse> createBalanceRebuild() {
        return V2ResponseSupport.created(
                "/inventory/balance-rebuilds",
                new InventoryBalanceRebuildResponse(maintenanceService.rebuild()));
    }

    /**
     * 运维诊断端点：分页查询账本与快照不一致的维度（守恒校验）。
     * 空列表表示守恒；只读，不修改任何数据。
     */
    @GetMapping("/balance-mismatches")
    @Operation(
            summary = "分页查询库存余额与账本不一致的维度（守恒校验）",
            description = "只读诊断：返回数量不一致，或数量非零而金额不一致的 (materialId, warehouseId) 维度；"
                    + "默认按 materialId、warehouseId 升序，金额字段受 inventory:read:cost 字段权限控制。")
    @RequirePermission(PermissionCodes.INVENTORY_READ)
    public PageResponse<InventoryBalanceMismatchResponse> balanceMismatches(
            @BindPageQuery(sortFieldKey = "inventory-balance-mismatch") PageQuery query) {
        return maintenanceService.reconcile(query);
    }
}
