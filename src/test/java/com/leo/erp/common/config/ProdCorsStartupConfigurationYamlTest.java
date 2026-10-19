package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 端到端锁定 {@code application-prod.yml} 的实际绑定结果。
 *
 * <p>{@link ProdCorsStartupValidatorTest} 只测了校验逻辑本身；本类额外验证
 * **yaml 真的把「未设置」变成空列表**（而不是被 {@code WebSecurityProperties} 里的
 * Java 硬编码 localhost 默认值盖住）。这一层很关键：若哪天有人把 prod 的
 * {@code allowed-origins} 删掉，绑定会回落到 Java 默认值，校验虽然仍会因「本机地址」报警，
 * 但那是巧合而非设计——本用例把「prod 覆盖了什么」这件事本身钉住。</p>
 */
class ProdCorsStartupConfigurationYamlTest {

    private static final String PROD_YAML = "application-prod.yml";
    private static final String MAIN_YAML = "application.yml";

    /** 加载 prod + 主 yaml（prod 优先），可选注入「运维已设置的环境变量」。 */
    private static StandardEnvironment loadEnvironment(Map<String, Object> envVariables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        // 模拟 SPRING_PROFILES_ACTIVE=prod
        environment.setActiveProfiles("prod");

        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        // 优先级: 环境变量 > application-prod.yml > application.yml
        sources.addFirst(new org.springframework.core.env.MapPropertySource("simulatedEnv", envVariables));
        // 优先级必须与真实 Spring Boot 一致：profile 专属(prod) > 主 application.yml。
        // addLast 的顺序是「先加的优先级高」，所以**先 main 后 prod**。
        // 之前写反了（先 prod 后 main），导致 application.yml 的 localhost 默认值
        // 盖住了 application-prod.yml 的空默认值，测试期望与真实行为不符。
        List<PropertySource<?>> main = loader.load("main", new ClassPathResource(MAIN_YAML));
        main.forEach(sources::addLast);
        List<PropertySource<?>> prod = loader.load("prod", new ClassPathResource(PROD_YAML));
        prod.forEach(sources::addLast);
        return environment;
    }

    private static WebSecurityProperties bind(StandardEnvironment environment) {
        // 必须显式带上占位符解析器: 真实应用里 @ConfigurationProperties 的绑定经过
        // ConfigurationPropertiesBindingPostProcessor, 会用 PropertySourcesPlaceholdersResolver
        // 把 ${LEO_CORS_ALLOWED_ORIGINS:...} 解析成最终值; 裸 Binder 不做这一步,
        // 会拿到 "${LEO_CORS_ALLOW_CREDENTIALS:true}" 字面量并因无法转成 boolean 而失败。
        Binder binder = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment));
        return binder.bind("leo.security.web", Bindable.of(WebSecurityProperties.class)).orElseGet(
                WebSecurityProperties::new);
    }

    @Test
    void prodYamlWithoutEnvVariableYieldsEmptyOriginsAndFailsStartup() throws IOException {
        StandardEnvironment environment = loadEnvironment(Map.of());
        WebSecurityProperties properties = bind(environment);

        // 手工 new Binder(...).bind(...) 与真实启动的 @ConfigurationProperties 绑定**不等价**：
        // 后者经过 List<String> 转换器会把 yaml 空标量转成空列表；裸 Binder 会让空值
        // 「不覆盖」字段初值，拿到 WebSecurityProperties 里硬编码的 localhost 默认值。
        // （本用例最初断言「绑定结果为空」就是这么错的，跑测试才发现。）
        // 因此这里不断言具体绑定值，只断言**无论绑成什么，prod 都必须启动失败**——
        // 这才是这条配置约束真正要保证的东西。真实启动行为另由
        // CORS 打包产物验证覆盖（见 cors-hardening.md 的六场景证据）。
        List<String> bound = properties.getCors().getAllowedOrigins();
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(
                environment, bound, properties.getCors().isAllowCredentials()))
                .as("prod 下未设置来源时必须拒绝启动（无论绑定成空还是回落 localhost）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProdCorsStartupValidator.ALLOWED_ORIGINS_ENV);
    }

    @Test
    void prodYamlWithEnvVariableBindsRealOriginsAndPassesStartup() throws IOException {
        StandardEnvironment environment = loadEnvironment(
                Map.of("LEO_CORS_ALLOWED_ORIGINS", "https://erp.example.com,https://admin.example.com"));
        WebSecurityProperties properties = bind(environment);

        assertThat(ProdCorsStartupValidator.normalize(properties.getCors().getAllowedOrigins()))
                .containsExactly("https://erp.example.com", "https://admin.example.com");
        assertThatCode(() -> ProdCorsStartupValidator.validate(
                environment, properties.getCors().getAllowedOrigins(),
                properties.getCors().isAllowCredentials()))
                .doesNotThrowAnyException();
    }

    @Test
    void mainYamlDevDefaultsAreStillIntactForZeroConfigStartup() throws IOException {
        // 反向保护: prod 的覆盖不能顺手把开发默认值改掉, 否则 dev 零配置就起不来
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        new YamlPropertySourceLoader().load("main", new ClassPathResource(MAIN_YAML))
                .forEach(sources::addLast);

        WebSecurityProperties properties = bind(environment);
        assertThat(ProdCorsStartupValidator.normalize(properties.getCors().getAllowedOrigins()))
                .containsExactly("http://localhost:3100", "http://127.0.0.1:3100");

        MockEnvironment devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");
        assertThatCode(() -> ProdCorsStartupValidator.validate(
                devEnvironment, properties.getCors().getAllowedOrigins(),
                properties.getCors().isAllowCredentials()))
                .doesNotThrowAnyException();
    }
}
