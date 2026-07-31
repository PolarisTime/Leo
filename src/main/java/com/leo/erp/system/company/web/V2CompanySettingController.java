package com.leo.erp.system.company.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/company-settings")
public class V2CompanySettingController {

    private final CompanySettingService companySettingService;

    public V2CompanySettingController(CompanySettingService companySettingService) {
        this.companySettingService = companySettingService;
    }

    @GetMapping
    public PageResponse<CompanySettingResponse> page(@BindPageQuery(sortFieldKey = "company-setting") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(companySettingService.page(query, keyword, status));
    }

    @GetMapping("/options")
    public List<CompanySettingOptionResponse> options() {
        return companySettingService.listActiveOptions();
    }

    @GetMapping("/{id}")
    public CompanySettingResponse detail(@PathVariable Long id) {
        return companySettingService.detail(id);
    }

    @GetMapping("/current")
    public CompanySettingResponse current() {
        return companySettingService.current();
    }

    @PutMapping("/current")
    @OperationLoggable(moduleName = "结算主体", actionType = "保存")
    public CompanySettingResponse saveCurrent(@Valid @RequestBody CompanySettingRequest request) {
        return companySettingService.saveCurrent(request);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<CompanySettingResponse> create(@Valid @RequestBody CompanySettingRequest request) {
        return V2ResponseSupport.created("/company-settings", companySettingService.create(request));
    }

    @PutMapping("/{id}")
    public CompanySettingResponse update(@PathVariable Long id, @Valid @RequestBody CompanySettingRequest request) {
        return companySettingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companySettingService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
