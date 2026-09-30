package com.leo.erp.auth.service;

import com.leo.erp.auth.api.SessionInvalidatedEvent;
import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.AuthorityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发令牌链路守护测试（P1，2026-09-30 审计报告发现 2）。
 *
 * <p>{@code issueTokens} 曾经无条件 {@code userAccountRepository.save(user)}：当前 0 SQL，
 * 但只要有人给 user 加任意 setter，就会变成整行 {@code update ... version=?}——覆盖同事务刚写入的
 * 审计值并复活并发 409。P1 已删除该调用并让本服务彻底不持有 {@code UserAccountRepository}；
 * 对「登录成功路径不产生 save」的交互级断言见 {@code LoginServiceTest}（其中 issueTokens 真实执行）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenIssuanceServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private SessionManagementService sessionManagementService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuthorityProvider authorityProvider;

    private TokenIssuanceService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new TokenIssuanceService(
                jwtTokenService, sessionManagementService, eventPublisher, authorityProvider);

        user = new UserAccount();
        user.setId(11L);
        user.setLoginName("alice");
        user.setUserName("Alice");
        user.setCredentialVersion(3L);
        user.setVersion(7L);
        user.setLastLoginDate(LocalDateTime.of(2026, 9, 30, 8, 0, 0));

        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(9001L);
        session.setUserId(11L);
        session.setTokenId("session-001");
        session.setCredentialVersion(3L);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(sessionManagementService.newSessionTokenId()).thenReturn("session-001");
        when(sessionManagementService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(sessionManagementService.createSession(
                eq(11L), eq("session-001"), eq("raw-refresh-token"), any(), any())).thenReturn(session);
        when(jwtTokenService.generateAccessToken(any(), eq("session-001"))).thenReturn("access-token");
        when(jwtTokenService.getAccessExpirationMs()).thenReturn(600_000L);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(authorityProvider.authoritiesFor(any())).thenReturn(List.of("sys:user:read"));
    }

    @Test
    void serviceNeverHoldsUserAccountRepository() {
        // 结构守护：issueTokens 一旦重新注入 UserAccountRepository，就重新打开 sys_user 的
        // 整行 save 入口——本断言迫使改动者显式重审 P1 结论
        assertThat(TokenIssuanceService.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .doesNotContain(UserAccountRepository.class));
        assertThat(List.of(TokenIssuanceService.class.getDeclaredFields()).stream().map(Field::getType).toList())
                .doesNotContain(UserAccountRepository.class);
    }

    @Test
    void issueTokens_doesNotMutateUserAccountEntity() {
        LocalDateTime lastLoginDate = user.getLastLoginDate();
        Long version = user.getVersion();

        TokenResponse response = service.issueTokens(user, "127.0.0.1", "junit-agent");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().permissions()).containsExactly("sys:user:read");
        // 任何 setter 脏化实体都会让 Hibernate 在提交时整行回写 sys_user：此处必须原封不动
        assertThat(user.getLastLoginDate()).isEqualTo(lastLoginDate);
        assertThat(user.getVersion()).isEqualTo(version);
        verify(eventPublisher).publishEvent(any(SessionInvalidatedEvent.class));
    }
}
