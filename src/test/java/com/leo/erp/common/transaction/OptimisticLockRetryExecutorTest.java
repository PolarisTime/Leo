package com.leo.erp.common.transaction;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OptimisticLockRetryExecutor} 边界测试：重试白名单、有界次数、新事务与自调用语义。
 */
class OptimisticLockRetryExecutorTest {

    private RecordingTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void clearOuterTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private OptimisticLockRetryExecutor executor(int maxAttempts) {
        return new OptimisticLockRetryExecutor(transactionTemplate, maxAttempts, Duration.ZERO);
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException("SalesOrder", 5L);
    }

    @Test
    void execute_conflictOnFirstCommit_retriesInNewTransactionUntilSuccess() {
        transactionManager.failNextCommits(1, conflict());
        AtomicInteger invocations = new AtomicInteger();

        String result = executor(3).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        // 每次尝试都开启并提交一个全新事务：2 次尝试、1 次成功提交（首次提交抛冲突）。
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
    }

    @Test
    void execute_conflictInsideWork_retriesAndRollsBackFailedAttempt() {
        AtomicInteger invocations = new AtomicInteger();

        String result = executor(3).execute("sales-order.update", () -> {
            if (invocations.incrementAndGet() == 1) {
                // 模拟方法体内 auto-flush 阶段抛出的乐观锁冲突。
                throw conflict();
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        assertThat(transactionManager.getRollbackCount()).isEqualTo(1);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
    }

    @Test
    void execute_rethrowsOriginalConflict_whenAttemptsExhausted() {
        ObjectOptimisticLockingFailureException conflict = conflict();
        transactionManager.failNextCommits(3, conflict);
        AtomicInteger invocations = new AtomicInteger();

        // 重试耗尽后必须原样抛出最后一次乐观锁异常（→ GlobalExceptionHandler 仍映射 409）。
        assertThatThrownBy(() -> executor(3).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            return "ok";
        })).isSameAs(conflict);

        assertThat(invocations.get()).isEqualTo(3);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(3);
        assertThat(transactionManager.getCommitCount()).isZero();
    }

    @Test
    void execute_neverRetriesBusinessException() {
        BusinessException failure = new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> executor(3).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        // 校验类错误一次都不重试，且该次尝试已整体回滚。
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
        assertThat(transactionManager.getRollbackCount()).isEqualTo(1);
    }

    @Test
    void execute_neverRetriesDataIntegrityViolation() {
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> executor(3).execute("sales-order.create", () -> {
            invocations.incrementAndGet();
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        })).isInstanceOf(DataIntegrityViolationException.class);

        // 唯一键冲突禁止重试。
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
    }

    @Test
    void execute_neverRetriesPessimisticLockFailure() {
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> executor(3).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            throw new CannotAcquireLockException("timeout waiting for lock");
        })).isInstanceOf(CannotAcquireLockException.class);

        // 悲观锁/取锁超时不在本执行器的重试白名单内。
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
    }

    @Test
    void execute_detectsJakartaOptimisticLockInCauseChain() {
        AtomicInteger invocations = new AtomicInteger();

        String result = executor(3).execute("sales-order.update", () -> {
            if (invocations.incrementAndGet() == 1) {
                throw new IllegalStateException("flush failed",
                        new OptimisticLockException("Row was updated or deleted by another transaction"));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
    }

    @Test
    void execute_detectsHibernateStaleObjectStateInCauseChain() {
        AtomicInteger invocations = new AtomicInteger();

        String result = executor(3).execute("sales-order.update", () -> {
            if (invocations.incrementAndGet() == 1) {
                throw new IllegalStateException("commit failed",
                        new StaleObjectStateException("SalesOrder", 5L));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
    }

    @Test
    void execute_disabled_whenMaxAttemptsIsOne() {
        ObjectOptimisticLockingFailureException conflict = conflict();
        transactionManager.failNextCommits(1, conflict);
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> executor(1).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            return "ok";
        })).isSameAs(conflict);

        // <=1 表示禁用重试：冲突直接抛出，仅尝试一次。
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
        assertThat(transactionManager.getCommitCount()).isZero();
    }

    @Test
    void execute_disabled_whenMaxAttemptsIsZeroOrNegative() {
        transactionManager.failNextCommits(2, conflict());
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> executor(0).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            return "ok";
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> executor(-5).execute("sales-order.update", () -> {
            invocations.incrementAndGet();
            return "ok";
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
    }

    @Test
    void execute_runsInlineWithoutNewTransactionOrRetry_whenOuterTransactionActive() {
        // 模拟自调用链（create → updateStatus）或调用方自带 @Transactional 的场景。
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AtomicInteger invocations = new AtomicInteger();

        // 成功路径：直接执行，不开启任何新事务。
        String result = executor(3).execute("sales-order.updateStatus", () -> {
            invocations.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(invocations.get()).isEqualTo(1);

        // 冲突路径：保持改造前语义——原样抛出，绝不在外层事务内重试。
        assertThatThrownBy(() -> executor(3).execute("sales-order.updateStatus", () -> {
            invocations.incrementAndGet();
            throw conflict();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(invocations.get()).isEqualTo(2);
        assertThat(transactionManager.getTransactionCount()).isZero();
        assertThat(transactionManager.getCommitCount()).isZero();
    }
}
