package com.leo.erp.master.valuealias.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.valuealias.service.ValueAliasService;
import com.leo.erp.master.valuealias.web.dto.ValueAliasRequest;
import com.leo.erp.master.valuealias.web.dto.ValueAliasResponse;
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
 * 值映射/别名维护接口。
 *
 * <p>资源路径 {@code /api/v2/value-aliases} 与权限码资源名 {@code value-aliases} 对齐。</p>
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/value-aliases")
public class V2ValueAliasController {

    private final ValueAliasService valueAliasService;

    public V2ValueAliasController(ValueAliasService valueAliasService) {
        this.valueAliasService = valueAliasService;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public PageResponse<ValueAliasResponse> page(
            @BindPageQuery(sortFieldKey = "value-alias") PageQuery query,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String keyword) {
        return PageResponse.from(valueAliasService.page(query, dimension, keyword));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public ValueAliasResponse detail(@PathVariable Long id) {
        return valueAliasService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.VALUE_ALIASES_CREATE)
    public ResponseEntity<ValueAliasResponse> create(@Valid @RequestBody ValueAliasRequest request) {
        return V2ResponseSupport.created("/value-aliases", valueAliasService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_UPDATE)
    public ValueAliasResponse update(@PathVariable Long id, @Valid @RequestBody ValueAliasRequest request) {
        return valueAliasService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.VALUE_ALIASES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        valueAliasService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
