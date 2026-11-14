package com.leo.erp.common.collab;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 协作会话（房间票据）的统一装配：**所有业务房间的唯一出口**。
 *
 * <p>各业务只需要决定三件事，其余全部收敛在这里，避免「供应商价格表一个口径、报单比价另一个口径」：</p>
 * <ol>
 *   <li>{@code room}：房间名（{@code <domain>:<id>}），由业务按自己的聚合根决定；</li>
 *   <li>{@code writable}：该用户在这个业务里有没有写权限（权限码由业务传，通常
 *       {@code xxx:update}）；</li>
 *   <li>{@code displayName}：账号资料里的显示名，查不到就传 {@code null}，由本类回退到登录名。</li>
 * </ol>
 *
 * <p>本类统一负责：未登录 401、显示名回退、光标颜色、过期时间（秒级截断，与 JWT {@code exp}
 * 同一时刻）、签票、响应装配。这样「换个业务开房间」不会顺带改坏过期时间或 claims 结构。</p>
 */
@Slf4j
@Service
public class CollabSessionIssuer {

    private final CollabTokenService tokenService;
    private final CollabSessionProperties properties;

    public CollabSessionIssuer(CollabTokenService tokenService, CollabSessionProperties properties) {
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * 未登录一律 401，且**必须在资源存在性探测之前**调用。
     *
     * <p>顺序有意义：先查库再判身份，会让「价格表/报价单是否存在」通过 404 与 401 的差异泄露给
     * 未登录调用方；生产环境虽然由安全过滤链先挡住，但 service 层保持同样的顺序后，
     * 直接调用 service 的路径（单测、将来的内部调用）也不会出现「未登录拿到 404」的错觉。</p>
     *
     * @throws BusinessException 未登录/主体非法（401）
     */
    public void requireAuthenticated(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
    }

    /**
     * 签发房间票据并装配响应。
     *
     * @param room        房间名，形如 {@code quote-sheet:<sheetId>}
     * @param principal   当前登录主体；为空或 id 非法直接 401
     * @param displayName 账号显示名，可为 null/空白（回退到登录名）
     * @param writable    是否允许写（由业务按权限码判定后传入）
     * @return 201 响应体（room/url/token/writable/color/expiresAt）
     * @throws BusinessException 未登录（401）
     */
    public CollabSessionResponse issue(String room,
                                       SecurityPrincipal principal,
                                       String displayName,
                                       boolean writable) {
        requireAuthenticated(principal);
        String name = displayName == null || displayName.isBlank()
                ? principal.username()
                : displayName;
        String color = CollabCursorColors.colorOf(principal.id());
        // 截到秒：与 JWT 的 exp 秒级精度一致，前端拿到的 expiresAt 与 token 里的时刻完全相同。
        Instant expiresAt = Instant.now()
                .plusSeconds(properties.getTokenTtlSeconds())
                .truncatedTo(ChronoUnit.SECONDS);
        String token = tokenService.issue(principal.id(), name, room, writable, color, expiresAt);

        log.info("签发协作票据, userId={}, room={}, writable={}", principal.id(), room, writable);
        return new CollabSessionResponse(room, properties.getUrl(), token, writable, color,
                expiresAt.toString());
    }
}
