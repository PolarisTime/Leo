package com.leo.erp.auth.service;

import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.RefreshTokenAdminResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenAdminServiceExtendedTest {

    @Test
    void shouldRevokeActiveTokenSuccessfully() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        session.setRevokedAt(null);

        List<RefreshTokenSession> stored = new ArrayList<>(List.of(session));
        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(stored),
                userAccountRepository(),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        service.revoke(1L);

        assertThat(stored.get(0).getRevokedAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenRevokingNonExistentToken() {
        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of()),
                userAccountRepository(),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        assertThatThrownBy(() -> service.revoke(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("令牌不存在");
    }

    @Test
    void shouldThrowWhenRevokingAlreadyRevokedToken() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        session.setRevokedAt(LocalDateTime.now().minusHours(1));

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        assertThatThrownBy(() -> service.revoke(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("令牌已被禁用");
    }

    @Test
    void shouldRevokeAllActiveTokensAndReturnCount() {
        RefreshTokenSession s1 = session(1L, 101L, "sid-1");
        RefreshTokenSession s2 = session(2L, 102L, "sid-2");
        RefreshTokenSession s3 = session(3L, 103L, "sid-3");
        s3.setRevokedAt(LocalDateTime.now().minusHours(1));

        List<RefreshTokenSession> stored = new ArrayList<>(List.of(s1, s2, s3));
        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(stored.stream().filter(s -> s.getRevokedAt() == null).toList()),
                userAccountRepository(),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        int count = service.revokeAll();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldSetStatusToDisabledWhenTokenIsRevoked() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        session.setRevokedAt(LocalDateTime.now().minusHours(1));

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(user(101L, "admin", "管理员")),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        RefreshTokenAdminResponse response = service.page(PageQuery.of(0, 20, null, null), null)
                .getContent().getFirst();

        assertThat(response.status()).isEqualTo("已禁用");
        assertThat(response.online()).isFalse();
    }

    @Test
    void shouldSetStatusToExpiredWhenTokenPastExpiry() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        session.setExpiresAt(LocalDateTime.now().minusHours(1));

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(user(101L, "admin", "管理员")),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        RefreshTokenAdminResponse response = service.page(PageQuery.of(0, 20, null, null), null)
                .getContent().getFirst();

        assertThat(response.status()).isEqualTo("已过期");
        assertThat(response.online()).isFalse();
    }

    @Test
    void shouldFallbackToUserIdWhenUserNotFoundInMap() {
        RefreshTokenSession session = session(1L, 999L, "sid-1");

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(),
                blacklistService(),
                sessionActivityService(Map.of()),
                afterCommitExecutor()
        );

        RefreshTokenAdminResponse response = service.page(PageQuery.of(0, 20, null, null), null)
                .getContent().getFirst();

        assertThat(response.loginName()).isEqualTo("999");
        assertThat(response.userName()).isEqualTo("--");
    }

    @Test
    void shouldMarkOnlineWhenActiveAndHasLastActivity() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        LocalDateTime lastActive = LocalDateTime.now().minusSeconds(10);

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(user(101L, "admin", "管理员")),
                blacklistService(),
                sessionActivityService(Map.of("sid-1", lastActive)),
                afterCommitExecutor()
        );

        Page<RefreshTokenAdminResponse> page = service.pageWithUserInfo(PageQuery.of(0, 20, null, null), null);
        RefreshTokenAdminResponse response = page.getContent().getFirst();

        assertThat(response.online()).isTrue();
        assertThat(response.lastActiveAt()).isEqualTo(lastActive);
    }

    @SuppressWarnings("unchecked")
    private RefreshTokenSessionRepository refreshTokenRepository(List<RefreshTokenSession> sessions) {
        return (RefreshTokenSessionRepository) Proxy.newProxyInstance(
                RefreshTokenSessionRepository.class.getClassLoader(),
                new Class[]{RefreshTokenSessionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (args.length == 2 && args[1] instanceof Pageable pageable) {
                            yield new PageImpl<>(sessions, pageable, sessions.size());
                        }
                        yield sessions;
                    }
                    case "findById" -> sessions.stream()
                            .filter(s -> s.getId().equals(args[0]))
                            .findFirst();
                    case "findByDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter" -> sessions.stream()
                            .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(LocalDateTime.now()))
                            .toList();
                    case "save" -> args[0];
                    case "saveAll" -> args[0];
                    case "toString" -> "RefreshTokenSessionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository userAccountRepository(UserAccount... users) {
        Map<Long, UserAccount> userMap = java.util.Arrays.stream(users)
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, u -> u));
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.ofNullable(userMap.get((Long) args[0]));
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private AccessTokenBlacklistService blacklistService() {
        return mock(AccessTokenBlacklistService.class);
    }

    private SessionActivityService sessionActivityService(Map<String, LocalDateTime> activityMap) {
        SessionActivityService service = mock(SessionActivityService.class);
        when(service.resolveLastActiveAt(org.mockito.ArgumentMatchers.any())).thenReturn(activityMap);
        return service;
    }

    private AfterCommitExecutor afterCommitExecutor() {
        return new AfterCommitExecutor() {
            @Override
            public void run(Runnable action) {
                if (action != null) action.run();
            }
        };
    }

    private RefreshTokenSession session(Long id, Long userId, String tokenId) {
        RefreshTokenSession s = new RefreshTokenSession();
        s.setId(id);
        s.setUserId(userId);
        s.setTokenId(tokenId);
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        return s;
    }

    private UserAccount user(Long id, String loginName, String userName) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setLoginName(loginName);
        u.setUserName(userName);
        u.setStatus(UserStatus.NORMAL);
        return u;
    }
}
