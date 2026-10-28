package com.leo.erp.system.operationlog.support;

import com.leo.erp.system.operationlog.config.OperationLogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求策略单测: 去噪/去重规则与操作类别。
 *
 * <p>这些规则决定「哪些请求不落审计」，一旦回退会被 40 万级日志淹没，因此用测试锁定；
 * 同时覆盖 context-path 与 /v2.0 前缀的剥离，避免部署前缀变化后规则失效。</p>
 */
class OperationLogRequestPolicyTest {

    private final OperationLogProperties properties = new OperationLogProperties();
    private final OperationLogRequestPolicy policy = new OperationLogRequestPolicy(properties);

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContextPath("/api");
        request.setServletPath(uri.substring("/api".length()));
        return request;
    }

    @Test
    void loginIsSkippedBecauseLoginServiceLogsItExplicitly() {
        assertThat(policy.shouldSkip(request("/api/v2.0/auth/login"))).isTrue();
    }

    @Test
    void refreshIsSkippedByDefaultToAvoidSessionNoise() {
        assertThat(policy.shouldSkip(request("/api/v2.0/auth/refresh"))).isTrue();
    }

    @Test
    void refreshCanBeLoggedWhenSwitchIsOn() {
        properties.setLogAuthRefresh(true);
        assertThat(policy.shouldSkip(request("/api/v2.0/auth/refresh"))).isFalse();
    }

    @Test
    void businessRequestsAreNotSkipped() {
        assertThat(policy.shouldSkip(request("/api/v2.0/materials"))).isFalse();
        assertThat(policy.shouldSkip(request("/api/v2.0/supplier-price-lists"))).isFalse();
    }

    @Test
    void categoryIsDerivedFromPath() {
        assertThat(policy.resolveCategory(request("/api/v2.0/auth/login")))
                .isEqualTo(OperationLogConstants.CATEGORY_SESSION);
        assertThat(policy.resolveCategory(request("/api/v2.0/system/config")))
                .isEqualTo(OperationLogConstants.CATEGORY_SYSTEM);
        assertThat(policy.resolveCategory(request("/api/v2.0/materials")))
                .isEqualTo(OperationLogConstants.CATEGORY_BUSINESS);
        assertThat(policy.resolveCategory(request("/api/v2.0/actuator/health")))
                .isEqualTo(OperationLogConstants.CATEGORY_SYSTEM);
    }

    @Test
    void relativePathStripsContextPathAndApiVersion() {
        assertThat(policy.relativePath(request("/api/v2.0/auth/login"))).isEqualTo("/auth/login");
        assertThat(policy.relativePath(request("/api/v2.0/quote-sheets/1"))).isEqualTo("/quote-sheets/1");
    }
}
