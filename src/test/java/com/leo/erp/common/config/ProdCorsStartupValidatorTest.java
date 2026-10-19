package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 锁定「生产环境 CORS 缺失即启动失败」的行为。
 *
 * <p>重点是**不能把开发环境搞挂**：本组用例显式覆盖 dev profile 零配置可启动，
 * 以及 prod 下四类静默误配（未设置、空值、把 localhost 带进生产、origin 形态不合法）
 * 全部启动失败且报错点名环境变量。另用 {@code ProdCorsStartupConfigurationTest}
 * 走真实 yaml 做端到端确认。</p>
 */
class ProdCorsStartupValidatorTest {

    private static final List<String> DEV_DEFAULT_ORIGINS =
            List.of("http://localhost:3100", "http://127.0.0.1:3100");

    private static MockEnvironment environment(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    // ---------- 开发/测试环境必须零配置可启动 ----------

    @Test
    void devProfileAllowsMissingOrigins() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(environment("dev"), null, true))
                .doesNotThrowAnyException();
    }

    @Test
    void devProfileAllowsLocalhostDefaults() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(environment("dev"), DEV_DEFAULT_ORIGINS, true))
                .doesNotThrowAnyException();
    }

    @Test
    void noActiveProfileIsNotProd() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(environment(), null, true))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfileIsNotProd() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(environment("test"), null, true))
                .doesNotThrowAnyException();
    }

    // ---------- prod：缺失即失败，且报错点名环境变量 ----------

    @Test
    void prodProfileRejectsNullOrigins() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment("prod"), null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProdCorsStartupValidator.ALLOWED_ORIGINS_ENV)
                .hasMessageContaining("未设置或为空");
    }

    @Test
    void prodProfileRejectsEmptyOrigins() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment("prod"), List.of(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProdCorsStartupValidator.ALLOWED_ORIGINS_ENV);
    }

    @Test
    void prodProfileRejectsBlankOrigins() {
        // LEO_CORS_ALLOWED_ORIGINS=  或 ="  " 都会解析成功但得到空列表,
        // 结果所有跨域被静默拒绝 —— 必须与「未设置」同样在启动期失败。
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment("prod"), List.of("  ", ""), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProdCorsStartupValidator.ALLOWED_ORIGINS_ENV);
    }

    @Test
    void errorMessageTellsOperatorExactlyWhatToSet() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment("prod"), null, true))
                .hasMessageContaining("LEO_CORS_ALLOWED_ORIGINS=https://")
                .hasMessageContaining("多个来源用英文逗号分隔");
    }

    // ---------- prod：把开发默认值带进生产 = 失败 ----------

    @Test
    void prodProfileRejectsLocalhostDefaultsCarriedOverFromDev() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment("prod"), DEV_DEFAULT_ORIGINS, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能包含本机地址")
                .hasMessageContaining("http://localhost:3100");
    }

    @Test
    void prodProfileRejectsIpv6Loopback() {
        assertThatThrownBy(() ->
                ProdCorsStartupValidator.validate(environment("prod"), List.of("http://[::1]:3100"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能包含本机地址");
    }

    @Test
    void prodProfileRejectsEitherLoopbackWhenMixedWithRealDomain() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(
                environment("prod"), List.of("https://erp.example.com", "http://127.0.0.1:3100"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://127.0.0.1:3100");
    }

    // ---------- prod：origin 形态非法 = 失败 ----------

    @Test
    void prodProfileRejectsTrailingSlash() {
        assertThatThrownBy(() ->
                ProdCorsStartupValidator.validate(environment("prod"), List.of("https://erp.example.com/"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式不合法");
    }

    @Test
    void prodProfileRejectsPathInOrigin() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(
                environment("prod"), List.of("https://erp.example.com/app"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式不合法");
    }

    @Test
    void prodProfileRejectsOriginWithoutScheme() {
        assertThatThrownBy(() ->
                ProdCorsStartupValidator.validate(environment("prod"), List.of("erp.example.com"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式不合法");
    }

    // ---------- prod：通配符 + 凭证是非法组合 ----------

    @Test
    void prodProfileRejectsWildcardWithCredentials() {
        // CORS 规范禁止 `*` 与 allow-credentials=true 同时出现; Spring 直到请求期才抛,
        // 表现为每个跨域请求 500 —— 提前到启动期失败。
        assertThatThrownBy(() ->
                ProdCorsStartupValidator.validate(environment("prod"), List.of("*"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通配符");
    }

    @Test
    void prodProfileAllowsWildcardWhenCredentialsDisabled() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(environment("prod"), List.of("*"), false))
                .doesNotThrowAnyException();
    }

    // ---------- prod：正常配置必须放行 ----------

    @Test
    void prodProfileAcceptsRealOrigins() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(
                environment("prod"), List.of("https://erp.example.com", "https://admin.example.com"), true))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileAcceptsOriginWithPort() {
        assertThatCode(() -> ProdCorsStartupValidator.validate(
                environment("prod"), List.of("https://erp.example.com:8443"), true))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileDetectedWhenCombinedWithOtherProfiles() {
        assertThatThrownBy(() ->
                ProdCorsStartupValidator.validate(environment("monitoring", "prod"), null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProdCorsStartupValidator.ALLOWED_ORIGINS_ENV);
    }

    @Test
    void prodProfileDetectionIsCaseInsensitiveAndTrimmed() {
        assertThatThrownBy(() -> ProdCorsStartupValidator.validate(environment(" PROD "), null, true))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- 工具方法 ----------

    @Test
    void normalizeTrimsAndDropsBlankEntries() {
        assertThat(ProdCorsStartupValidator.normalize(List.of(" https://a.example.com ", "", "   ",
                "https://b.example.com")))
                .containsExactly("https://a.example.com", "https://b.example.com");
    }

    @Test
    void normalizeReturnsEmptyForNull() {
        assertThat(ProdCorsStartupValidator.normalize(null)).isEmpty();
    }

    @Test
    void loopbackDetectionDoesNotMisjudgeRealDomainsContainingLocalhost() {
        // 只按 host 精确匹配, 不能把 localhost.example.com 这类真实域名误判成本机
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("https://localhost.example.com")).isFalse();
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("https://erp.example.com/localhost")).isFalse();
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("https://127.0.0.1.example.com")).isFalse();
    }

    @Test
    void loopbackDetectionMatchesAllLoopbackForms() {
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("http://localhost:3100")).isTrue();
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("HTTP://LOCALHOST")).isTrue();
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("http://127.0.0.1")).isTrue();
        assertThat(ProdCorsStartupValidator.isLoopbackOrigin("http://[::1]:3100")).isTrue();
    }
}
