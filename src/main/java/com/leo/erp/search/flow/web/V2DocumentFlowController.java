package com.leo.erp.search.flow.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.search.flow.service.DocumentFlowService;
import com.leo.erp.search.flow.web.dto.DocumentFlowResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "单据流向")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/document-flows")
public class V2DocumentFlowController {

    private final DocumentFlowService documentFlowService;

    public V2DocumentFlowController(DocumentFlowService documentFlowService) {
        this.documentFlowService = documentFlowService;
    }

    @Operation(summary = "按单号查询采购/销售/物流单据流向")
    @GetMapping("/{documentNo}")
    @RequirePermission(PermissionCodes.GLOBAL_SEARCH_READ)
    public DocumentFlowResponse documentFlow(@PathVariable String documentNo) {
        return documentFlowService.documentFlow(documentNo);
    }
}
