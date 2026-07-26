package com.leo.erp.auth.api;

/** 首次初始化账号的模块命令，不依赖调用方 Web DTO。 */
public record InitialAccountCommand(
        String loginName,
        String password,
        String userName,
        String mobile
) {
}
