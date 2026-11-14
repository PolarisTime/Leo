package com.leo.erp.market.supplierprice.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.supplierprice.CollabSessionProperties;
import com.leo.erp.market.supplierprice.repository.SupplierPriceListRepository;
import com.leo.erp.market.supplierprice.web.dto.CollabSessionResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
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
 * <p><b>票据</b>：JWT HS256，默认 10 分钟有效（{@code leo.collab.token-ttl-seconds}），
 * 只授予上面那一个房间。过期后前端重新 POST 取票即可，不提供续期接口。</p>
 */
@Slf4j
@Service
public class SupplierPriceCollabSessionService {

    /** 房间前缀，与边车 {@code room.ts} 的 {@code supplier-price-list:<listId>} 完全一致。 */
    static final String ROOM_PREFIX = "supplier-price-list:";

    private final SupplierPriceListRepository repository;
    private final PermissionChecker permissionChecker;
    private final AccountQuery accountQuery;
    private final CollabTokenService tokenService;
    private final CollabSessionProperties properties;

    public SupplierPriceCollabSessionService(SupplierPriceListRepository repository,
                                             PermissionChecker permissionChecker,
                                             AccountQuery accountQuery,
                                             CollabTokenService tokenService,
                                             CollabSessionProperties properties) {
        this.repository = repository;
        this.permissionChecker = permissionChecker;
        this.accountQuery = accountQuery;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * 为指定价格表签发协作房间票据。
     *
     * @param listId    价格表 id（雪花，来自路径参数）
     * @param principal 当前登录主体
     * @return 201 响应体：房间、接入地址、票据、是否可写、光标颜色、过期时间
     * @throws BusinessException 价格表不存在或未关联供应商（404）、未登录（401）
     */
    @Transactional(readOnly = true)
    public CollabSessionResponse createSession(Long listId, SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        // 存在性校验不能省：给一张已删除/不存在表的 id 签票，浏览器会拿到一个连不上的房间。
        repository.findByIdAndDeletedFlagFalse(listId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商价格表不存在"));

        String room = ROOM_PREFIX + listId;
        boolean writable = permissionChecker.has(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE);
        String displayName = resolveDisplayName(principal);
        String color = CollabCursorColors.colorOf(principal.id());
        // 截到秒：与 JWT 的 exp 秒级精度一致，前端拿到的 expiresAt 与 token 里的时刻完全相同。
        Instant expiresAt = Instant.now()
                .plusSeconds(properties.getTokenTtlSeconds())
                .truncatedTo(ChronoUnit.SECONDS);
        String token = tokenService.issue(principal.id(), displayName, room, writable, color, expiresAt);

        log.info("签发协作票据, userId={}, listId={}, room={}, writable={}",
                principal.id(), listId, room, writable);
        return new CollabSessionResponse(room, properties.getUrl(), token, writable, color,
                expiresAt.toString());
    }

    /**
     * 解析票据里要展示的用户名：优先账号资料里的显示名（同操作日志口径），
     * 查不到或为空时退化为登录名 —— 票据里必须有个能显示给人看的名字，不能留空。
     */
    private String resolveDisplayName(SecurityPrincipal principal) {
        return accountQuery.findById(principal.id())
                .map(AccountQuery.AccountSnapshot::userName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(principal.username());
    }
}
