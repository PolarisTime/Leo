package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BusinessCreateIdResolverTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        DataScopeContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldGenerateSnowflakeIdWhenNoRequestContext() {
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.id()).isPositive();
        assertThat(result.preallocatedModuleKey()).isNull();
    }

    @Test
    void shouldRejectMissingSnowflakeIdGenerator() {
        assertThatThrownBy(() -> new BusinessCreateIdResolver(null, null, getClass()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SnowflakeIdGenerator");
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
    void shouldConsumeReservedPreallocatedIdAfterCommitWhenSynchronizationActive() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());
        CreateEntityId createEntityId = new CreateEntityId(123456789L, "sales-order");
        TransactionSynchronizationManager.initSynchronization();

        resolver.consumeAfterCommit(createEntityId);

        verify(preallocationService, never()).consume("sales-order", 123456789L);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();
        verify(preallocationService).consume("sales-order", 123456789L);
    }

    @Test
    void shouldIgnoreConsumeFailureAfterCommit() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        doThrow(new IllegalStateException("already consumed"))
                .when(preallocationService).consume("sales-order", 123456789L);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        resolver.consumeAfterCommit(new CreateEntityId(123456789L, "sales-order"));

        verify(preallocationService).consume("sales-order", 123456789L);
    }

    @Test
    void shouldNotConsumeWhenCreateEntityIdIsNull() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        resolver.consumeAfterCommit(null);

        verify(preallocationService, never()).consume(any(), anyLong());
    }

    @Test
    void shouldNotConsumeWhenPreallocationServiceIsMissing() {
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        resolver.consumeAfterCommit(new CreateEntityId(123L, "sales-order"));
    }

    @Test
    void shouldNotConsumeWhenCreateEntityIdHasNoModuleKey() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        resolver.consumeAfterCommit(new CreateEntityId(123L, null));

        verify(preallocationService, never()).consume(eq("sales-order"), eq(123L));
    }

    @Test
    void shouldResolveModuleKeyFromRequestUriWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/sales-orders/123");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("sales-order");
        verify(preallocationService).assertReservedByPrincipal(eq("sales-order"), eq(123456789L), any(SecurityPrincipal.class));
    }

    @Test
    void shouldResolveKnownModuleKeyFromRequestUriWithoutApiPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("purchase-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("purchase-order");
    }

    @Test
    void shouldReturnRawSegmentWhenUriDoesNotMatchKnownModule() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/custom-resources/1");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("custom-resources");
    }

    @Test
    void shouldResolveMenuAliasFromBusinessModuleHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "material-categories");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("material-categories");
        verify(preallocationService).assertReservedByPrincipal(eq("material-categories"), eq(123456789L), any(SecurityPrincipal.class));
    }

    @Test
    void shouldRejectPreallocatedIdWhenPrincipalMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldRejectPreallocatedIdWhenPrincipalTypeIsUnsupported() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldRejectBusinessModuleHeaderWhenCurrentResourceDiffers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DataScopeContext.set(1L, "purchase-order", ResourcePermissionCatalog.SCOPE_ALL);
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模块与当前接口不匹配");
    }

    @Test
    void shouldAllowBusinessModuleHeaderWhenCurrentResourceMatchesAlias() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "material-category");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DataScopeContext.set(1L, "material", ResourcePermissionCatalog.SCOPE_ALL);
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("material-category");
        verify(preallocationService).assertReservedByPrincipal(eq("material-category"), eq(123456789L), any(SecurityPrincipal.class));
    }

    @Test
    void shouldRejectBlankUriWhenModuleHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
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

    @Test
    void shouldRejectPreallocatedIdWhenUriMissingAndHeaderAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(
                new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
    }

    @Test
    void shouldRejectPreallocatedIdWhenRequestContextDisappearsBeforeModuleResolution() {
        MockHttpServletRequest request = new ClearingPreallocatedIdHeaderRequest();
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(
                new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
    }

    @Test
    void shouldRejectNullUriWhenModuleHeaderMissing() {
        MockHttpServletRequest request = new NullUriRequest();
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(
                new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
    }

    @Test
    void shouldRejectApiPrefixWithoutModuleSegment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(
                new SnowflakeIdGenerator(0L), mock(BusinessPreallocationService.class), getClass());

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
    }

    @Test
    void shouldRejectNullRestCollectionModuleSegment() {
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), null, getClass());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                resolver, "normalizeRestCollectionModuleKey", (Object) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别当前请求模块");
    }

    @Test
    void shouldResolvePluralSModuleKeyAndKeepKnownMenuCodesFromUri() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        setupAdminPrincipal();

        MockHttpServletRequest categoryRequest = new MockHttpServletRequest();
        categoryRequest.setRequestURI("/api/material-categories/1");
        categoryRequest.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(categoryRequest));
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("material-categories");

        MockHttpServletRequest customerRequest = new MockHttpServletRequest();
        customerRequest.setRequestURI("/api/customers/1");
        customerRequest.addHeader("X-Preallocated-Id", "987654321");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customerRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("customer");
    }

    @Test
    void shouldResolveAdditionalRestCollectionModuleKeyShapes() {
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());
        setupAdminPrincipal();

        MockHttpServletRequest settingsRequest = new MockHttpServletRequest();
        settingsRequest.setRequestURI("/api/company-settings/1");
        settingsRequest.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(settingsRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("company-setting");

        MockHttpServletRequest knownIesRequest = new MockHttpServletRequest();
        knownIesRequest.setRequestURI("/api/api-keies/1");
        knownIesRequest.addHeader("X-Preallocated-Id", "2233445566");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(knownIesRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("api-key");

        MockHttpServletRequest unknownIesRequest = new MockHttpServletRequest();
        unknownIesRequest.setRequestURI("/api/companies/1");
        unknownIesRequest.addHeader("X-Preallocated-Id", "987654321");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(unknownIesRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("companies");

        MockHttpServletRequest shortIesRequest = new MockHttpServletRequest();
        shortIesRequest.setRequestURI("/api/ies/1");
        shortIesRequest.addHeader("X-Preallocated-Id", "9988776655");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(shortIesRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("ies");

        MockHttpServletRequest singleLetterRequest = new MockHttpServletRequest();
        singleLetterRequest.setRequestURI("/api/s/1");
        singleLetterRequest.addHeader("X-Preallocated-Id", "1122334455");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(singleLetterRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("s");

        MockHttpServletRequest rawSegmentRequest = new MockHttpServletRequest();
        rawSegmentRequest.setRequestURI("/api/custom-resource/1");
        rawSegmentRequest.addHeader("X-Preallocated-Id", "3344556677");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(rawSegmentRequest));

        assertThat(resolver.resolve().preallocatedModuleKey()).isEqualTo("custom-resource");
    }

    @Test
    void shouldAllowBusinessModuleHeaderWhenCurrentResourceContextIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DataScopeContext.set(1L, " ", ResourcePermissionCatalog.SCOPE_ALL);
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("sales-order");
        verify(preallocationService).assertReservedByPrincipal(eq("sales-order"), eq(123456789L), any(SecurityPrincipal.class));
    }

    @Test
    void shouldAllowBusinessModuleHeaderWhenCurrentResourceContextIsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DataScopeContext.set(1L, null, ResourcePermissionCatalog.SCOPE_ALL);
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("sales-order");
        verify(preallocationService).assertReservedByPrincipal(eq("sales-order"), eq(123456789L), any(SecurityPrincipal.class));
    }

    @Test
    void shouldAllowUnknownBusinessModuleHeaderWhenCurrentResourceMatches() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "custom-module");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DataScopeContext.set(1L, "custom-module", ResourcePermissionCatalog.SCOPE_ALL);
        setupAdminPrincipal();
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        BusinessCreateIdResolver resolver = new BusinessCreateIdResolver(new SnowflakeIdGenerator(0L), preallocationService, getClass());

        CreateEntityId result = resolver.resolve();

        assertThat(result.preallocatedModuleKey()).isEqualTo("custom-module");
        verify(preallocationService).assertReservedByPrincipal(eq("custom-module"), eq(123456789L), any(SecurityPrincipal.class));
    }

    private void setupAdminPrincipal() {
        var principal = new SecurityPrincipal(
                1L, "admin", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static final class ClearingPreallocatedIdHeaderRequest extends MockHttpServletRequest {

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            if ("X-Preallocated-Id".equals(name)) {
                RequestContextHolder.resetRequestAttributes();
            }
            return value;
        }
    }

    private static final class NullUriRequest extends MockHttpServletRequest {

        @Override
        public String getRequestURI() {
            return null;
        }
    }
}
