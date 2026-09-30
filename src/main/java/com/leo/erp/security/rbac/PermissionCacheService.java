package com.leo.erp.security.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.security.rbac.repository.SysUserRoleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 登录用户权限集合的 Redis 缓存。
 *
 * <p>背景：{@link RoleBasedAuthorityProvider} 原先每个已认证请求都实时查询
 * {@code sys_role_permission} + {@code sys_role} + {@code sys_user_role} 三表 join，
 * 只为拿到一份变更极低频的权限码集合。本类把它缓存起来。</p>
 *
 * <p><strong>失效策略（主动失效 + 版本号纪元）：</strong>缓存 key 中带有两级纪元：</p>
 * <pre>auth:perm:{全局纪元}:{用户纪元|-}:{userId}:{credentialVersion}</pre>
 *
 * <p><strong>为什么两级纪元都必须进 key：</strong>只放用户级纪元的话，一旦某用户被按范围失效过
 * （用户级纪元已存在），全局纪元轮换就对它<strong>不再生效</strong>——而全局轮换正是
 * 「按角色反查用户失败 / 影响面过大」时的兜底路径，失效不生效等于权限收不回来（安全问题）。
 * 因此 key 必须同时携带两级纪元：用户级轮换只让该用户的 key 变化，全局轮换让所有人的 key 都变化。
 * 无用户纪元时用 {@code -} 占位，保证两种结构不会与他人 key 混淆。</p>
 *
 * <p>任何 RBAC 写入提交后调用 {@link #invalidateAll()} / {@link #invalidateUser(Long)} /
 * {@link #invalidateUsersByRole(Collection)}，把对应纪元换成全新随机值，
 * 于是既有缓存 key 立即不可达，随后自然过期回收。</p>
 *
 * <p><strong>失效范围（本次优化）：</strong>原先所有 RBAC 写入都换全局纪元，一次「改某个角色权限」
 * 会清空<strong>全部用户</strong>的缓存，代价随用户数线性放大，并让所有请求同时回源（惊群）。</p>
 *
 * <p>现在改为两级纪元：</p>
 * <ul>
 *   <li><b>用户级纪元</b> {@code auth:perm:uepoch:{userId}}：用户角色变更只失效该用户；
 *       角色权限/状态/删除则先反查该角色下的用户 id（{@code sys_user_role}），只失效这些用户。</li>
 *   <li><b>全局纪元</b> {@code auth:perm:epoch}：仍作为兜底与大规模变更使用（受影响用户过多、
 *       反查失败、或调用方明确要求全量失效时）。</li>
 * </ul>
 *
 * <p>为什么不会漏失效：一个用户的权限只可能因为两件事变化——「他所属的角色变了」
 * （走 {@link #invalidateUser}）或「他所属角色的权限/状态变了」（走
 * {@link #invalidateUsersByRole}）。两条路径都覆盖了，且都在事务提交后执行。
 * 这也是原实现不敢做精确删除的原因（漏删会变成权限收不回来的安全问题），
 * 现在用「用户级纪元」而非「精确删 key」，即使反查遗漏某个用户，也只影响那一个用户的 TTL，
 * 不会让整库缓存失效。</p>
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

    /** 全局权限版本号（纪元）key；无 TTL，仅在 Redis 数据丢失时按随机值重建。 */
    private static final String EPOCH_KEY = "auth:perm:epoch";

    /** 用户级纪元 key 前缀。 */
    private static final String USER_EPOCH_PREFIX = "auth:perm:uepoch:";

    private static final String CACHE_KEY_PREFIX = "auth:perm:";

    /**
     * 单飞等待上限：回源期间其它线程最多等这么久，超时后各自回源（宁可多查一次，不阻塞请求）。
     */
    private static final long SINGLE_FLIGHT_WAIT_MILLIS = 200L;

    /**
     * 单次角色级失效允许波及的最大用户数。
     *
     * <p>超过该值说明这是「大面积变更」，逐个失效既慢又占用 Redis 往返，
     * 直接换全局纪元更划算（也更安全，不会漏）。</p>
     */
    private static final int SCOPED_INVALIDATION_MAX_USERS = 500;

    private static final TypeReference<List<String>> CODES_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonCacheSupport cacheSupport;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RedisTuningProperties redisTuningProperties;
    private final SysUserRoleRepository userRoleRepository;

    /** 正在进行回源的用户（单飞）：避免同一个用户被并发回源 N 次。 */
    private final ConcurrentHashMap<Long, CountDownLatch> inFlightLoads = new ConcurrentHashMap<>();

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter loadCounter;
    private final Counter errorCounter;
    private final Counter singleFlightJoinCounter;
    private final Counter evictionCounter;

    public PermissionCacheService(StringRedisTemplate redisTemplate,
                                  RedisJsonCacheSupport cacheSupport,
                                  AfterCommitExecutor afterCommitExecutor,
                                  RedisTuningProperties redisTuningProperties,
                                  SysUserRoleRepository userRoleRepository,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.redisTemplate = redisTemplate;
        this.cacheSupport = cacheSupport;
        this.afterCommitExecutor = afterCommitExecutor;
        this.redisTuningProperties = redisTuningProperties;
        this.userRoleRepository = userRoleRepository;
        MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        this.hitCounter = counter(registry, "hit", "权限缓存命中次数");
        this.missCounter = counter(registry, "miss", "权限缓存未命中次数");
        this.loadCounter = counter(registry, "load", "权限集合回源数据库次数");
        this.errorCounter = counter(registry, "error", "权限缓存读写异常次数（已降级为直查数据库）");
        this.singleFlightJoinCounter = counter(registry, "single_flight_join", "回源期间合并到同一批次的等待次数");
        this.evictionCounter = counter(registry, "invalidate", "权限缓存失效操作次数");
    }

    private static Counter counter(MeterRegistry registry, String outcome, String description) {
        if (registry == null) {
            return null;
        }
        return Counter.builder("leo.cache.permission")
                .tag("outcome", outcome)
                .description(description)
                .register(registry);
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * 读取权限缓存，未命中时由 {@code loader} 回源。
     *
     * <p>把「读缓存 → 回源 → 写缓存」收敛到本方法，目的是顺带提供单飞（single-flight）：
     * 缓存刚失效时，同一用户的并发请求如果各自回源，会形成一次惊群式的重复查询
     * （实测 50 并发轮换 epoch 时回源 6 次，真实瞬时并发下更高）。</p>
     *
     * <p>纪元在方法入口<strong>只解析一次</strong>，回源后的写入复用同一份快照：若写入时重新解析，
     * 会出现「读到的是旧数据、写入却拿到失效后的新纪元」的组合——新 key 下的旧数据永远不会被
     * 下一次失效命中，成为读到过期权限的窗口。复用快照则相反：失效一旦发生，本次写入落在
     * 旧 key 上自然不可达，代价只是一次多余的回源。</p>
     */
    public List<String> getOrLoad(Long userId, long credentialVersion, Supplier<List<String>> loader) {
        Epochs epochs = resolveEpochs(userId);
        Optional<List<String>> cached = read(userId, credentialVersion, epochs);
        if (cached.isPresent()) {
            increment(hitCounter);
            return cached.get();
        }
        increment(missCounter);
        return loadWithSingleFlight(userId, credentialVersion, epochs, loader);
    }

    private List<String> loadWithSingleFlight(Long userId, long credentialVersion, Epochs epochs,
                                              Supplier<List<String>> loader) {
        CountDownLatch myLatch = new CountDownLatch(1);
        CountDownLatch existing = inFlightLoads.putIfAbsent(userId, myLatch);
        if (existing != null) {
            // 已有线程在回源：等它写完缓存后直接读缓存，避免重复查库
            increment(singleFlightJoinCounter);
            try {
                existing.await(SINGLE_FLIGHT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            Optional<List<String>> afterWait = read(userId, credentialVersion, epochs);
            if (afterWait.isPresent()) {
                increment(hitCounter);
                return afterWait.get();
            }
            // 等不到（超时或对方失败）就自己回源，保证不会因为合并而拿不到权限
            return loadAndCache(userId, credentialVersion, epochs, loader);
        }
        try {
            return loadAndCache(userId, credentialVersion, epochs, loader);
        } finally {
            inFlightLoads.remove(userId, myLatch);
            myLatch.countDown();
        }
    }

    private List<String> loadAndCache(Long userId, long credentialVersion, Epochs epochs,
                                      Supplier<List<String>> loader) {
        increment(loadCounter);
        List<String> codes = loader.get();
        write(userId, credentialVersion, epochs, codes);
        return codes == null ? List.of() : codes;
    }

    /** 读取缓存的权限码集合；未命中或出现任何异常时返回空，由调用方回源查询。 */
    public Optional<List<String>> get(Long userId, long credentialVersion) {
        if (userId == null || userId <= 0L) {
            return Optional.empty();
        }
        return read(userId, credentialVersion, resolveEpochs(userId));
    }

    private Optional<List<String>> read(Long userId, long credentialVersion, Epochs epochs) {
        try {
            return cacheSupport.read(cacheKey(epochs, userId, credentialVersion), CODES_TYPE);
        } catch (RuntimeException ex) {
            increment(errorCounter);
            log.warn("权限缓存读取失败，降级为直查数据库: userId={}, err={}", userId, ex.toString());
            return Optional.empty();
        }
    }

    /** 写入权限码集合缓存。空集合同样缓存，避免无角色用户反复回源。 */
    public void put(Long userId, long credentialVersion, Collection<String> codes) {
        if (userId == null || userId <= 0L || codes == null) {
            return;
        }
        write(userId, credentialVersion, resolveEpochs(userId), codes);
    }

    private void write(Long userId, long credentialVersion, Epochs epochs, Collection<String> codes) {
        if (userId == null || userId <= 0L || codes == null) {
            return;
        }
        try {
            // TTL 加抖动：避免同一批写入的缓存同时过期形成周期性惊群
            cacheSupport.write(
                    cacheKey(epochs, userId, credentialVersion),
                    List.copyOf(codes),
                    redisTuningProperties.withTtlJitter(redisTuningProperties.permissionTtl())
            );
        } catch (RuntimeException ex) {
            increment(errorCounter);
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

    /**
     * 只失效指定用户的权限缓存（事务提交后执行）。
     *
     * <p>用于「用户角色变更」这类影响面恰好是一个用户的写入。</p>
     */
    public void invalidateUser(Long userId) {
        if (userId == null || userId <= 0L) {
            return;
        }
        afterCommitExecutor.run(() -> rotateUserEpoch(userId));
    }

    /**
     * 只失效指定角色下用户的权限缓存（事务提交后执行）。
     *
     * <p>影响面过大（超过 {@link #SCOPED_INVALIDATION_MAX_USERS}）或反查失败时，
     * 自动退化为全局纪元轮换：宁可多失效，也不能漏失效。</p>
     */
    public void invalidateUsersByRole(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        afterCommitExecutor.run(() -> {
            Set<Long> userIds;
            try {
                userIds = new LinkedHashSet<>(userRoleRepository.findUserIdsByRoleIdIn(roleIds));
            } catch (RuntimeException ex) {
                log.warn("按角色反查用户失败，退化为全局失效: roleIds={}, err={}", roleIds, ex.toString());
                rotateEpoch();
                return;
            }
            if (userIds.size() > SCOPED_INVALIDATION_MAX_USERS) {
                log.info("角色影响用户数 {} 超过阈值 {}，退化为全局失效: roleIds={}",
                        userIds.size(), SCOPED_INVALIDATION_MAX_USERS, roleIds);
                rotateEpoch();
                return;
            }
            userIds.forEach(this::rotateUserEpoch);
        });
    }

    private void rotateUserEpoch(Long userId) {
        try {
            redisTemplate.opsForValue().set(userEpochKey(userId), newEpochToken());
            increment(evictionCounter);
        } catch (RuntimeException ex) {
            // 用户级纪元写入失败必须退化为全局失效，否则该用户会继续命中旧权限
            log.warn("用户级权限纪元刷新失败，退化为全局失效: userId={}, err={}", userId, ex.toString());
            rotateEpoch();
        }
    }

    /** 观察用：当前处于回源中的用户数量。 */
    int inFlightLoadCount() {
        return inFlightLoads.size();
    }

    /** 轮换全局纪元：让所有用户（包括已有用户级纪元的）缓存 key 一起失效。 */
    private void rotateEpoch() {
        try {
            redisTemplate.opsForValue().set(EPOCH_KEY, newEpochToken());
            increment(evictionCounter);
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

    /**
     * 一次性解析两级纪元：用 {@code MGET} 一条命令取回，保持每请求两次 Redis 往返
     * （纪元 1 次 + 缓存本体 1 次），不因引入用户级纪元而增加热路径开销。
     *
     * <p>任何 Redis 异常都在此吞掉并返回占位纪元：随后的缓存读写会同样失败并被
     * {@link #read} / {@link #write} 捕获，最终降级为直查数据库——缓存不可用不能影响鉴权。</p>
     */
    private Epochs resolveEpochs(Long userId) {
        try {
            List<String> values = redisTemplate.opsForValue()
                    .multiGet(List.of(EPOCH_KEY, userEpochKey(userId)));
            String global = values != null && !values.isEmpty() ? values.get(0) : null;
            String user = values != null && values.size() > 1 ? values.get(1) : null;
            if (global == null || global.isBlank()) {
                global = currentEpoch();
            }
            return new Epochs(global, user == null || user.isBlank() ? null : user);
        } catch (RuntimeException ex) {
            increment(errorCounter);
            log.warn("权限纪元解析失败，降级为直查数据库: userId={}, err={}", userId, ex.toString());
            // 占位纪元：该 key 下的读写会随 Redis 一起失败，不会误命中历史条目
            return new Epochs(newEpochToken(), null);
        }
    }

    private String userEpochKey(Long userId) {
        return USER_EPOCH_PREFIX + userId;
    }

    private String newEpochToken() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE));
    }

    private String cacheKey(Epochs epochs, Long userId, long credentialVersion) {
        // 两级纪元都必须在 key 里：缺一就会让对应级别的失效对该 key 静默失效（见类注释）。
        // 用户纪元缺失用 "-" 占位，避免「三段式」key 与他人「四段式」key 结构混淆。
        String userSegment = epochs.userEpoch() == null ? "-" : epochs.userEpoch();
        return CACHE_KEY_PREFIX + epochs.globalEpoch() + ":" + userSegment + ":" + userId + ":" + credentialVersion;
    }

    /** 两级纪元快照；读与写必须复用同一份快照（见 {@link #getOrLoad} 的说明）。 */
    private record Epochs(String globalEpoch, String userEpoch) {
    }
}
