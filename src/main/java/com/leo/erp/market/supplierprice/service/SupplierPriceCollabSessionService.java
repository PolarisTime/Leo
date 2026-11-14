package com.leo.erp.market.supplierprice.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.collab.CollabSessionIssuer;
import com.leo.erp.common.collab.CollabSessionResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.supplierprice.repository.SupplierPriceListRepository;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商价格表的实时协作会话（房间票据）签发。
 *
 * <p><b>房间口径</b>：一张价格表一个房间 {@code supplier-price-list:<listId>}。原因在前端：
 * 供应商报价页里每个品牌列 = 一张 {@code mk_supplier_price_list} = 一次取票 = 一个房间，
 * 房间名直接取本响应里的 {@code room}，前端不自己拼。边车 {@code parseRoom} 认这个前缀，
 * 物化时以文档 {@code meta.listId} 为准，因此一条链路能对上。</p>
 *
 * <p>这里必须用 {@code listId}，**不是** {@code supplierId}：同一供应商可能出现在多个品牌列
 * （多张表），按供应商聚成一个房间会让两张表的内容串在一起。</p>
 *
 * <p><b>writable</b>：取票只要求读权限（看板/大屏也要能连），是否可写由
 * {@code supplier-price-lists:update} 决定；没有写权限时发 {@code writable=false} 的票据，
 * 边车只读转发、丢弃该连接的 update。</p>
 *
 * <p><b>票据装配</b>全部在 {@link CollabSessionIssuer}（401、显示名回退、颜色、过期时间、
 * 签票、响应体），这里只决定房间名与写权限 —— 与报单比价共用同一套票据口径，不会各写一份。</p>
 */
@Service
public class SupplierPriceCollabSessionService {

    /** 房间前缀，与边车 {@code room.ts} 的 {@code supplier-price-list:<listId>} 完全一致。 */
    static final String ROOM_PREFIX = "supplier-price-list:";

    private final SupplierPriceListRepository repository;
    private final PermissionChecker permissionChecker;
    private final AccountQuery accountQuery;
    private final CollabSessionIssuer sessionIssuer;

    public SupplierPriceCollabSessionService(SupplierPriceListRepository repository,
                                             PermissionChecker permissionChecker,
                                             AccountQuery accountQuery,
                                             CollabSessionIssuer sessionIssuer) {
        this.repository = repository;
        this.permissionChecker = permissionChecker;
        this.accountQuery = accountQuery;
        this.sessionIssuer = sessionIssuer;
    }

    /**
     * 为指定价格表签发协作房间票据。
     *
     * @param listId    价格表 id（雪花，来自路径参数）
     * @param principal 当前登录主体
     * @return 201 响应体：房间、接入地址、票据、是否可写、光标颜色、过期时间
     * @throws BusinessException 价格表不存在（404）、未登录（401）
     */
    @Transactional(readOnly = true)
    public CollabSessionResponse createSession(Long listId, SecurityPrincipal principal) {
        // 认证先于资源探测：未登录不应该拿到 404 与 200 的差异（与改造前的语义保持一致）。
        sessionIssuer.requireAuthenticated(principal);
        // 存在性校验不能省：给一张已删除/不存在表的 id 签票，浏览器会拿到一个连不上的房间。
        repository.findByIdAndDeletedFlagFalse(listId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商价格表不存在"));

        boolean writable = permissionChecker.has(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE);
        String displayName = resolveDisplayName(principal);
        return sessionIssuer.issue(ROOM_PREFIX + listId, principal, displayName, writable);
    }

    /**
     * 取账号资料里的显示名；查不到返回 null，由 {@link CollabSessionIssuer} 回退到登录名。
     *
     * <p>未登录时不查库（也查不到），同样交给 issuer 统一 401。</p>
     */
    private String resolveDisplayName(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            return null;
        }
        return accountQuery.findById(principal.id())
                .map(AccountQuery.AccountSnapshot::userName)
                .orElse(null);
    }
}
