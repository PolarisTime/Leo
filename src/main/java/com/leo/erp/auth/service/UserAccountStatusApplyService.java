package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import org.springframework.stereotype.Service;

/** 账号状态的唯一写入口，保持状态变更规则与账号编排职责分离。 */
@Service
public class UserAccountStatusApplyService {

    public void apply(UserAccount account, UserStatus status) {
        account.setStatus(status);
    }
}
