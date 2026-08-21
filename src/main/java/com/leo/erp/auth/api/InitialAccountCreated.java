package com.leo.erp.auth.api;

/** 首次初始化创建的账号摘要（不含凭据），供初始化流程返回脱敏表示。 */
public record InitialAccountCreated(
        Long id,
        String loginName,
        String userName
) {
}
