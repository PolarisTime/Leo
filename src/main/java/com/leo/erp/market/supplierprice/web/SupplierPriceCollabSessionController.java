package com.leo.erp.market.supplierprice.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.market.supplierprice.service.SupplierPriceCollabSessionService;
import com.leo.erp.common.collab.CollabSessionResponse;
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
 * 供应商价格表的实时协作房间票据。
 *
 * <p><b>为什么单独一个控制器</b>：价格表本身的写接口在
 * {@link SupplierPriceListController}（加列 / 整组保存条目），而协作票据是「进房间的临时凭证」，
 * 生命周期与资源不同（默认 10 分钟过期、不可 GET、无持久化标识），单独放一类更清楚，
 * 也避免给价格表控制器再塞一个与列/条目无关的依赖。</p>
 *
 * <p><b>幂等</b>：沿用仓库统一门禁的类级 {@link IdempotencyRequired}，客户端 POST 需带
 * {@code X-Idempotency-Key}。票据本身不落库，重复提交只是重新签一张（旧票到期前仍然可用），
 * 不会产生资源副作用。</p>
 */
@Tag(name = "供应商价格表协作")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/supplier-price-lists")
public class SupplierPriceCollabSessionController {

    private final SupplierPriceCollabSessionService service;

    public SupplierPriceCollabSessionController(SupplierPriceCollabSessionService service) {
        this.service = service;
    }

    /**
     * 签发某张价格表的协作房间短票据。
     *
     * <p>只要读权限即可取票（看板/大屏只读也要连得上）；没有 {@code supplier-price-lists:update}
     * 时返回的票据 {@code writable=false}，边车侧只读转发。</p>
     *
     * <p>响应 201：会话资源一次性签发、不可寻址（无 GET），故不发 {@code Location}；
     * {@code room} 才是真正的持久化标识（{@code supplier-price:<supplierId>}）。</p>
     */
    @Operation(
            summary = "签发供应商价格表协作房间短票据（JWT HS256，默认 10 分钟）",
            description = "读权限即可取票；无 supplier-price-lists:update 权限时 writable=false。"
                    + "房间按价格表划分：room=supplier-price-list:<listId>，票据只授予这一个房间。")
    @PostMapping("/{listId}/collab-sessions")
    @RequirePermission(PermissionCodes.SUPPLIER_PRICE_LISTS_READ)
    public ResponseEntity<CollabSessionResponse> create(
            @PathVariable @SnowflakeId Long listId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ResponseSupport.created(
                "/supplier-price-lists/" + listId + "/collab-sessions",
                service.createSession(listId, principal));
    }
}
