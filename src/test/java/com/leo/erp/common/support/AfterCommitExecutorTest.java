package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor = new AfterCommitExecutor();

    @Test
    void shouldRunImmediatelyWhenNoActiveTransaction() {
        AtomicBoolean executed = new AtomicBoolean(false);

        executor.run(() -> executed.set(true));

        assertThat(executed).isTrue();
    }

    @Test
    void shouldNotFailWhenActionIsNull() {
        executor.run(null);
    }

    @Test
    void shouldRegisterSynchronizationWhenTransactionActive() {
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            AtomicBoolean executed = new AtomicBoolean(false);

            executor.run(() -> executed.set(true));

            assertThat(executed).isFalse();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCommit();

            assertThat(executed).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
