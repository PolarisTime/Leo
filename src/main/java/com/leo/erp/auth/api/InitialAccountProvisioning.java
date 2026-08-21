package com.leo.erp.auth.api;

/** 首次账号初始化能力。并发编排仍由系统初始化模块负责。 */
public interface InitialAccountProvisioning {

    boolean isConfigured();

    InitialAccountCreated provision(InitialAccountCommand command);
}
