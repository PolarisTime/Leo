package com.leo.erp.common.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 定时任务的跨实例互斥：PostgreSQL 会话级咨询锁（{@code pg_try_advisory_lock} + try/finally 释放）。
 *
 * <p><b>为什么不是 JVM 内 AtomicBoolean：</b>{@code AtomicBoolean} 只能防同 JVM 重叠。水平扩容、
 * 蓝绿/滚动发布期间新旧实例并行时，行情抓取（外部抓取 + 写 market 表）、附件恢复清单导出
 * （写本地文件，实例间互相覆盖）、操作日志分区清理会重复执行。</p>
 *
 * <p><b>key 空间：</b>本类使用 PostgreSQL 的<b>二参</b>形式
 * {@code pg_try_advisory_lock(classid, objid)}，与库存记账使用的<b>单参</b>形式
 * {@code pg_advisory_lock(hashtext('inv:...'))} 属于两套互不重叠的锁空间（PostgreSQL 规定
 * 单参/二参 advisory lock 不冲突），因此不会与事务级库存锁互相阻塞。命名空间固定为
 * {@link #LOCK_NAMESPACE}，任务键由 {@link ScheduleJob} 登记。</p>
 *
 * <p><b>会话级锁的代价：</b>{@code pg_try_advisory_lock} 的持有者是数据库连接，加锁与解锁必须在
 * <b>同一条连接</b>上完成。这里用 {@link JdbcTemplate#execute(ConnectionCallback)} 保证整个任务
 * 期间只占用一条连接（任务本身不是长事务，低频执行，代价可接受）。不使用
 * {@code pg_advisory_xact_lock}：那会要求任务在单事务内完成，也失去"跨批次多事务互斥"的能力。</p>
 */
@Slf4j
@Component
public class ClusterScheduleLock {

    /**
     * 二参咨询锁的 classid 命名空间：{@code 0x4C454F53}（"LEOS"），与库存事务锁的单参键空间隔离。
     */
    static final int LOCK_NAMESPACE = 0x4C454F53;

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?, ?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public ClusterScheduleLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 仅当本实例拿到集群锁时执行 {@code task}；拿不到说明其它实例正在执行，直接跳过。
     *
     * <p>任务异常照常向上抛出（由调用方记录），但 {@code finally} 一定释放锁，不会让后续轮次
     * 因上次异常而永久跳过。</p>
     *
     * @return true 表示本次执行了任务；false 表示其它实例持有锁、已跳过
     */
    public boolean runIfLocked(ScheduleJob job, Runnable task) {
        if (job == null || task == null) {
            return false;
        }
        Boolean executed = jdbcTemplate.execute(
                (ConnectionCallback<Boolean>) connection -> executeLocked(connection, job, task));
        return Boolean.TRUE.equals(executed);
    }

    /** 在给定连接上完成「加锁 → 执行 → 释放」；包内可见以便单测直接验证 try/finally 语义。 */
    boolean executeLocked(Connection connection, ScheduleJob job, Runnable task) throws SQLException {
        if (!tryLock(connection, job)) {
            log.info("跳过定时任务 {}：其它实例正持有集群锁", job);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(connection, job);
        }
    }

    private boolean tryLock(Connection connection, ScheduleJob job) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK_SQL)) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, job.key());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection, ScheduleJob job) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UNLOCK_SQL)) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, job.key());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && !resultSet.getBoolean(1)) {
                    log.warn("释放定时任务集群锁失败（连接不是持有者？）: {}", job);
                }
            }
        }
    }
}
