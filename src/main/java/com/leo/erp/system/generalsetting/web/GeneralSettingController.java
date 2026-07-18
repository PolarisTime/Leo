package com.leo.erp.system.generalsetting.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.system.generalsetting.service.GeneralSettingQueryService;
import com.leo.erp.system.generalsetting.service.GeneralSettingService;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingResponse;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingUpdateRequest;
import com.leo.erp.system.generalsetting.web.dto.StatementGeneratorRulesResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/general-settings")
public class GeneralSettingController {

    private final GeneralSettingService generalSettingService;
    private final GeneralSettingQueryService generalSettingQueryService;

    public GeneralSettingController(GeneralSettingService generalSettingService,
                                    GeneralSettingQueryService generalSettingQueryService) {
        this.generalSettingService = generalSettingService;
        this.generalSettingQueryService = generalSettingQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GeneralSettingResponse>> page(
            @BindPageQuery(sortFieldKey = "general-setting") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(generalSettingQueryService.page(query, keyword, status)));
    }

    @GetMapping("/statement-generator-rule")
    public ApiResponse<StatementGeneratorRulesResponse> statementGeneratorRules() {
        return ApiResponse.success(generalSettingService.statementGeneratorRules());
    }

    @GetMapping("/{id}")
    public ApiResponse<GeneralSettingResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(generalSettingService.detail(id));
    }

    @PutMapping("/{id}")
    @OperationLoggable(moduleName = "通用设置", actionType = "编辑", businessNoFields = {"settingCode"})
    public ApiResponse<GeneralSettingResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody GeneralSettingUpdateRequest request
    ) {
        return ApiResponse.success("更新成功", generalSettingService.update(id, request));
    }

}
