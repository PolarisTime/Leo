package com.leo.erp.purchase.order.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.purchase.order.service.PurchaseOrderItemIssuedDoneService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemIssuedDoneRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemIssuedDoneResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 采购订单明细行的「已开完」<b>手动标记</b>。
 *
 * <p>标记是明细行上的一个受控子资源 {@code issued-done}: {@code PUT} 设置(200 + 标记后状态),
 * {@code DELETE} 取消(204, 幂等)。它是人工裁定"该明细行不用再开了", <b>与吨位列的自动数字无关</b> ——
 * 吨位列给事实(已开货多少、还剩多少), 本标记给出人的最终结论; 两者并存, 本标记不根据吨位自动判定。</p>
 *
 * <p><b>权限</b>: 复用采购订单的 update 权限({@code purchase-orders:update})。理由: 标记是采购订单明细行
 * 自身的状态字段, 写它就是改采购订单; 新增 {@code purchase-order-items:update} 会引入一套只服务单字段的
 * 权限点、角色默认集合与菜单迁移, 收益不足。若后续明细行出现独立于订单的写操作再拆权限。</p>
 */
@Tag(name = "采购订单明细")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/purchase-order-items")
public class V2PurchaseOrderItemController {

    private final PurchaseOrderItemIssuedDoneService issuedDoneService;

    public V2PurchaseOrderItemController(PurchaseOrderItemIssuedDoneService issuedDoneService) {
        this.issuedDoneService = issuedDoneService;
    }

    @Operation(summary = "设置/取消采购订单明细行「已开完」标记",
            description = "人工裁定标记(不根据吨位自动判定、无任何自动超量提示); 幂等: 重复标记不覆盖首次留痕, "
                    + "issuedDone=false 等价于 DELETE; 明细行不存在或订单已删除 → 404; 缺 issuedDone → 422。")
    @PutMapping("/{itemId}/issued-done")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_UPDATE)
    public PurchaseOrderItemIssuedDoneResponse mark(
            @PathVariable Long itemId,
            @Valid @RequestBody PurchaseOrderItemIssuedDoneRequest request) {
        return issuedDoneService.mark(itemId, Boolean.TRUE.equals(request.issuedDone()),
                currentUserId(), currentUserName());
    }

    @Operation(summary = "取消采购订单明细行「已开完」标记",
            description = "取消标记并清空留痕; 幂等(未标记时同样 204); 明细行不存在 → 404。")
    @DeleteMapping("/{itemId}/issued-done")
    @V2NoContent
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_UPDATE)
    public ResponseEntity<Void> clear(@PathVariable Long itemId) {
        issuedDoneService.mark(itemId, false, currentUserId(), currentUserName());
        return V2ResponseSupport.noContent();
    }

    /** 当前操作人(与 JpaAuditConfig 同源): 未认证时回退 0/system。 */
    private static Long currentUserId() {
        return currentPrincipal().map(SecurityPrincipal::id).orElse(0L);
    }

    private static String currentUserName() {
        return currentPrincipal()
                .map(SecurityPrincipal::username)
                .filter(name -> name != null && !name.isBlank())
                .orElse("system");
    }

    private static Optional<SecurityPrincipal> currentPrincipal() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(SecurityPrincipal.class::isInstance)
                .map(SecurityPrincipal.class::cast);
    }
}
