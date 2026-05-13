package com.leo.erp.common.validation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * 全局 Bean Validation 配置。
 * 开启方法级校验（@Validated on class + @NotBlank/@Positive on method params）。
 */
@Configuration
public class ValidationConfig {

    /** 开启 Controller 类级别 @Validated 的方法参数校验 */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    /** 使用 Hibernate Validator 作为 JSR-380 实现 */
    @Bean
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }
}
