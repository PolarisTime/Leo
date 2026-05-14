package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.dto.MetaCodeResponse;
import com.leo.erp.common.web.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "元数据")
@RestController
@Validated
@RequestMapping("/api/meta")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @Operation(summary = "获取错误码和权限点元数据")
    @GetMapping("/code")
    public ApiResponse<MetaCodeResponse> codes() {
        return ApiResponse.success(metaService.codes());
    }
}
