package com.leo.erp.common.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 集群定时任务互斥：拿不到锁必须跳过、任务异常也必须在 finally 释放锁。
 *
 * <p>回归背景：这些 {@code @Scheduled} 任务原来只用 JVM 内 {@code AtomicBoolean} 互斥，
 * 多实例部署时行情抓取、清单导出、分区清理会重复执行。</p>
 */
class ClusterScheduleLockTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final ClusterScheduleLock lock = new ClusterScheduleLock(jdbcTemplate);

    private void stubLockResult(boolean acquired) throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(acquired);
    }

    @Test
    void whenLockNotAcquired_skipsTaskAndDoesNotUnlock() throws Exception {
        stubLockResult(false);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = lock.executeLocked(connection, ScheduleJob.STEEL_QUOTE_SYNC, () -> ran.set(true));

        assertThat(executed).isFalse();
        assertThat(ran).isFalse();
        // 只尝试加锁，没有加锁成功就不该调用 unlock
        verify(connection, times(1)).prepareStatement(anyString());
        verify(connection, never()).prepareStatement(contains("pg_advisory_unlock"));
    }

    @Test
    void whenLockAcquired_runsTaskAndReleasesLock() throws Exception {
        stubLockResult(true);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = lock.executeLocked(connection, ScheduleJob.ATTACHMENT_MANIFEST_EXPORT,
                () -> ran.set(true));

        assertThat(executed).isTrue();
        assertThat(ran).isTrue();
        verify(connection).prepareStatement(contains("pg_advisory_unlock"));
    }

    @Test
    void whenTaskThrows_releasesLockAndPropagates() throws Exception {
        stubLockResult(true);

        assertThatThrownBy(() -> lock.executeLocked(connection, ScheduleJob.OPERATION_LOG_PARTITION_CLEANUP,
                () -> {
                    throw new IllegalStateException("任务失败");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("任务失败");

        // 异常路径同样必须释放锁，否则后续轮次会被永久跳过
        verify(connection).prepareStatement(contains("pg_advisory_unlock"));
    }

    @Test
    void whenLockAcquired_usesDedicatedNamespaceAndJobKey() throws Exception {
        stubLockResult(true);

        lock.executeLocked(connection, ScheduleJob.REDIS_CACHE_HEALTH_CHECK, () -> {
        });

        // 二参形式 (classid, objid)：classid 固定命名空间，与库存单参 advisory lock 的键空间隔离
        verify(statement, times(2)).setInt(1, ClusterScheduleLock.LOCK_NAMESPACE);
        verify(statement, times(2)).setInt(2, ScheduleJob.REDIS_CACHE_HEALTH_CHECK.key());
    }

    @Test
    void runIfLocked_delegatesToJdbcConnection() throws Exception {
        stubLockResult(true);
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
                .thenAnswer(invocation -> {
                    ConnectionCallback<Boolean> callback = invocation.getArgument(0);
                    return callback.doInConnection(connection);
                });
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = lock.runIfLocked(ScheduleJob.STEELX_QUOTE_SYNC, () -> ran.set(true));

        assertThat(executed).isTrue();
        assertThat(ran).isTrue();
    }
}
