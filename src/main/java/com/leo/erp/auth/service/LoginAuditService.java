package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 登录成功后的审计写入：记录「最后登录时间」。
 *
 * <p>三条结论都是实测出来的（2026-09-30 压测报告 P0 项）：</p>
 * <ol>
 *   <li><b>必须节流。</b>这是审计字段，精确到「最近一次」即可：距上次写不足
 *       {@code leo.auth.login-audit.last-login-write-interval-seconds} 直接跳过。
 *       实测节流命中时一次登录对 {@code sys_user} 没有任何写语句，写放大归零。</li>
 *   <li><b>必须原子 UPDATE，不能改实体。</b>改 {@code user.setLastLoginDate(...)} 会让
 *       带 {@code @Version} 的实体在提交时做版本比对且失败不重试：并发登录先提交者
 *       让后者 0 行更新 → {@code ObjectOptimisticLockingFailureException}（409）。
 *       实测 20 并发登录仅 5% 成功。</li>
 *   <li><b>也不能靠「先锁行再改实体」绕过。</b>给账号行加悲观锁会把 bcrypt 与整个
 *       登录事务都压在锁里，实测 20 并发直接打满连接池（active 20/20、获取超时 19 次），
 *       失败率反而升到 88%。所以登录既不锁账号行、也不改实体，只发这一条 UPDATE。</li>
 * </ol>
 *
 * <p><b>节流是两道闸（P2）：</b>① 实体快照预判（{@link #shouldWrite}）防常态——节流命中时
 * 一条 UPDATE 都不发；② 阈值条件并入 UPDATE 的 WHERE（见
 * {@code UserAccountRepository#updateLastLoginDate}）防并发突发——快照在并发下会过期，
 * 首个事务提交前同批次全部通过预判，靠 PostgreSQL 的 EvalPlanQual 重评 WHERE 收敛到只写 1 条。</p>
 */
@Service
public class LoginAuditService {

    /** staleBefore 计算允许的最大间隔：100 年，防止极端配置使 minusSeconds 溢出或超出 PG timestamp 范围。 */
    private static final long MAX_INTERVAL_SECONDS = 100L * 365 * 24 * 60 * 60;

    /** 登录审计写入的持久化入口（登录链路唯一的 sys_user 写通道）。 */
    private final UserAccountRepository userAccountRepository;
    private final AuthProperties authProperties;

    public LoginAuditService(UserAccountRepository userAccountRepository, AuthProperties authProperties) {
        this.userAccountRepository = userAccountRepository;
        this.authProperties = authProperties == null ? new AuthProperties() : authProperties;
    }

    /**
     * 记录登录成功时间。
     *
     * @param user 已通过密码校验的账号
     * @return 是否真正落库（被节流跳过时为 false），便于测试与观测
     */
    public boolean recordSuccessfulLogin(UserAccount user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        // 第一道闸（预判防常态）：读实体加载时的快照，节流命中时连 UPDATE 都不发，省掉无谓往返；
        // 但快照在并发突发下会过期——首个事务提交前，同批次并发全部通过判断，必须靠第二道闸兜底。
        if (!shouldWrite(user.getLastLoginDate(), now)) {
            return false;
        }
        // 第二道闸（WHERE 防并发突发）：staleBefore 随 UPDATE 下发到 WHERE；PostgreSQL READ COMMITTED
        // 下后到的 UPDATE 锁行后会用最新已提交版本重评条件（EvalPlanQual），首条提交后其余并发直接跳过。
        // 原子 UPDATE：不修改受管实体，因此既不产生版本冲突，也不会让上下文里的旧快照被回写。
        return userAccountRepository.updateLastLoginDate(user.getId(), now, staleBefore(now)) > 0;
    }

    /**
     * 计算 UPDATE WHERE 用的节流界线：已提交的 {@code lastLoginDate} 严格早于该时刻才允许写入。
     *
     * <p>与 {@link #shouldWrite} 同源：阈值 &lt;= 0（每次登录都写）时把界线推到遥远未来，
     * 让 WHERE 条件恒真；正常阈值（默认 60s）即 {@code now - interval}；超大阈值钳制到 100 年，
     * 避免时间运算溢出或超出 PostgreSQL timestamp 范围。</p>
     */
    private LocalDateTime staleBefore(LocalDateTime now) {
        long intervalSeconds = writeIntervalSeconds();
        if (intervalSeconds <= 0) {
            return now.plusYears(100);
        }
        return now.minusSeconds(Math.min(intervalSeconds, MAX_INTERVAL_SECONDS));
    }

    /**
     * 节流判断（第一道闸：防常态，只看实体快照，不产生任何 SQL）。
     *
     * <p>上次登录时间为空、或间隔阈值 &lt;= 0 时一律写入；
     * 时间戳出现“未来值”（例如多实例时钟漂移）时也写入，避免长期不更新。</p>
     *
     * <p>注意与第二道闸（UPDATE WHERE）的细微差异：未来值预判放行、但 WHERE 的
     * {@code lastLoginDate < :staleBefore} 会拒绝，该值要等回落到界线前才会真正落库；
     * 属可接受的收敛（罕见的时钟漂移场景），不影响登录成功。</p>
     */
    private boolean shouldWrite(LocalDateTime lastLoginAt, LocalDateTime now) {
        long intervalSeconds = writeIntervalSeconds();
        if (intervalSeconds <= 0) {
            return true;
        }
        if (lastLoginAt == null) {
            return true;
        }
        if (lastLoginAt.isAfter(now)) {
            return true;
        }
        return Duration.between(lastLoginAt, now).getSeconds() >= intervalSeconds;
    }

    private long writeIntervalSeconds() {
        AuthProperties.LoginAudit audit = authProperties.getLoginAudit();
        return audit == null ? 0L : audit.getLastLoginWriteIntervalSeconds();
    }
}
