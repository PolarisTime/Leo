package com.leo.erp.common.config;

import com.leo.erp.common.web.PageQueryArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebMvcConfigTest {

    @Test
    void addArgumentResolvers_registersPageQueryResolver() {
        PageQueryArgumentResolver resolver = mock(PageQueryArgumentResolver.class);
        WebMvcConfig config = new WebMvcConfig(resolver);

        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        config.addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(resolver);
    }

    @Test
    void constructor_setsResolver() {
        PageQueryArgumentResolver resolver = mock(PageQueryArgumentResolver.class);
        WebMvcConfig config = new WebMvcConfig(resolver);

        assertThat(config).isNotNull();
    }
}
