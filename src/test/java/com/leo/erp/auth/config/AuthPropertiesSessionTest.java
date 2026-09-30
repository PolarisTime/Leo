package com.leo.erp.auth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话上限豁免名单匹配规则测试。
 *
 * <p>为什么要专门测：豁免名单是「服务账号不再互相顶掉」的唯一开关，
 * 匹配写错（例如把前缀写法的星号当成字面量）会静默失效——
 * 表现为服务账号仍被踢掉，而配置看起来「已经加上了」。</p>
 */
class AuthPropertiesSessionTest {

    private AuthProperties.Session sessionWith(List<String> patterns) {
        AuthProperties.Session session = new AuthProperties().getSession();
        session.setExemptLoginNames(patterns);
        return session;
    }

    @Test
    void defaults_areThreeSessionsAndNoExemption() {
        AuthProperties properties = new AuthProperties();
        assertThat(properties.getSession().getMaxRefreshTokens()).isEqualTo(3);
        assertThat(properties.getSession().getExemptLoginNames()).isEmpty();
        assertThat(properties.getLoginAudit().getLastLoginWriteIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void exactMatch_isExempt() {
        AuthProperties.Session session = sessionWith(List.of("admin_prod"));
        assertThat(session.isExempt("admin_prod")).isTrue();
        assertThat(session.isExempt("admin_prod2")).isFalse();
        assertThat(session.isExempt("other")).isFalse();
    }

    @Test
    void prefixWildcard_isExempt() {
        AuthProperties.Session session = sessionWith(List.of("svc_*"));
        assertThat(session.isExempt("svc_monitor")).isTrue();
        assertThat(session.isExempt("svc_")).isFalse();
        assertThat(session.isExempt("user_svc_monitor")).isFalse();
    }

    @Test
    void blankAndNullInputs_areNotExempt() {
        AuthProperties.Session session = sessionWith(List.of("admin_prod", "svc_*", "  ", ""));
        assertThat(session.isExempt(null)).isFalse();
        assertThat(session.isExempt("   ")).isFalse();
        assertThat(session.isExempt("svc_x")).isTrue();
    }

    @Test
    void surroundingWhitespace_isTrimmed() {
        AuthProperties.Session session = sessionWith(List.of("  admin_prod  ", " svc_* "));
        assertThat(session.isExempt("admin_prod")).isTrue();
        assertThat(session.isExempt("svc_a")).isTrue();
    }

    @Test
    void nullList_isTreatedAsEmpty() {
        AuthProperties.Session session = new AuthProperties().getSession();
        session.setExemptLoginNames(null);
        assertThat(session.getExemptLoginNames()).isEmpty();
        assertThat(session.isExempt("anyone")).isFalse();
    }

    @Test
    void bareWildcard_matchesNothing() {
        // 只写 "*" 时前缀为空串，按实现不匹配任何账号；避免「一个星号就放行全部」
        AuthProperties.Session session = sessionWith(List.of("*"));
        assertThat(session.isExempt("admin_prod")).isFalse();
    }
}
