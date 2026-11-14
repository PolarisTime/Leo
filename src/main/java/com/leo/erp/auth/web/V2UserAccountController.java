package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAccountService;
import com.leo.erp.auth.service.UserAccountSettingsService;
import com.leo.erp.auth.service.UserSettingsConflictException;
import com.leo.erp.auth.web.dto.CurrentAccountResponse;
import com.leo.erp.auth.web.dto.CurrentAccountUpdateRequest;
import com.leo.erp.auth.web.dto.PasswordChangeRequest;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.auth.web.dto.UserSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsUpdateRequest;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2NoContent;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/account")
public class V2UserAccountController {

    private final UserAccountService userAccountService;
    private final UserAccountSettingsService userAccountSettingsService;
    private final ApiProblemFactory apiProblemFactory;

    public V2UserAccountController(UserAccountService userAccountService,
                                   UserAccountSettingsService userAccountSettingsService,
                                   ApiProblemFactory apiProblemFactory) {
        this.userAccountService = userAccountService;
        this.userAccountSettingsService = userAccountSettingsService;
        this.apiProblemFactory = apiProblemFactory;
    }

    @GetMapping
    public CurrentAccountResponse current(@AuthenticationPrincipal SecurityPrincipal principal) {
        return userAccountService.current(currentUserId(principal));
    }

    @PutMapping
    @OperationLoggable(moduleName = "个人账号", actionType = "编辑")
    public CurrentAccountResponse update(@AuthenticationPrincipal SecurityPrincipal principal, @Valid @RequestBody CurrentAccountUpdateRequest request) {
        return userAccountService.updateCurrent(currentUserId(principal), request);
    }

    @PutMapping("/password")
    @OperationLoggable(moduleName = "身份认证", actionType = "修改密码")
    @V2NoContent
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal SecurityPrincipal principal, @Valid @RequestBody PasswordChangeRequest request) {
        userAccountService.changePassword(currentUserId(principal), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 读取跨设备同步的界面设置（v2 结构；库里的 v1 旧数据也能正常读出）。
     *
     * <p>与已弃用的 {@code GET /account/preferences} 读写<b>同一份</b>存储
     * （{@code sys_user.preferences_json}），区别只是响应体多了 {@code version} 与 {@code sections}。</p>
     */
    @Operation(summary = "读取界面设置同步载荷（v2，兼容 v1 旧结构）")
    @GetMapping("/settings")
    public UserSettingsPayload settings(@AuthenticationPrincipal SecurityPrincipal principal) {
        return userAccountSettingsService.getSettings(currentUserId(principal));
    }

    /**
     * 按分片增量保存界面设置。
     *
     * <p><b>为什么不挂 {@link OperationLoggable}</b>：同步引擎按变更去抖后高频提交，若每次 PUT
     * 都记一条操作日志，审计表会被「改字号/改页签」这类个人偏好噪声淹没；本接口只改个人偏好、
     * 不碰业务数据，排查时看 {@code sys_user.preferences_json} 与 409 响应就够。</p>
     *
     * <p><b>幂等</b>：沿用仓库统一门禁的类级 {@link IdempotencyRequired}，客户端 PUT 需带
     * {@code X-Idempotency-Key}（与既有 {@code PUT /account/preferences} 一致）；服务端对
     * 「同一 {@code clientUpdatedAt} + 同一内容」的重复提交不做任何行改写。</p>
     *
     * <p><b>空请求体</b>按「无变化」处理：直接返回当前完整结构（200），便于客户端做幂等确认。</p>
     */
    @Operation(
            summary = "按分片增量保存界面设置（覆盖型 LWW，supplierQuote.materialTabs 深度合并）",
            description = "只提交变化的分片；服务端该分片更新更晚时返回 409，ProblemDetail 额外带 "
                    + "sections 与 serverValue；单分片超过 32KB 或总量超过 256KB 返回 413。")
    @PutMapping("/settings")
    public UserSettingsPayload saveSettings(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody(required = false) UserSettingsUpdateRequest request) {
        return userAccountSettingsService.saveSettings(currentUserId(principal), request);
    }

    /**
     * 设置分片冲突 → 409 ProblemDetail。
     *
     * <p>额外字段：{@code sections}（冲突分片的 key 列表）与 {@code serverValue}
     * （冲突分片在服务端的当前值，含 {@code value}/{@code clientUpdatedAt}/{@code serverUpdatedAt}），
     * 客户端据此「采用服务端值 + 提示已在其他设备更新」。放在控制器内而不是全局异常处理器：
     * 这两份扩展字段只对设置同步接口有意义，全局处理器不该为单个资源引入特例。</p>
     */
    @ExceptionHandler(UserSettingsConflictException.class)
    public ResponseEntity<ProblemDetail> handleSettingsConflict(
            UserSettingsConflictException exception,
            HttpServletRequest request) {
        ProblemDetail problem = apiProblemFactory.create(request, HttpStatus.CONFLICT,
                exception.getErrorCode(), exception.getMessage());
        problem.setProperty("sections", exception.getConflictSections());
        problem.setProperty("serverValue", exception.getServerSections());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 列表页列设置的读接口（v1 兼容别名，已被 {@code GET /account/settings} 取代）。
     *
     * @deprecated 改用 {@code GET /account/settings}；只为已上线客户端保留，两者读写同一份存储。
     */
    @Deprecated
    @Operation(summary = "读取列表页列设置（已弃用，改用 GET /account/settings）", deprecated = true)
    @GetMapping("/preferences")
    public UserAccountPreferencesPayload preferences(@AuthenticationPrincipal SecurityPrincipal principal) {
        return userAccountSettingsService.getPreferences(currentUserId(principal));
    }

    /**
     * 列表页列设置的写接口（v1 兼容别名，已被 {@code PUT /account/settings} 取代）。
     *
     * <p>只替换 {@code pages}，不会清掉新客户端写入的 {@code sections}。</p>
     *
     * @deprecated 改用 {@code PUT /account/settings}；只为已上线客户端保留。
     */
    @Deprecated
    @Operation(summary = "保存列表页列设置（已弃用，改用 PUT /account/settings）", deprecated = true)
    @PutMapping("/preferences")
    public UserAccountPreferencesPayload savePreferences(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody UserAccountPreferencesPayload request) {
        return userAccountSettingsService.savePreferences(currentUserId(principal), request);
    }

    private Long currentUserId(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw new IllegalStateException("认证主体不可用");
        }
        return principal.id();
    }
}
