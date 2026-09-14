package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.master.material.service.MaterialBatchRollbackService;
import com.leo.erp.master.material.web.dto.MaterialBatchRollbackResponse;
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
 * 导入批次是一等父资源，回滚是创建其 rollbacks 子资源的操作：
 * {@code POST /import-batches/{importBatchNo}/rollbacks}。
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

    @PostMapping("/{importBatchNo}/rollbacks")
    @V2Created
    @Operation(summary = "创建导入批次回滚任务")
    public ResponseEntity<MaterialBatchRollbackResponse> rollback(@PathVariable String importBatchNo) {
        return V2ResponseSupport.created(
                "/import-batches/" + importBatchNo + "/rollbacks",
                materialBatchRollbackService.rollback(importBatchNo)
        );
    }
}
