package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.master.material.service.MaterialBatchRollbackService;
import com.leo.erp.master.material.web.dto.MaterialBatchRollbackResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品资料导入批次资源接口。
 * 导入批次是一等父资源，回滚是对该批次执行的一次同步命令：
 * {@code POST /import-batches/{importBatchNo}/rollbacks}，成功返回 200 + 回滚统计。
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/import-batches")
@Tag(name = "商品资料导入批次")
public class V2ImportBatchController {

    private final MaterialBatchRollbackService materialBatchRollbackService;

    public V2ImportBatchController(MaterialBatchRollbackService materialBatchRollbackService) {
        this.materialBatchRollbackService = materialBatchRollbackService;
    }

    /**
     * 回滚命令端点：CREATED 行软删、UPDATED 行还原 before 快照。
     * <p>同步命令语义（不是创建可回读资源）：成功 200 + 回滚统计，不返回 201，也不发 Location。</p>
     */
    @PostMapping("/{importBatchNo}/rollbacks")
    @Operation(summary = "回滚导入批次（同步命令，200 + 回滚统计）")
    @RequirePermission(PermissionCodes.IMPORT_BATCHES_ROLLBACK)
    public ResponseEntity<MaterialBatchRollbackResponse> rollback(@PathVariable String importBatchNo) {
        return ResponseEntity.ok(materialBatchRollbackService.rollback(importBatchNo));
    }
}
