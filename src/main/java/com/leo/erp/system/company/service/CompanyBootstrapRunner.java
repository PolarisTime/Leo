package com.leo.erp.system.company.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.config.CompanyBootstrapProperties;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompanyBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyBootstrapRunner.class);

    private final CompanyBootstrapProperties bootstrapProperties;

    public CompanyBootstrapRunner(CompanySettingRepository companySettingRepository,
                                  SnowflakeIdGenerator snowflakeIdGenerator,
                                  CompanyBootstrapProperties bootstrapProperties) {
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapProperties.isEnabled()) {
            log.warn("检测到 leo.company.bootstrap.enabled=true，但启动期公司主体灌库已废弃，请改用网页首次初始化流程。");
        }
    }
}
