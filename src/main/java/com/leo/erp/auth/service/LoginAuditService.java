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
 */
@Service
public class LoginAuditService {

    /** 落库阈值：只有距上次登录超过该间隔才写，避免并发登录争抢同一行。 */
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
        if (!shouldWrite(user.getLastLoginDate(), now)) {
            return false;
        }
        // 原子 UPDATE：不修改受管实体，因此既不产生版本冲突，也不会让上下文里的旧快照被回写
        return userAccountRepository.updateLastLoginDate(user.getId(), now) > 0;
    }

    /**
     * 节流判断。
     *
     * <p>上次登录时间为空、或间隔阈值 &lt;= 0 时一律写入；
     * 时间戳出现“未来值”（例如多实例时钟漂移）时也写入，避免长期不更新。</p>
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
