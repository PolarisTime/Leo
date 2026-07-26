package com.leo.erp.auth.api;

import java.util.Optional;

/** 跨模块读取可认证账号状态的同步查询接口。 */
public interface AuthenticationAccountQuery {

    Optional<AuthenticatedAccountSnapshot> findActiveById(Long userId);

    Optional<Long> findActiveCredentialVersion(Long userId);

    record AuthenticatedAccountSnapshot(
            Long userId,
            String loginName,
            long credentialVersion
    ) {
    }
}
