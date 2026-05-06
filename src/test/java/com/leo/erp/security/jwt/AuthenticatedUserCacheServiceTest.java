package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserCacheServiceTest {

    @Test
    void shouldReturnPrincipalFromCacheWithoutQueryingRepository() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(Map.of(
                "auth:user:snapshot:1",
                "{\"userId\":1,\"loginName\":\"leo\",\"authorities\":[\"ROLE_ADMIN\"],\"totpEnabled\":true,\"forceTotpSetup\":false}"
        ));
        AtomicBoolean repositoryQueried = new AtomicBoolean(false);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> {
                    repositoryQueried.set(true);
                    return Optional.empty();
                }),
                roleBindingService(List.of())
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(1L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getUsername()).isEqualTo("leo");
        assertThat(principal.orElseThrow().getPassword()).isEmpty();
        assertThat(principal.orElseThrow().totpEnabled()).isTrue();
        assertThat(principal.orElseThrow().forceTotpSetup()).isFalse();
        assertThat(principal.orElseThrow().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
        assertThat(repositoryQueried.get()).isFalse();
    }

    @Test
    void shouldLoadAndCacheActiveUserWhenCacheMissed() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        UserAccount user = new UserAccount();
        user.setId(2L);
        user.setLoginName("ops");
        user.setStatus(UserStatus.NORMAL);
        user.setTotpEnabled(Boolean.FALSE);
        user.setRequireTotpSetup(Boolean.TRUE);

        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("系统管理员");
        role.setStatus("正常");

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of(role))
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(2L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getPassword()).isEmpty();
        assertThat(principal.orElseThrow().totpEnabled()).isFalse();
        assertThat(principal.orElseThrow().forceTotpSetup()).isTrue();
        assertThat(redisTemplate.lastSetKey).isEqualTo("auth:user:snapshot:2");
        assertThat(redisTemplate.lastSetTtl).isEqualTo(Duration.ofMinutes(2));
        assertThat(redisTemplate.values.get("auth:user:snapshot:2")).contains("\"forceTotpSetup\":true");
    }

    private UserAccountRepository repository(UserLoader loader) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> loader.load((Long) args[0]);
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleBindingService roleBindingService(List<RoleSetting> roles) {
        return new UserRoleBindingService(null, null, null) {
            @Override
            public List<RoleSetting> resolveRolesForUser(Long userId) {
                return roles;
            }

            @Override
            public List<org.springframework.security.core.GrantedAuthority> toGrantedAuthorities(java.util.Collection<RoleSetting> ignored) {
                return roles.stream()
                        .<org.springframework.security.core.GrantedAuthority>map(
                                role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode())
                        )
                        .toList();
            }
        };
    }

    @FunctionalInterface
    private interface UserLoader {
        Optional<UserAccount> load(Long userId);
    }

    private static final class StubRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values;
        private final ValueOperations<String, String> valueOperations;
        private String lastSetKey;
        private Duration lastSetTtl;

        private StubRedisTemplate(Map<String, String> values) {
            this.values = values;
            this.valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> this.values.get((String) args[0]);
                        case "set" -> {
                            this.lastSetKey = (String) args[0];
                            this.values.put((String) args[0], (String) args[1]);
                            this.lastSetTtl = args.length >= 3 && args[2] instanceof Duration duration ? duration : null;
                            yield null;
                        }
                        case "toString" -> "ValueOperationsStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        @Override
        public Boolean delete(String key) {
            values.remove(key);
            return Boolean.TRUE;
        }
    }
}
