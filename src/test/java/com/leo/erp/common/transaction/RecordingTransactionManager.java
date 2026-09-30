package com.leo.erp.common.transaction;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 记录型事务管理器：统计「开启事务 / 提交 / 回滚」次数，并可预设随后若干次 commit 抛出指定异常。
 *
 * <p>用于无数据库的单元测试中模拟真实场景里「flush/commit 阶段才抛出的乐观锁冲突」，
 * 从而断言重试执行器的每次尝试都对应一个全新的事务。
 */
public class RecordingTransactionManager implements PlatformTransactionManager {

    private final Deque<RuntimeException> commitFailures = new ArrayDeque<>();

    private int transactionCount;
    private int commitCount;
    private int rollbackCount;

    /** 预设接下来 {@code count} 次 commit 依次抛出 {@code failure}（同一实例可复用）。 */
    public void failNextCommits(int count, RuntimeException failure) {
        for (int i = 0; i < count; i++) {
            commitFailures.add(failure);
        }
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        transactionCount++;
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        RuntimeException failure = commitFailures.poll();
        if (failure != null) {
            throw failure;
        }
        commitCount++;
    }

    @Override
    public void rollback(TransactionStatus status) {
        rollbackCount++;
    }

    /** 开启事务的次数 —— 每次重试尝试都应产生一次。 */
    public int getTransactionCount() {
        return transactionCount;
    }

    /** 成功提交的次数（commit 抛出异常的尝试不计入）。 */
    public int getCommitCount() {
        return commitCount;
    }

    /** 回滚次数（执行体抛异常时由事务模板触发）。 */
    public int getRollbackCount() {
        return rollbackCount;
    }
}
