package com.leo.erp.security.totp;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.TotpService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.support.SecurityPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotpVerificationAspectTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldRejectApiKeyRequests() {
        TotpVerificationAspect aspect = new TotpVerificationAspect(mock(UserAccountRepository.class), mock(TotpService.class));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("security-key"), List.of("update")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(() -> aspect.verify(joinPoint(new AtomicBoolean(false)), requiresTotpVerification()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 不支持执行需要2FA验证的敏感操作");
    }

    @Test
    void shouldRejectWhenCurrentUserHasNotEnabledTotp() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpVerificationAspect aspect = new TotpVerificationAspect(repository, totpService);
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setLoginName("admin");
        account.setTotpEnabled(Boolean.FALSE);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account));

        SecurityContextHolder.getContext().setAuthentication(userAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TotpVerificationAspect.TOTP_HEADER_NAME, "123456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> aspect.verify(joinPoint(new AtomicBoolean(false)), requiresTotpVerification()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前账号未启用2FA");
    }

    @Test
    void shouldRejectWhenTotpCodeInvalid() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpVerificationAspect aspect = new TotpVerificationAspect(repository, totpService);
        UserAccount account = enabledAccount();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account));

        SecurityContextHolder.getContext().setAuthentication(userAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TotpVerificationAspect.TOTP_HEADER_NAME, "12");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> aspect.verify(joinPoint(new AtomicBoolean(false)), requiresTotpVerification()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请提供6位2FA验证码");
    }

    @Test
    void shouldProceedWhenTotpCodeMatches() throws Throwable {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpVerificationAspect aspect = new TotpVerificationAspect(repository, totpService);
        UserAccount account = enabledAccount();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account));
        when(totpService.decryptSecret("encrypted-secret")).thenReturn("plain-secret");
        when(totpService.verifyCode("plain-secret", "123456")).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(userAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TotpVerificationAspect.TOTP_HEADER_NAME, "123456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AtomicBoolean proceeded = new AtomicBoolean(false);

        Object result = aspect.verify(joinPoint(proceeded), requiresTotpVerification());

        assertThat(result).isNull();
        assertThat(proceeded.get()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private ProceedingJoinPoint joinPoint(AtomicBoolean proceeded) {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> {
                        proceeded.set(true);
                        yield null;
                    }
                    case "toString" -> "ProceedingJoinPointStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RequiresTotpVerification requiresTotpVerification() {
        return (RequiresTotpVerification) Proxy.newProxyInstance(
                RequiresTotpVerification.class.getClassLoader(),
                new Class[]{RequiresTotpVerification.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "annotationType" -> RequiresTotpVerification.class;
                    case "toString" -> "@RequiresTotpVerification";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        return new UsernamePasswordAuthenticationToken(principal(), null, List.of());
    }

    private SecurityPrincipal principal() {
        return new SecurityPrincipal(1L, "admin", "", true, List.of());
    }

    private UserAccount enabledAccount() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setLoginName("admin");
        account.setTotpEnabled(Boolean.TRUE);
        account.setTotpSecret("encrypted-secret");
        return account;
    }
}
