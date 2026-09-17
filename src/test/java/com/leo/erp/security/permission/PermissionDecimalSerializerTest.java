package com.leo.erp.security.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字段级权限序列化收敛测试：无权限时金额字段输出 null，具备权限（含资源通配）时正常输出。
 */
class PermissionDecimalSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private record Sample(
            @PermissionField("sales-orders:read:amount")
            @JsonSerialize(using = PermissionDecimalSerializer.class)
            BigDecimal amount) {
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... permissions) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tester", null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void masksAmountWhenPermissionMissing() throws Exception {
        authenticate("sales-orders:read");

        assertThat(mapper.writeValueAsString(new Sample(new BigDecimal("100.00"))))
                .isEqualTo("{\"amount\":null}");
    }

    @Test
    void masksAmountWhenAnonymous() throws Exception {
        assertThat(mapper.writeValueAsString(new Sample(new BigDecimal("100.00"))))
                .isEqualTo("{\"amount\":null}");
    }

    @Test
    void exposesAmountWhenPermissionGranted() throws Exception {
        authenticate("sales-orders:read:amount");

        assertThat(mapper.writeValueAsString(new Sample(new BigDecimal("100.00"))))
                .isEqualTo("{\"amount\":100.00}");
    }

    @Test
    void exposesAmountWhenResourceWildcardGranted() throws Exception {
        authenticate("sales-orders:*");

        assertThat(mapper.writeValueAsString(new Sample(new BigDecimal("100.00"))))
                .isEqualTo("{\"amount\":100.00}");
    }

    @Test
    void exposesAmountWhenGlobalWildcardGranted() throws Exception {
        authenticate("*");

        assertThat(mapper.writeValueAsString(new Sample(new BigDecimal("100.00"))))
                .isEqualTo("{\"amount\":100.00}");
    }
}
