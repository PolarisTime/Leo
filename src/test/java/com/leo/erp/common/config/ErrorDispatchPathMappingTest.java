package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 错误分派路径的映射级验证：{@code server.error.path} 生效后，
 * 新路径 {@code /v2.0/error} 必须由 {@code BasicErrorController} 承接，旧 {@code /error} 不再暴露。
 *
 * <p>用 Spring Boot 的 {@link WebApplicationContextRunner} 只装配 Web MVC + 错误分派相关的
 * 自动配置（不启动任何真实服务、不连接数据库），因此可以在本地按定向测试反复执行。
 * 注意：这里验证的是「映射是否按配置注册」。业务异常仍由 {@code GlobalExceptionHandler}
 * 直接返回 RFC 9457 {@code ProblemDetail}，不经过 {@code BasicErrorController}；
 * 该契约由既有异常处理器测试单独覆盖。</p>
 */
class ErrorDispatchPathMappingTest {

    private static final String LEGACY_ERROR_PATH = "/error";

    @Test
    void configuredErrorPathIsMappedAndLegacyErrorPathIsGone() throws Exception {
        String errorPath = configuredErrorPath();
        assertThat(errorPath).isEqualTo("/v2.0/error");

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DispatcherServletAutoConfiguration.class,
                        WebMvcAutoConfiguration.class,
                        ErrorMvcAutoConfiguration.class,
                        JacksonAutoConfiguration.class))
                .withPropertyValues("server.error.path=" + errorPath)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    MockMvc mockMvc = MockMvcBuilders
                            .webAppContextSetup((WebApplicationContext) context)
                            .build();

                    // 新错误路径已注册 BasicErrorController：无错误属性时按 500 兜底，证明映射存在。
                    mockMvc.perform(get(errorPath).accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isInternalServerError());

                    // 旧 /error 不再有处理器，返回 404 而不是错误页 JSON。
                    mockMvc.perform(get(LEGACY_ERROR_PATH).accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());
                });
    }

    private static String configuredErrorPath() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("main", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        Binder binder = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment));
        ServerProperties server = binder.bind("server", Bindable.of(ServerProperties.class))
                .orElseThrow(() -> new AssertionError("无法绑定 server 配置前缀"));
        return server.getError().getPath();
    }
}
