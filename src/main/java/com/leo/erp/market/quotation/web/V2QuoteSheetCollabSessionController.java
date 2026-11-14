package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.collab.CollabSessionResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.market.quotation.service.QuoteSheetCollabSessionService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报单比价（报价单）的实时协作房间票据。
 *
 * <p>与供应商价格表的票据接口同一套机制（同一份 JWT 密钥/claims/响应结构），只是房间按
 * 「一张报价单」划分：{@code quote-sheet:<sheetId>}。单独一个控制器而不是塞进退价单控制器：
 * 票据是临时凭证（默认 10 分钟、不可 GET、无持久化标识），生命周期与报价单本身不同。</p>
 *
 * <p><b>幂等</b>：类级 {@link IdempotencyRequired}，客户端 POST 需带 {@code X-Idempotency-Key}；
 * 票据不落库，重复提交只是重签一张，不会产生资源副作用。</p>
 */
@Tag(name = "比价报价单协作")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-sheets")
public class V2QuoteSheetCollabSessionController {

    private final QuoteSheetCollabSessionService service;

    public V2QuoteSheetCollabSessionController(QuoteSheetCollabSessionService service) {
        this.service = service;
    }

    /**
     * 签发某张报价单的协作房间短票据。
     *
     * <p>读权限即可取票；无 {@code quote-sheets:update} 时 {@code writable=false}，边车只读转发。
     * 响应 201：会话不可寻址（无 GET），故不发 {@code Location}；持久化标识是 {@code room}。</p>
     */
    @Operation(
            summary = "签发报单比价协作房间短票据（JWT HS256，默认 10 分钟）",
            description = "读权限即可取票；无 quote-sheets:update 权限时 writable=false。"
                    + "room=quote-sheet:<sheetId>，票据只授予这一张报价单的房间。"
                    + "可协作范围仅报单吨位与行备注，价格不进 CRDT。")
    @PostMapping("/{sheetId}/collab-sessions")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public ResponseEntity<CollabSessionResponse> create(
            @PathVariable @SnowflakeId Long sheetId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return V2ResponseSupport.created(
                "/quote-sheets/" + sheetId + "/collab-sessions",
                service.createSession(sheetId, principal));
    }
}
