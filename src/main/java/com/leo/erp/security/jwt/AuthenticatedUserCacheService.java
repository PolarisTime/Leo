package com.leo.erp.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class AuthenticatedUserCacheService {

    private static final String USER_CACHE_PREFIX = "auth:user:snapshot:";
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingService userRoleBindingService;

    public AuthenticatedUserCacheService(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         UserAccountRepository userAccountRepository,
                                         UserRoleBindingService userRoleBindingService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingService = userRoleBindingService;
    }

    public Optional<SecurityPrincipal> getActivePrincipal(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        String cacheKey = cacheKey(userId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            Optional<SecurityPrincipal> principal = parseCachedPrincipal(cacheKey, cached);
            if (principal.isPresent()) {
                return principal;
            }
        }

        return loadAndCachePrincipal(userId, cacheKey);
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(cacheKey(userId));
    }

    public void evictAll() {
        var keys = redisTemplate.keys(USER_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Optional<SecurityPrincipal> parseCachedPrincipal(String cacheKey, String cached) {
        try {
            CachedAuthenticatedUser snapshot = objectMapper.readValue(cached, CachedAuthenticatedUser.class);
            return Optional.of(snapshot.toPrincipal());
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    private Optional<SecurityPrincipal> loadAndCachePrincipal(Long userId, String cacheKey) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .filter(user -> user.getStatus() == UserStatus.NORMAL)
                .map(this::toSnapshot)
                .map(snapshot -> {
                    writeSnapshot(cacheKey, snapshot);
                    return snapshot.toPrincipal();
                });
    }

    private CachedAuthenticatedUser toSnapshot(UserAccount user) {
        Collection<? extends GrantedAuthority> authorities = userRoleBindingService.toGrantedAuthorities(
                userRoleBindingService.resolveRolesForUser(user.getId())
        );
        List<String> authorityValues = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .distinct()
                .toList();
        return new CachedAuthenticatedUser(
                user.getId(),
                user.getLoginName(),
                authorityValues,
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getRequireTotpSetup())
        );
    }

    private void writeSnapshot(String cacheKey, CachedAuthenticatedUser snapshot) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(snapshot), USER_CACHE_TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("认证用户缓存序列化失败", ex);
        }
    }

    private String cacheKey(Long userId) {
        return USER_CACHE_PREFIX + userId;
    }

    private record CachedAuthenticatedUser(
            Long userId,
            String loginName,
            List<String> authorities,
            boolean totpEnabled,
            boolean forceTotpSetup
    ) {

        private SecurityPrincipal toPrincipal() {
            return SecurityPrincipal.authenticated(
                    userId,
                    loginName,
                    authorities.stream()
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .toList(),
                    totpEnabled,
                    forceTotpSetup
            );
        }
    }
}
