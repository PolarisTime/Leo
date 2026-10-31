package com.leo.erp.security.config;

import com.leo.erp.system.securitykey.api.SecurityKeyQuery;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SecurityKeyStartupValidator implements ApplicationRunner {

    private final SecurityKeyQuery securityKeyService;

    public SecurityKeyStartupValidator(SecurityKeyQuery securityKeyService) {
        this.securityKeyService = securityKeyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        securityKeyService.getActiveJwtMaterial();
        securityKeyService.getActiveDataMaterial();
    }
}
