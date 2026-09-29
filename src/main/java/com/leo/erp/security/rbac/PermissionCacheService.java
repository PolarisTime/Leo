package com.leo.erp.security.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 登录用户权限集合的 Redis 缓存。
 *
 * <p>背景：{@link RoleBasedAuthorityProvider} 原先每个已认证请求都实时查询
 * {@code sys_role_permission} + {@code sys_role} + {@code sys_user_role} 三表 join，
 * 只为拿到一份变更极低频的权限码集合。本类把它缓存起来。</p>
 *
 * <p><strong>失效策略（主动失效 + 版本号纪元）：</strong>缓存 key 中带有一个全局
 * <em>权限版本号（epoch）</em>：</p>
 * <pre>auth:perm:{epoch}:{userId}:{credentialVersion}</pre>
 *
 * <p>任何 RBAC 写入提交后调用 {@link #invalidateAll()}，把 epoch 换成全新随机值，
 * 于是所有既有缓存 key 立即不可达，随后自然过期回收。</p>
 *
 * <p>之所以用「换纪元」而不是「精确删除受影响用户的 key」：改一个角色的权限时，
 * 必须反查 {@code sys_user_role} 枚举该角色下全部用户再逐个删除，漏掉任何一个都会
 * 造成<strong>权限收不回来的安全问题</strong>。换纪元无需枚举任何用户，从机制上杜绝漏删。</p>
 *
 * <p><strong>正确性约束：</strong>epoch 必须在<em>事务提交之后</em>更换。若在提交前更换，
 * 并发读者可能以「新 epoch + 未提交的旧数据」写入缓存，之后该条目再也不会失效，
 * 从而读到一个数据库中并不存在的权限集合。</p>
 *
 * <p>缓存 key 还带有 {@code credentialVersion}：凭据变更后旧条目自动不可命中，
 * 无需额外失效逻辑。</p>
 */
@Slf4j
@Service
public class PermissionCacheService {

    /** 权限版本号（纪元）key；无 TTL，仅在 Redis 数据丢失时按随机值重建。 */
    private static final String EPOCH_KEY = "auth:perm:epoch";

    private static final String CACHE_KEY_PREFIX = "auth:perm:";

    private static final TypeReference<List<String>> CODES_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonCacheSupport cacheSupport;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RedisTuningProperties redisTuningProperties;

    public PermissionCacheService(StringRedisTemplate redisTemplate,
                                  RedisJsonCacheSupport cacheSupport,
                                  AfterCommitExecutor afterCommitExecutor,
                                  RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheSupport = cacheSupport;
        this.afterCommitExecutor = afterCommitExecutor;
        this.redisTuningProperties = redisTuningProperties;
    }

    /** 读取缓存的权限码集合；未命中或出现任何异常时返回空，由调用方回源查询。 */
    public Optional<List<String>> get(Long userId, long credentialVersion) {
        if (userId == null || userId <= 0L) {
            return Optional.empty();
        }
        try {
            String epoch = currentEpoch();
            return cacheSupport.read(cacheKey(epoch, userId, credentialVersion), CODES_TYPE);
        } catch (RuntimeException ex) {
            log.warn("权限缓存读取失败，降级为直查数据库: userId={}, err={}", userId, ex.toString());
            return Optional.empty();
        }
    }

    /** 写入权限码集合缓存。空集合同样缓存，避免无角色用户反复回源。 */
    public void put(Long userId, long credentialVersion, Collection<String> codes) {
        if (userId == null || userId <= 0L || codes == null) {
            return;
        }
        try {
            String epoch = currentEpoch();
            cacheSupport.write(
                    cacheKey(epoch, userId, credentialVersion),
                    List.copyOf(codes),
                    redisTuningProperties.permissionTtl()
            );
        } catch (RuntimeException ex) {
            log.warn("权限缓存写入失败，忽略并继续: userId={}, err={}", userId, ex.toString());
        }
    }

    /**
     * 主动失效全部权限缓存：事务提交后把 epoch 换成全新随机值。
     *
     * <p>所有 RBAC 写入路径（角色增删改、角色状态变更、角色权限替换、用户角色替换）都必须调用。</p>
     */
    public void invalidateAll() {
        afterCommitExecutor.run(this::rotateEpoch);
    }

    private void rotateEpoch() {
        try {
            redisTemplate.opsForValue().set(EPOCH_KEY, newEpochToken());
        } catch (RuntimeException ex) {
            // 失效失败不能影响业务写入；TTL 兜底会在 permissionTtl 后自然过期
            log.warn("权限缓存版本号刷新失败，依赖 TTL 兜底过期: err={}", ex.toString());
        }
    }

    /**
     * 取当前 epoch；缺失时按随机值重建。
     *
     * <p>必须用随机值而非固定初值（如 0 或 INCR 得到的 1）：若 epoch key 被内存策略淘汰而
     * 缓存条目仍在，固定初值会让历史条目重新变得可命中，读到过期权限。</p>
     */
    private String currentEpoch() {
        String epoch = redisTemplate.opsForValue().get(EPOCH_KEY);
        if (epoch != null && !epoch.isBlank()) {
            return epoch;
        }
        String generated = newEpochToken();
        Boolean created = redisTemplate.opsForValue().setIfAbsent(EPOCH_KEY, generated);
        if (Boolean.TRUE.equals(created)) {
            return generated;
        }
        String existing = redisTemplate.opsForValue().get(EPOCH_KEY);
        return existing != null && !existing.isBlank() ? existing : generated;
    }

    private String newEpochToken() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE));
    }

    private String cacheKey(String epoch, Long userId, long credentialVersion) {
        return CACHE_KEY_PREFIX + epoch + ":" + userId + ":" + credentialVersion;
    }
}
