package com.leo.erp.market.quotation.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.collab.CollabSessionIssuer;
import com.leo.erp.common.collab.CollabSessionResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报单比价（报价单）的实时协作会话（房间票据）签发。
 *
 * <p><b>可协作范围</b>：只有报单吨位与行备注（见前端 {@code quote-sheet-doc.ts}）。
 * 价格（网价/现货价/差价）全部实时派生且只读，**不进 CRDT**；因此本类也不碰价格字段 ——
 * 报价单的价格真相源仍是「行情 + 供应商价格表」，协作房间里只有吨位/备注两个可写值。</p>
 *
 * <p><b>房间口径</b>：一张报价单一个房间 {@code quote-sheet:<sheetId>}，一张单一次取票。
 * 边车把房间名当不透明键持久化文档与增量；报单比价文档 meta 里只有 {@code sheetId/version}、
 * 没有供应商价格表的 {@code meta.listId}，所以边车不会误走「写回供应商价格表条目」的物化路径。</p>
 *
 * <p><b>writable</b>：取票只要求读权限（只读看板也要能连），是否可写由
 * {@code quote-sheets:update} 决定；无写权限时发 {@code writable=false} 的票据，边车只读转发。</p>
 *
 * <p><b>票据装配</b>全部在 {@link CollabSessionIssuer}，与供应商价格表共用同一套口径。</p>
 */
@Service
public class QuoteSheetCollabSessionService {

    /** 房间前缀：一张报价单一个房间。 */
    static final String ROOM_PREFIX = "quote-sheet:";

    private final QuoteSheetRepository repository;
    private final PermissionChecker permissionChecker;
    private final AccountQuery accountQuery;
    private final CollabSessionIssuer sessionIssuer;

    public QuoteSheetCollabSessionService(QuoteSheetRepository repository,
                                          PermissionChecker permissionChecker,
                                          AccountQuery accountQuery,
                                          CollabSessionIssuer sessionIssuer) {
        this.repository = repository;
        this.permissionChecker = permissionChecker;
        this.accountQuery = accountQuery;
        this.sessionIssuer = sessionIssuer;
    }

    /**
     * 为指定报价单签发协作房间票据。
     *
     * @param sheetId   报价单 id（雪花，来自路径参数）
     * @param principal 当前登录主体
     * @return 201 响应体：房间、接入地址、票据、是否可写、光标颜色、过期时间
     * @throws BusinessException 报价单不存在或已删除（404）、未登录（401）
     */
    @Transactional(readOnly = true)
    public CollabSessionResponse createSession(Long sheetId, SecurityPrincipal principal) {
        // 认证先于资源探测：未登录不应该通过 404 与 200 的差异探出单据是否存在。
        sessionIssuer.requireAuthenticated(principal);
        // 只用「查版本号」做存在性探测：它不会初始化 items/brands 这些 bag（取票是高频只读操作，
        // 加载整单明细纯属浪费），deletedFlag 已在查询条件里过滤；返回 null 即不存在/已删除。
        if (repository.findVersionById(sheetId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在");
        }

        boolean writable = permissionChecker.has(PermissionCodes.QUOTE_SHEETS_UPDATE);
        String displayName = resolveDisplayName(principal);
        return sessionIssuer.issue(ROOM_PREFIX + sheetId, principal, displayName, writable);
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
