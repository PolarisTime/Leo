package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BusinessCreateIdResolverTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldGenerateSnowflakeIdWhenNoRequestContext() {
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.id()).isPositive();
        assertThat(result.preallocatedModuleKey()).isNull();
    }

    @Test
    void shouldUsePreallocatedIdWithoutConsumeService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.id()).isEqualTo(123456789L);
        assertThat(result.preallocatedModuleKey()).isNull();
    }

    @Test
    void shouldAssertAndConsumeReservedPreallocatedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();
        resolver.consumeAfterCommit(result);

        assertThat(result.id()).isEqualTo(123456789L);
        assertThat(result.preallocatedModuleKey()).isEqualTo("sales-order");
        verify(preallocationService).assertReservedByPrincipal(eq("sales-order"), eq(123456789L), any(SecurityPrincipal.class));
        verify(preallocationService).consume("sales-order", 123456789L);
    }

    @Test
    void shouldNotConsumeWhenCreateEntityIdHasNoModuleKey() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        resolver.consumeAfterCommit(new CreateEntityId(123L, null));

        verify(preallocationService, never()).consume(eq("sales-order"), eq(123L));
    }

    @Test
    void shouldThrowWhenPreallocatedIdInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "not-a-number");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void shouldIgnoreBlankPreallocatedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.id()).isPositive();
        assertThat(result.preallocatedModuleKey()).isNull();
    }

    @Test
    void shouldIgnoreNegativePreallocatedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "-5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.id()).isPositive();
        assertThat(result.preallocatedModuleKey()).isNull();
    }

    private void setupAdminPrincipal() {
        var principal = new SecurityPrincipal(
                1L, "admin", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
