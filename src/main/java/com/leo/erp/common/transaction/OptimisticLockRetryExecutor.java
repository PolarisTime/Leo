package com.leo.erp.common.transaction;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 有界乐观锁重试执行器：为「每次尝试」开启一个全新事务执行整段写逻辑，
 * 仅对乐观锁冲突做有限次重试，其余异常一律原样抛出；重试耗尽后抛出最后一次冲突异常，
 * 由调用方沿用既有的全局异常映射（乐观锁冲突 → 409）。
 *
 * <p><b>选型说明（方案 B：TransactionTemplate 包裹，未采用方案 A 的 AOP 切面）：</b>
 * <ol>
 *   <li>本项目源码没有任何 {@code @Aspect}，也没有显式声明 {@code spring-boot-starter-aop}
 *       （aspectjweaver 仅经 {@code spring-aspects} 传递引入）。新建切面会把并发正确性的关键路径
 *       绑在一条非显式的传递依赖与一套全新的代理顺序约定上；</li>
 *   <li>切面方案必须用 {@code @Order} 把自己显式排到 {@code TransactionInterceptor}
 *       （默认 {@code Ordered.LOWEST_PRECEEDING}）之外层，才能捕获提交阶段抛出的乐观锁异常；
 *       这一顺序依赖无法在纯单元测试中验证，只能靠 Spring 上下文测试兜底；</li>
 *   <li>{@link TransactionTemplate#execute} 把「开启事务 → 执行 → 提交」收进同一次调用，
 *       commit 阶段抛出的 {@code ObjectOptimisticLockingFailureException} 天然位于重试循环内部，
 *       无需任何顺序推断；每次尝试对应一次 {@code getTransaction}/{@code commit}，
 *       单元测试用记录型 {@code PlatformTransactionManager} 即可断言
 *       「每次重试都是全新事务」，不需要启动容器、不依赖数据库。</li>
 * </ol>
 *
 * <p><b>事务与自调用语义：</b>进入方法时若已存在活动事务（例如 {@code SalesOrderService.create()}
 * 自调用 {@code updateStatus(...)}，或调用方自带 {@code @Transactional}），则直接执行原逻辑：
 * 既不新开事务、也不重试，与改造前的参与行为逐字一致；只有顶层独立调用才会由本执行器开启事务并启用重试。
 * 因此「自调用参与外层事务」的既有语义不会因重试改造而回归。
 *
 * <p><b>异常白名单：</b>只识别 cause 链上的乐观锁异常（Spring
 * {@link OptimisticLockingFailureException} 及其子类、JPA {@link OptimisticLockException}、
 * Hibernate {@link StaleObjectStateException}）。{@code BusinessException}、
 * {@code DataIntegrityViolationException}（唯一键冲突）、{@code CannotAcquireLockException}
 * 等一律不重试。
 */
public class OptimisticLockRetryExecutor {

    /** cause 链遍历上限，防止自引用或异常环导致死循环。 */
    private static final int MAX_CAUSE_CHAIN_DEPTH = 16;

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockRetryExecutor.class);

    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final Duration backoff;

    /**
     * @param transactionTemplate 事务模板（PROPAGATION_REQUIRED）；由 Spring Boot 自动装配的 bean 注入
     * @param maxAttempts         单次调用的最大尝试次数（含首次执行）；小于等于 1 表示禁用重试
     * @param backoff             相邻两次尝试之间的退避时长，可为 {@code null}（视为不退避）
     */
    public OptimisticLockRetryExecutor(TransactionTemplate transactionTemplate,
                                       int maxAttempts,
                                       Duration backoff) {
        this.transactionTemplate = transactionTemplate;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /**
     * 在（可能被重试的）新事务中执行写逻辑。
     *
     * <p>重试循环内的每一次 {@code execute} 都会开启并提交一个独立事务：失败的尝试整体回滚、
     * 不留任何部分写入，重试则在全新事务中重新加载实体并重放整段逻辑。
     *
     * @param operation 日志用的操作名
     * @param work      整段写逻辑（必须可整体重放：读取与写入都以数据库当前提交状态为准）
     * @return 写逻辑的返回值
     */
    public <T> T execute(String operation, Supplier<T> work) {
        // 已在外层事务中：不新开事务也不重试。保持既有参与语义（自调用链、调用方 @Transactional 均不受影响），
        // 同时避免在同一个（可能已标记 rollback-only 的）事务里做无效重试。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        int attempts = Math.max(maxAttempts, 1);
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (RuntimeException ex) {
                if (!isOptimisticLockConflict(ex)) {
                    // 非乐观锁冲突（业务校验、唯一键、悲观锁等）禁止重试，原样抛出。
                    throw ex;
                }
                lastConflict = ex;
                if (attempt >= attempts) {
                    break;
                }
                log.warn("{}乐观锁冲突，进行第 {}/{} 次尝试: {}",
                        operation, attempt + 1, attempts, ex.getMessage());
                if (!sleepBeforeNextAttempt()) {
                    // 线程已被中断：放弃剩余重试，抛出最后一次冲突由上层处理。
                    break;
                }
            }
        }
        if (lastConflict == null) {
            // 循环至少会执行一次，此处不可达；仅为消除空指针歧义。
            throw new IllegalStateException(operation + " 未执行任何尝试");
        }
        throw lastConflict;
    }

    /** 仅当 cause 链上出现乐观锁异常时返回 true；深度上限防止异常环。 */
    private boolean isOptimisticLockConflict(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof StaleObjectStateException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
            depth++;
        }
        return false;
    }

    /** @return true 表示可以继续下一次尝试；false 表示线程被中断，应放弃重试 */
    private boolean sleepBeforeNextAttempt() {
        if (backoff == null || backoff.isZero() || backoff.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(backoff.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
