package com.leo.erp.auth.api;

import java.time.LocalDateTime;
import java.util.Optional;

/** 跨模块读取账号展示信息的同步查询接口。 */
public interface AccountQuery {

    Optional<AccountSnapshot> findById(Long userId);

    record AccountSnapshot(
            Long userId,
            String loginName,
            String userName,
            LocalDateTime lastLoginAt
    ) {
    }
}
