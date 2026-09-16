package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.market.quotation.service.QuoteSheetEditLockService;
import com.leo.erp.market.quotation.web.dto.QuoteSheetEditLockResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "比价报价单编辑锁")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-sheets/{id}/edit-locks")
public class V2QuoteSheetEditLockController {

    private final QuoteSheetEditLockService service;

    public V2QuoteSheetEditLockController(QuoteSheetEditLockService service) {
        this.service = service;
    }

    @Operation(summary = "签出/续约编辑锁",
            description = "幂等: 本人已持有则续约; 他人未过期返回 409; 已过期允许抢占")
    @PostMapping
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public QuoteSheetEditLockResponse acquire(@AuthenticationPrincipal SecurityPrincipal principal,
                                              @PathVariable Long id) {
        SecurityPrincipal current = principal == null ? SecurityPrincipal.system() : principal;
        return service.acquire(id, current.id(), current.username());
    }

    @Operation(summary = "查询当前编辑锁", description = "无锁或已过期返回 200 + locked=false")
    @GetMapping
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteSheetEditLockResponse find(@AuthenticationPrincipal SecurityPrincipal principal,
                                           @PathVariable Long id) {
        return service.find(id, principal == null ? 0L : principal.id());
    }

    @Operation(summary = "释放编辑锁", description = "本人释放返回 204; 非本人返回 409")
    @DeleteMapping
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<Void> release(@AuthenticationPrincipal SecurityPrincipal principal,
                                        @PathVariable Long id) {
        service.release(id, principal == null ? 0L : principal.id());
        return V2ResponseSupport.noContent();
    }
}
