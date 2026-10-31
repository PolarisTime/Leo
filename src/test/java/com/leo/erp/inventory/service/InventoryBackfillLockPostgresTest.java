package com.leo.erp.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存余额重建与记账互斥的真实 PostgreSQL 回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁协议：记账/软删事务取 {@code inv:backfill} 的<b>事务级共享锁</b>
 * （{@code pg_advisory_xact_lock_shared}），余额重建取同一键的<b>事务级排他锁</b>
 * （{@code pg_advisory_xact_lock}）。两个方向都必须互斥，否则重建的覆盖写会吞掉并发记账。</p>
 *
 * <p>用例用两个真实事务（{@code PROPAGATION_REQUIRES_NEW}，各自独立连接）验证：
 * 共享锁持有期间排他锁拿不到；排他锁持有期间共享锁拿不到；释放后立即获得。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class InventoryBackfillLockPostgresTest {

    private static final long LOCK_WAIT_MILLIS = 800L;
    private static final long LOCK_TIMEOUT_SECONDS = 10L;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private InventoryTransactionLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new InventoryTransactionLockService(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void rebuildExclusiveLock_shouldWaitUntilRecordingSharedLockCommits() throws Exception {
        CountDownLatch sharedLockHeld = new CountDownLatch(1);
        CountDownLatch releaseSharedLock = new CountDownLatch(1);

        Thread recordingThread = new Thread(() -> newTransaction().executeWithoutResult(status -> {
            lockService.lockBackfillSharedTxScoped();
            sharedLockHeld.countDown();
            await(releaseSharedLock);
        }), "recording-shared-lock");
        recordingThread.start();

        assertThat(sharedLockHeld.await(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("记账事务应能先取得 inv:backfill 共享锁")
                .isTrue();

        ExecutorService builder = Executors.newSingleThreadExecutor();
        try {
            Future<?> rebuild = builder.submit(() -> newTransaction().executeWithoutResult(status ->
                    lockService.lockBackfillTxScoped()));

            // 共享锁未释放前，重建的排他锁必须阻塞（覆盖写不能进入）。
            assertThatThrownBy(() -> rebuild.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseSharedLock.countDown();
            // 记账提交后，重建应立即获得排他锁。
            rebuild.get(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            builder.shutdownNow();
            releaseSharedLock.countDown();
            recordingThread.join(TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS));
        }
        assertThat(recordingThread.isAlive()).isFalse();
    }

    @Test
    void recordingSharedLock_shouldWaitUntilRebuildExclusiveLockCommits() throws Exception {
        CountDownLatch exclusiveLockHeld = new CountDownLatch(1);
        CountDownLatch releaseExclusiveLock = new CountDownLatch(1);

        Thread rebuildThread = new Thread(() -> newTransaction().executeWithoutResult(status -> {
            lockService.lockBackfillTxScoped();
            exclusiveLockHeld.countDown();
            await(releaseExclusiveLock);
        }), "rebuild-exclusive-lock");
        rebuildThread.start();

        assertThat(exclusiveLockHeld.await(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("重建事务应能先取得 inv:backfill 排他锁")
                .isTrue();

        ExecutorService recorder = Executors.newSingleThreadExecutor();
        try {
            Future<?> recording = recorder.submit(() -> newTransaction().executeWithoutResult(status ->
                    lockService.lockBackfillSharedTxScoped()));

            // 重建未提交前，记账的共享锁必须阻塞（不能读到将被覆盖的中间态）。
            assertThatThrownBy(() -> recording.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseExclusiveLock.countDown();
            recording.get(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            recorder.shutdownNow();
            releaseExclusiveLock.countDown();
            rebuildThread.join(TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS));
        }
        assertThat(rebuildThread.isAlive()).isFalse();
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待锁释放超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("被中断", ex);
        }
    }
}
