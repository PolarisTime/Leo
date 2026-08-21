package com.leo.erp.master.code.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.code.web.dto.MasterDataCodeIssuanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/master-data/code-issuances")
public class V2MasterDataCodeIssuanceController {

    private final MasterDataCodeIssuanceService codeIssuanceService;

    public V2MasterDataCodeIssuanceController(MasterDataCodeIssuanceService codeIssuanceService) {
        this.codeIssuanceService = codeIssuanceService;
    }

    /**
     * 签发基础资料编码：POST 创建发放记录资源，返回 201 Created。
     * <p>
     * 发放记录持久化于 Redis（key: master-data:code-issuances:{moduleKey}:{code}，TTL 2 小时），
     * 签发的雪花码即记录标识，故不再新增冗余 issuanceId 字段（前端响应 schema 为 strict 模式）。
     * Location 指向发放记录资源 URI；当前未提供对应 GET 端点，记录仅可通过 validate/consume 语义访问。
     */
    @Operation(summary = "签发基础资料编码")
    @PostMapping("/{moduleKey}")
    @V2Created
    public ResponseEntity<MasterDataCodeIssuanceResponse> issue(@PathVariable String moduleKey) {
        String code = codeIssuanceService.issue(moduleKey);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(ApiVersion.V2_PREFIX)
                        .path("/master-data/code-issuances/")
                        .pathSegment(moduleKey, code)
                        .build()
                        .toUri())
                .body(new MasterDataCodeIssuanceResponse(code));
    }
}
