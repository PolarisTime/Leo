package com.leo.erp.common.config;

import com.leo.erp.common.idempotent.IdempotencyRequiredInterceptor;
import com.leo.erp.common.web.PageQueryArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PageQueryArgumentResolver pageQueryArgumentResolver;
    private final IdempotencyRequiredInterceptor idempotencyRequiredInterceptor;

    public WebMvcConfig(PageQueryArgumentResolver pageQueryArgumentResolver,
                        IdempotencyRequiredInterceptor idempotencyRequiredInterceptor) {
        this.pageQueryArgumentResolver = pageQueryArgumentResolver;
        this.idempotencyRequiredInterceptor = idempotencyRequiredInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(pageQueryArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyRequiredInterceptor);
    }
}
