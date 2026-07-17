package com.leo.erp.common.config;

import org.casbin.adapter.JDBCAdapter;
import org.casbin.jcasbin.main.SyncedEnforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class CasbinConfiguration {

    @Bean(destroyMethod = "close")
    public JDBCAdapter casbinAdapter(DataSource dataSource) throws Exception {
        return new JDBCAdapter(dataSource, false, "casbin_rule", false);
    }

    @Bean
    public SyncedEnforcer casbinEnforcer(
            JDBCAdapter casbinAdapter,
            @Value("classpath:casbin/rbac_model.conf") Resource modelResource
    ) throws IOException {
        String modelText = modelResource.getContentAsString(StandardCharsets.UTF_8);
        return new SyncedEnforcer(Model.newModelFromString(modelText), casbinAdapter);
    }
}
