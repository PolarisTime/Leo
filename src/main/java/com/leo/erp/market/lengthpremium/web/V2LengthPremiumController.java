package com.leo.erp.market.lengthpremium.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.lengthpremium.service.LengthPremiumService;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumRequest;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

/**
 * 定尺加价维护接口。资源路径与权限码资源名 {@code length-premiums} 对齐。
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/length-premiums")
public class V2LengthPremiumController {

    private final LengthPremiumService lengthPremiumService;

    public V2LengthPremiumController(LengthPremiumService lengthPremiumService) {
        this.lengthPremiumService = lengthPremiumService;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_READ)
    public PageResponse<LengthPremiumResponse> page(
            @BindPageQuery(sortFieldKey = "length-premium") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        // 与取价地区同理: PageQuery 默认 DESC, 但排序号语义要求默认升序
        PageQuery effective = (query.direction() == null || query.direction().isBlank())
                ? new PageQuery(query.page(), query.size(), query.sortBy(), "asc")
                : query;
        return PageResponse.from(lengthPremiumService.page(effective, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_READ)
    public LengthPremiumResponse detail(@PathVariable Long id) {
        return lengthPremiumService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_CREATE)
    public ResponseEntity<LengthPremiumResponse> create(@Valid @RequestBody LengthPremiumRequest request) {
        return V2ResponseSupport.created("/length-premiums", lengthPremiumService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_UPDATE)
    public LengthPremiumResponse update(@PathVariable Long id, @Valid @RequestBody LengthPremiumRequest request) {
        return lengthPremiumService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        lengthPremiumService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
