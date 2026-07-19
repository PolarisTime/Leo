package com.leo.erp.master.code.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/master-data/code-issuances")
public class MasterDataCodeIssuanceController {

    private final MasterDataCodeIssuanceService codeIssuanceService;

    public MasterDataCodeIssuanceController(MasterDataCodeIssuanceService codeIssuanceService) {
        this.codeIssuanceService = codeIssuanceService;
    }

    @PostMapping("/{moduleKey}")
    public ApiResponse<String> issue(@PathVariable String moduleKey) {
        return ApiResponse.success("生成成功", codeIssuanceService.issue(moduleKey));
    }
}
