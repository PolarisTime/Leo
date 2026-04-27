package com.leo.erp.system.operationlog.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OperationLogWebConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    public OperationLogWebConfig(OperationLogInterceptor operationLogInterceptor) {
        this.operationLogInterceptor = operationLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor);
    }
}
