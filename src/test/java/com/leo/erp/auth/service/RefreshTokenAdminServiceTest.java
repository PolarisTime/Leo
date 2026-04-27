package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.RefreshTokenAdminResponse;
import com.leo.erp.auth.web.dto.RefreshTokenSessionSummaryResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenAdminServiceTest {

    @Test
    void shouldReturnOnlineFlagAndLastActiveAt() {
        RefreshTokenSession session = session(1L, 101L, "sid-1");
        LocalDateTime lastActiveAt = LocalDateTime.now().minusSeconds(20);

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(session)),
                userAccountRepository(user(101L, "admin", "管理员")),
                blacklistService(),
                sessionActivityService(Map.of("sid-1", lastActiveAt)),
                afterCommitExecutor()
        );

        RefreshTokenAdminResponse record = service.pageWithUserInfo(PageQuery.of(0, 20, null, null), null)
                .getContent()
                .getFirst();

        assertThat(record.online()).isTrue();
        assertThat(record.lastActiveAt()).isEqualTo(lastActiveAt);
        assertThat(record.loginName()).isEqualTo("admin");
    }

    @Test
    void shouldCountOnlineUsersAndSessions() {
        RefreshTokenSession first = session(1L, 101L, "sid-1");
        RefreshTokenSession second = session(2L, 101L, "sid-2");
        RefreshTokenSession third = session(3L, 202L, "sid-3");

        RefreshTokenAdminService service = new RefreshTokenAdminService(
                refreshTokenRepository(List.of(first, second, third)),
                userAccountRepository(user(101L, "admin", "管理员"), user(202L, "sale", "销售")),
                blacklistService(),
                sessionActivityService(Map.of(
                        "sid-1", LocalDateTime.now().minusSeconds(10),
                        "sid-3", LocalDateTime.now().minusSeconds(30)
                )),
                afterCommitExecutor()
        );

        RefreshTokenSessionSummaryResponse summary = service.summary();

        assertThat(summary.activeSessions()).isEqualTo(3);
        assertThat(summary.onlineSessions()).isEqualTo(2);
        assertThat(summary.onlineUsers()).isEqualTo(2);
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
                    case "findByDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter" -> sessions;
                    case "toString" -> "RefreshTokenSessionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository userAccountRepository(UserAccount... users) {
        Map<Long, UserAccount> userMap = java.util.Arrays.stream(users).collect(java.util.stream.Collectors.toMap(UserAccount::getId, item -> item));
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
        return new AccessTokenBlacklistService(null, null) {
        };
    }

    private SessionActivityService sessionActivityService(Map<String, LocalDateTime> activityMap) {
        return new SessionActivityService(null) {
            @Override
            public Map<String, LocalDateTime> resolveLastActiveAt(java.util.Collection<String> sessionIds) {
                return activityMap;
            }
        };
    }

    private AfterCommitExecutor afterCommitExecutor() {
        return new AfterCommitExecutor() {
            @Override
            public void run(Runnable action) {
                if (action != null) {
                    action.run();
                }
            }
        };
    }

    private RefreshTokenSession session(Long id, Long userId, String tokenId) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTokenId(tokenId);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        return session;
    }

    private UserAccount user(Long id, String loginName, String userName) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setLoginName(loginName);
        user.setUserName(userName);
        user.setStatus(UserStatus.NORMAL);
        return user;
    }
}
