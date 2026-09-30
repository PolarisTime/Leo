package com.leo.erp.security.jwt;

import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.error.ServiceUnavailableException;

/**
 * 认证依赖（Redis）不可用，且降级窗口内没有可用快照时抛出。
 *
 * <p>存在的意义是把「基础设施不可用」与「代码缺陷」在异常体系里分开：
 * 前者必须映射成 503 + {@link ErrorCode#SERVICE_UNAVAILABLE}，
 * 后者仍然是 500。详见 {@code AuthFallbackProperties} 的背景说明。</p>
 */
public class AuthenticationInfrastructureException extends ServiceUnavailableException {

    public AuthenticationInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
