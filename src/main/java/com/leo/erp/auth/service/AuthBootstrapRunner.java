package com.leo.erp.auth.service;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.auth.config.AuthProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthBootstrapRunner implements ApplicationRunner {
    private final AuthProperties authProperties;

    public AuthBootstrapRunner(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (authProperties.getBootstrap().isEnabled()) {
            log.warn("检测到 leo.auth.bootstrap.enabled=true，但启动期管理员灌库已废弃，请改用网页首次初始化流程。");
        }
    }
}
