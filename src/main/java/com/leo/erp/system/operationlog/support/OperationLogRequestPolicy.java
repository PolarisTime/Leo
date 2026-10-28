package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.system.operationlog.config.OperationLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 操作日志请求策略: 集中决定「哪些请求不记录」与「记录到哪个操作类别」。
 *
 * <p>排除规则集中在本类, 不散落在拦截器/服务里, 便于测试与调整:</p>
 * <ul>
 *   <li>{@code /auth/login}: {@code LoginService} 已显式记录「身份认证/登录」(成功与失败都有),
 *       拦截器再记一条就是双写, 因此拦截器侧跳过, 保留服务层那条显式记录;</li>
 *   <li>{@code /auth/refresh}: 默认视为会话噪声不记录; 需要排查令牌刷新时可配置
 *       {@code leo.operation-log.log-auth-refresh=true} 打开;</li>
 *   <li>{@code /auth/logout} 不在此列: 显式「退出登录」记录只在存在有效会话时产生,
 *       跳过拦截器会让无效令牌的退出请求失去审计, 故保持现状(仍可能双写, 属已知遗留)。</li>
 * </ul>
 *
 * <p>路径先去掉 context-path 与 API 版本前缀后再匹配, 因此规则不依赖部署前缀, 也不依赖
 * {@code /v2.0} 这一具体版本号。</p>
 */
@Component
public class OperationLogRequestPolicy {

    private final OperationLogProperties properties;

    public OperationLogRequestPolicy(OperationLogProperties properties) {
        this.properties = properties;
    }

    /** 拦截器是否应跳过该请求(去噪/去重)。 */
    public boolean shouldSkip(HttpServletRequest request) {
        String path = relativePath(request);
        if (OperationLogConstants.PATH_LOGIN.equals(path)) {
            return true;
        }
        if (OperationLogConstants.PATH_REFRESH.equals(path)) {
            return !properties.isLogAuthRefresh();
        }
        return false;
    }

    /** 解析操作类别: 会话类、系统类或业务类(默认)。 */
    public String resolveCategory(HttpServletRequest request) {
        String path = relativePath(request);
        if (isUnder(path, "/auth")) {
            return OperationLogConstants.CATEGORY_SESSION;
        }
        if (isUnder(path, "/system") || path.startsWith("/actuator")) {
            return OperationLogConstants.CATEGORY_SYSTEM;
        }
        return OperationLogConstants.CATEGORY_BUSINESS;
    }

    /** 去掉 context-path 与 API 版本前缀后的请求路径, 供路径规则匹配。 */
    public String relativePath(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if (path == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith(ApiVersion.V2_PREFIX)) {
            path = path.substring(ApiVersion.V2_PREFIX.length());
        }
        return path;
    }

    private boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
