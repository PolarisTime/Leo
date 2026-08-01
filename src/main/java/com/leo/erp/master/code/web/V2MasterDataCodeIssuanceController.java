package com.leo.erp.master.code.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.code.web.dto.MasterDataCodeIssuanceResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/master-data/code-issuances")
public class V2MasterDataCodeIssuanceController {

    private final MasterDataCodeIssuanceService codeIssuanceService;

    public V2MasterDataCodeIssuanceController(MasterDataCodeIssuanceService codeIssuanceService) {
        this.codeIssuanceService = codeIssuanceService;
    }

    @PostMapping("/{moduleKey}")
    public MasterDataCodeIssuanceResponse issue(@PathVariable String moduleKey) {
        return new MasterDataCodeIssuanceResponse(codeIssuanceService.issue(moduleKey));
    }
}
