package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端锁定框架/运维端点的实际配置值：actuator、springdoc、错误分派入口都必须挂在 {@code /api/v2.0} 下。
 *
 * <p>这一层测试解决的是「配置写了但绑定不生效」：直接加载真实的 {@code application.yml}
 * （以及 prod profile 的 {@code application-prod.yml}），经过与 Spring Boot 相同的
 * {@link Binder} + 占位符解析，再断言绑定后的对象。旧路径（{@code /actuator}、{@code /api-docs}、
 * {@code /doc}、{@code /error}）不得再作为有效配置出现，防止后人改回。</p>
 *
 * <p>独立管理端口与同端口的路径拼接规则（Spring Boot 3.5 语义）也在这里钉住：</p>
 * <ul>
 *   <li>同端口（本地/测试）：{@code server.servlet.context-path} + {@code management.endpoints.web.base-path}
 *       = {@code /api/v2.0/actuator}，此时 {@code management.server.base-path} 不生效；</li>
 *   <li>独立管理端口（prod）：{@code management.server.base-path} + {@code management.endpoints.web.base-path}
 *       = {@code /api/v2.0/actuator}，{@code server.servlet.context-path} 不参与。</li>
 * </ul>
 */
class FrameworkEndpointPathsConfigurationTest {

    private static final String SERVER_ERROR_PATH = "/v2.0/error";
    private static final String ACTUATOR_BASE_PATH = "/v2.0/actuator";
    private static final String API_V2_PREFIX = "/api/v2.0";

    @Test
    void mainProfileKeepsFrameworkEndpointsUnderApiV2Prefix() throws IOException {
        StandardEnvironment environment = loadEnvironment(false);

        ServerProperties server = bind(environment, "server", ServerProperties.class);
        WebEndpointProperties managementEndpoints =
                bind(environment, "management.endpoints.web", WebEndpointProperties.class);

        assertThat(server.getServlet().getContextPath()).isEqualTo("/api");
        assertThat(server.getError().getPath()).isEqualTo(SERVER_ERROR_PATH);
        assertThat(managementEndpoints.getBasePath()).isEqualTo(ACTUATOR_BASE_PATH);
        assertThat(managementEndpoints.getExposure().getInclude())
                .containsExactlyInAnyOrder("health", "loggers", "configprops");

        // 同端口（本地/测试）对外路径：context-path + 管理端点前缀，不允许出现 /api/api 双前缀。
        assertThat(server.getServlet().getContextPath() + managementEndpoints.getBasePath())
                .isEqualTo(API_V2_PREFIX + "/actuator");

        // 旧路径不得再是有效配置（改回即测试失败）。
        assertThat(server.getError().getPath()).isNotEqualTo("/error");
        assertThat(managementEndpoints.getBasePath()).isNotEqualTo("/actuator");

        // springdoc 的配置没有对应的强类型 @ConfigurationProperties 绑定需求，直接断言解析后的属性值。
        assertThat(environment.getProperty("springdoc.api-docs.path")).isEqualTo("/v2.0/api-docs");
        assertThat(environment.getProperty("springdoc.swagger-ui.path")).isEqualTo("/v2.0/doc");
        assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("springdoc.api-docs.path")).isNotEqualTo("/api-docs");
        assertThat(environment.getProperty("springdoc.swagger-ui.path")).isNotEqualTo("/doc");
    }

    @Test
    void mainProfileDoesNotSetManagementServerBasePathSoSamePortDoesNotDoubleApiPrefix() throws IOException {
        StandardEnvironment environment = loadEnvironment(false);

        // management.server.base-path 只在独立管理端口时生效；主配置不设置它，
        // 避免将来有人误以为同端口也需要 /api 而写出 /api/api/v2.0/actuator。
        assertThat(environment.getProperty("management.server.base-path")).isNull();
    }

    @Test
    void prodProfileKeepsIndependentManagementPortUnderApiV2Prefix() throws IOException {
        StandardEnvironment environment = loadEnvironment(true);

        ManagementServerProperties managementServer =
                bind(environment, "management.server", ManagementServerProperties.class);
        WebEndpointProperties managementEndpoints =
                bind(environment, "management.endpoints.web", WebEndpointProperties.class);

        assertThat(managementServer.getPort()).isEqualTo(57218);
        assertThat(managementServer.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(managementServer.getBasePath()).isEqualTo("/api");
        assertThat(managementEndpoints.getBasePath()).isEqualTo(ACTUATOR_BASE_PATH);
        assertThat(managementEndpoints.getExposure().getInclude())
                .contains("health", "prometheus", "loggers", "configprops");

        // 独立管理端口的对外路径：management.server.base-path + 管理端点前缀。
        assertThat(managementServer.getBasePath() + managementEndpoints.getBasePath())
                .isEqualTo(API_V2_PREFIX + "/actuator");
    }

    /** 加载 main（可选叠加 prod profile），优先级与 Spring Boot 一致：prod > application.yml。 */
    private static StandardEnvironment loadEnvironment(boolean withProdProfile) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        if (withProdProfile) {
            environment.setActiveProfiles("prod");
        }
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        // MutablePropertySources 中越靠前的源优先级越高，addLast 追加到末尾 = 更低优先级；
        // 因此先追加 prod 再追加 main，保证 profile 专属配置覆盖主配置。
        if (withProdProfile) {
            loader.load("prod", new ClassPathResource("application-prod.yml")).forEach(sources::addLast);
        }
        loader.load("main", new ClassPathResource("application.yml")).forEach(sources::addLast);
        return environment;
    }

    private static <T> T bind(StandardEnvironment environment, String prefix, Class<T> type) {
        // 必须带占位符解析器：真实应用绑定 @ConfigurationProperties 时会解析 ${LEO_...:默认值}，
        // 裸 Binder 会拿到占位符字面量并因类型转换失败。
        Binder binder = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment));
        return binder.bind(prefix, Bindable.of(type))
                .orElseThrow(() -> new AssertionError("无法绑定配置前缀: " + prefix));
    }
}
