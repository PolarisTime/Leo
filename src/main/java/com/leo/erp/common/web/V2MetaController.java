package com.leo.erp.common.web;

import com.leo.erp.common.web.dto.MetaCodeResponse;
import com.leo.erp.common.web.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@Tag(name = "元数据")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/meta")
public class V2MetaController {

    private final MetaService metaService;

    public V2MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @Operation(summary = "获取错误码和系统元数据")
    @GetMapping("/codes")
    public MetaCodeResponse codes() {
        return metaService.codes();
    }
}
