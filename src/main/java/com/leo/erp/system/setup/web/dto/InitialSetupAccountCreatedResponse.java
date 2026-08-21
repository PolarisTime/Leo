package com.leo.erp.system.setup.web.dto;

import com.leo.erp.auth.api.InitialAccountCreated;

/**
 * 初始化账号创建结果摘要。仅含脱敏表示，不暴露密码哈希等凭据；
 * id 为雪花 ID，经 JacksonConfig 全局 ToStringSerializer 序列化为十进制字符串。
 */
public record InitialSetupAccountCreatedResponse(
        Long id,
        String loginName,
        String userName
) {

    public static InitialSetupAccountCreatedResponse from(InitialAccountCreated created) {
        return new InitialSetupAccountCreatedResponse(created.id(), created.loginName(), created.userName());
    }
}
