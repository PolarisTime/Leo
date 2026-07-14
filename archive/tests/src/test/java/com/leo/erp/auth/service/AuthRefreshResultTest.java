package com.leo.erp.auth.service;

import com.leo.erp.auth.web.dto.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRefreshResultTest {

    @Test
    void shouldCreateRecordWithToken() {
        TokenResponse token = new TokenResponse("access", "refresh", "Bearer", 300L, 1800L, null);
        AuthRefreshResult result = new AuthRefreshResult("刷新成功", token);

        assertThat(result.message()).isEqualTo("刷新成功");
        assertThat(result.token()).isEqualTo(token);
    }

    @Test
    void shouldCreateRecordWithNullToken() {
        AuthRefreshResult result = new AuthRefreshResult("刷新失败", null);

        assertThat(result.message()).isEqualTo("刷新失败");
        assertThat(result.token()).isNull();
    }

    @Test
    void shouldSupportEquality() {
        TokenResponse token = new TokenResponse("access", "refresh", "Bearer", 300L, 1800L, null);
        AuthRefreshResult result1 = new AuthRefreshResult("成功", token);
        AuthRefreshResult result2 = new AuthRefreshResult("成功", token);

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        TokenResponse token = new TokenResponse("access", "refresh", "Bearer", 300L, 1800L, null);
        AuthRefreshResult result = new AuthRefreshResult("成功", token);

        assertThat(result.toString()).contains("成功");
    }
}
